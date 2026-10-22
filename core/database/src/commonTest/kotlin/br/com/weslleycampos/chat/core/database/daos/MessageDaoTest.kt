package br.com.weslleycampos.chat.core.database.daos

import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.core.database.entities.ReplyProgress
import br.com.weslleycampos.chat.core.database.entities.StoredBlock
import br.com.weslleycampos.chat.core.database.inMemoryChatDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MessageDaoTest {
    private val database = inMemoryChatDatabase()
    private val conversations = database.conversationDao()
    private val dao = database.messageDao()

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun messagesAreObservedInTheOrderTheyWereWrittenWhateverTheirTimestamps() = runTest {
        val conversationId = conversations.insert(conversation())
        val other = conversations.insert(conversation())
        val first = dao.insert(userMessage(conversationId, text = "first", createdAt = 20))
        dao.insert(userMessage(other, text = "elsewhere", createdAt = 15))
        val second = dao.insert(reply(conversationId, createdAt = 10))
        val third = dao.insert(userMessage(conversationId, text = "third", createdAt = 10))

        val messages = dao.observeByConversation(conversationId).first()

        assertEquals(listOf(first, second, third), messages.map { it.id })
        assertEquals(userMessage(conversationId, text = "first", createdAt = 20).copy(id = first), messages.first())
    }

    @Test
    fun aReplyKeepsThePersonaItWasWrittenAs() = runTest {
        val conversationId = conversations.insert(conversation())
        val asPersona = dao.insert(reply(conversationId).copy(personaId = "reviewer"))
        val plain = dao.insert(reply(conversationId))
        val user = dao.insert(userMessage(conversationId))

        val personas = dao.observeByConversation(conversationId).first().associate { it.id to it.personaId }

        assertEquals(mapOf(asPersona to "reviewer", plain to null, user to null), personas)
    }

    @Test
    fun replyBlocksRoundTripThroughTheirJsonColumn() = runTest {
        val conversationId = conversations.insert(conversation())
        val blocks = listOf(
            StoredBlock.Paragraph("Shell equivalent, no dependencies:"),
            StoredBlock.Code(
                language = "bash",
                text = "find . -name \"*.log\" -mtime +7 -print0 \\\n  | xargs -0 rm -v",
            ),
            StoredBlock.NumberedList(listOf("Trailing-only is unchanged.", "With leading, “both edges” — skipped.")),
        )

        dao.insert(reply(conversationId, blocks = blocks))

        assertEquals(blocks, dao.observeByConversation(conversationId).first().single().blocks)
    }

    @Test
    fun theBlocksColumnHoldsTheDesignPrototypesJsonShape() = runTest {
        val conversationId = conversations.insert(conversation())
        dao.insert(
            reply(
                conversationId,
                blocks = listOf(
                    StoredBlock.Paragraph("a"),
                    StoredBlock.Code(language = "ts", text = "b"),
                    StoredBlock.NumberedList(listOf("c", "d")),
                ),
            ),
        )

        val stored = database.useReaderConnection { connection ->
            connection.usePrepared("SELECT blocks, role, status FROM messages") { statement ->
                statement.step()
                listOf(statement.getText(0), statement.getText(1), statement.getText(2))
            }
        }

        assertEquals(
            listOf(
                """[{"type":"p","text":"a"},""" +
                    """{"type":"code","lang":"ts","text":"b"},""" +
                    """{"type":"list","items":["c","d"]}]""",
                "assistant",
                "done",
            ),
            stored,
        )
    }

    @Test
    fun updatingAReplyRewritesOnlyItsBlocksStatusAndLatency() = runTest {
        val conversationId = conversations.insert(conversation())
        val id = dao.insert(reply(conversationId, status = MessageStatus.THINKING, blocks = emptyList(), createdAt = 7))
        val blocks = listOf(StoredBlock.Paragraph("Two likely"))

        dao.updateReply(ReplyProgress(id = id, blocks = blocks, status = MessageStatus.STREAMING, latencyMillis = null))
        val streaming = dao.observeByConversation(conversationId).first().single()
        dao.updateReply(ReplyProgress(id = id, blocks = blocks, status = MessageStatus.DONE, latencyMillis = 1900))
        val done = dao.observeByConversation(conversationId).first().single()

        val expected = reply(
            conversationId,
            status = MessageStatus.THINKING,
            blocks = blocks,
            createdAt = 7,
        ).copy(id = id)
        assertEquals(expected.copy(status = MessageStatus.STREAMING), streaming)
        assertEquals(expected.copy(status = MessageStatus.DONE, latencyMillis = 1900), done)
    }

    @Test
    fun updatingAReplySaysWhetherTheReplyIsStillThere() = runTest {
        val conversationId = conversations.insert(conversation())
        val id = dao.insert(reply(conversationId, status = MessageStatus.STREAMING, blocks = emptyList()))
        val progress = ReplyProgress(
            id = id,
            blocks = listOf(StoredBlock.Paragraph("Two")),
            status = MessageStatus.STREAMING,
            latencyMillis = null,
        )

        assertEquals(1, dao.updateReply(progress))
        conversations.delete(conversationId)

        assertEquals(0, dao.updateReply(progress))
        assertTrue(dao.observeByConversation(conversationId).first().isEmpty())
    }

    @Test
    fun repliesAreCountedPerConversation() = runTest {
        val conversationId = conversations.insert(conversation())
        val other = conversations.insert(conversation())
        dao.insert(userMessage(conversationId))
        dao.insert(reply(conversationId))
        dao.insert(userMessage(conversationId))
        dao.insert(reply(conversationId, status = MessageStatus.INTERRUPTED))
        dao.insert(reply(other))

        assertEquals(2, dao.countReplies(conversationId))
        assertEquals(1, dao.countReplies(other))
    }

    @Test
    fun aReplyIsUnfinishedWhileItThinksOrStreams() = runTest {
        val unfinished = MessageStatus.entries.associateWith { status ->
            val conversationId = conversations.insert(conversation())
            dao.insert(userMessage(conversationId))
            dao.insert(reply(conversationId, status = status))
            dao.hasUnfinishedReply(conversationId)
        }

        assertEquals(
            mapOf(
                MessageStatus.THINKING to true,
                MessageStatus.STREAMING to true,
                MessageStatus.DONE to false,
                MessageStatus.INTERRUPTED to false,
            ),
            unfinished,
        )
    }

    @Test
    fun interruptingUnfinishedRepliesKeepsWhatTheyRevealed() = runTest {
        val conversationId = conversations.insert(conversation())
        val partial = listOf(StoredBlock.Paragraph("Two li"))
        val thinking = dao.insert(reply(conversationId, status = MessageStatus.THINKING, blocks = emptyList()))
        val streaming = dao.insert(reply(conversationId, status = MessageStatus.STREAMING, blocks = partial))
        val done = dao.insert(reply(conversationId))

        val interrupted = dao.interruptUnfinishedReplies()

        val byId = dao.observeByConversation(conversationId).first().associateBy { it.id }
        assertEquals(2, interrupted)
        assertEquals(MessageStatus.INTERRUPTED, byId.getValue(thinking).status)
        assertEquals(MessageStatus.INTERRUPTED, byId.getValue(streaming).status)
        assertEquals(partial, byId.getValue(streaming).blocks)
        assertEquals(MessageStatus.DONE, byId.getValue(done).status)
        assertFalse(dao.hasUnfinishedReply(conversationId))
    }

    @Test
    fun interruptingOneReplyLeavesTheOthersAndAFinishedOneAlone() = runTest {
        val conversationId = conversations.insert(conversation())
        val first = dao.insert(reply(conversationId, status = MessageStatus.STREAMING))
        val second = dao.insert(reply(conversationId, status = MessageStatus.STREAMING))
        val done = dao.insert(reply(conversationId))

        dao.interruptReply(first)
        dao.interruptReply(done)

        val statuses = dao.observeByConversation(conversationId).first().associate { it.id to it.status }
        assertEquals(
            mapOf(first to MessageStatus.INTERRUPTED, second to MessageStatus.STREAMING, done to MessageStatus.DONE),
            statuses,
        )
    }

    @Test
    fun aMessageNeedsAnExistingConversation() = runTest {
        val failure = runCatching { dao.insert(userMessage(conversationId = 42)) }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(dao.observeByConversation(42).first().isEmpty())
    }

    @Test
    fun deletingAConversationDeletesItsMessages() = runTest {
        val conversationId = conversations.insert(conversation())
        val kept = conversations.insert(conversation())
        dao.insert(userMessage(conversationId))
        dao.insert(reply(conversationId))
        dao.insert(userMessage(kept))

        database.useWriterConnection { connection ->
            connection.usePrepared("DELETE FROM conversations WHERE id = ?") { statement ->
                statement.bindLong(1, conversationId)
                statement.step()
            }
        }

        assertTrue(dao.observeByConversation(conversationId).first().isEmpty())
        assertEquals(1, dao.observeByConversation(kept).first().size)
    }
}
