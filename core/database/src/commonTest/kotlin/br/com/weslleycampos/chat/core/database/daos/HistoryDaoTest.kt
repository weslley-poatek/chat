package br.com.weslleycampos.chat.core.database.daos

import br.com.weslleycampos.chat.core.database.entities.StoredBlock
import br.com.weslleycampos.chat.core.database.inMemoryChatDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryDaoTest {
    private val database = inMemoryChatDatabase()
    private val conversations = database.conversationDao()
    private val messages = database.messageDao()
    private val dao = database.historyDao()

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun historyIsEveryConversationThatIsNotArchivedNewestFirstTheLaterIdWinningATieAndCountsThem() = runTest {
        val oldest = conversations.insert(conversation(title = "oldest", updatedAt = 1))
        val tiedFirst = conversations.insert(conversation(title = "tied first", updatedAt = 3))
        val pinned = conversations.insert(conversation(title = "pinned", updatedAt = 2, pinned = true))
        conversations.insert(conversation(title = "archived", updatedAt = 5, archived = true))
        val tiedSecond = conversations.insert(conversation(title = "tied second", updatedAt = 3))
        val newest = conversations.insert(conversation(title = "newest", updatedAt = 4))

        assertEquals(
            listOf(newest, tiedSecond, tiedFirst, pinned, oldest),
            dao.observeAll().first().map { it.id },
        )
        assertEquals(5, dao.observeCount().first())
    }

    @Test
    fun searchMatchesTitlesAndTheUsersMessagesIgnoringTheCaseOfAsciiLetters() = runTest {
        val byTitle = conversations.insert(conversation(title = "useDebounce hook with leading edge", updatedAt = 1))
        val byMessage = conversations.insert(conversation(title = "Hello", updatedAt = 2))
        messages.insert(userMessage(byMessage, text = "Why does my DEBOUNCE read a stale value?"))
        val neither = conversations.insert(conversation(title = "Postgres index", updatedAt = 3))
        messages.insert(userMessage(neither, text = "partial JSONB column"))

        val matches = dao.search(containsPattern("debounce"), modelIds = emptyList()).first()

        assertEquals(listOf(byMessage, byTitle), matches.map { it.id })
        assertEquals(
            listOf(neither),
            dao.search(containsPattern("jsonb COL"), modelIds = emptyList()).first().map { it.id },
        )
    }

    @Test
    fun searchDoesNotLookInReplies() = runTest {
        val id = conversations.insert(conversation(title = "Hello"))
        messages.insert(userMessage(id, text = "hi"))
        messages.insert(reply(id, blocks = listOf(StoredBlock.Paragraph("Two likely causes"))))

        assertTrue(dao.search(containsPattern("likely"), modelIds = emptyList()).first().isEmpty())
        assertTrue(dao.search(containsPattern("type"), modelIds = emptyList()).first().isEmpty())
    }

    @Test
    fun searchMatchesTheConversationsOnTheModelsItIsGiven() = runTest {
        val gpt = conversations.insert(conversation(title = "Postgres index", updatedAt = 1, modelId = "gpt5"))
        val o3 = conversations.insert(conversation(title = "Rust ownership", updatedAt = 2, modelId = "o3"))
        conversations.insert(conversation(title = "Hello", updatedAt = 3, modelId = "sonnet"))

        val matches = dao.search(containsPattern("nothing like it"), modelIds = listOf("gpt5", "o3")).first()

        assertEquals(listOf(o3, gpt), matches.map { it.id })
    }

    @Test
    fun searchTreatsPercentUnderscoreAndBackslashAsThemselves() = runTest {
        val percent = conversations.insert(conversation(title = "Cut costs by 50% now"))
        val underscore = conversations.insert(conversation(title = "snake_case keys"))
        val backslash = conversations.insert(conversation(title = """C:\Users path"""))
        conversations.insert(conversation(title = "50 percent, snakeXcase, C:/Users"))

        fun search(text: String) = dao.search(containsPattern(text), modelIds = emptyList())

        assertEquals(listOf(percent), search("50%").first().map { it.id })
        assertEquals(listOf(underscore), search("e_c").first().map { it.id })
        assertEquals(listOf(backslash), search("""C:\""").first().map { it.id })
    }
}
