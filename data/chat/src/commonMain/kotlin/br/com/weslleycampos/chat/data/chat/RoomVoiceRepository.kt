package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.core.database.TransactionRunner
import br.com.weslleycampos.chat.core.database.daos.ConversationDao
import br.com.weslleycampos.chat.core.database.daos.MessageDao
import br.com.weslleycampos.chat.core.database.entities.MessageEntity
import br.com.weslleycampos.chat.core.database.entities.MessageRole
import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.core.database.entities.StoredBlock
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.VoiceSession
import br.com.weslleycampos.chat.data.chat.models.VoiceTurn
import br.com.weslleycampos.chat.data.chat.replies.VoiceLines
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/**
 * [VoiceRepository] on Room, next to [chats] and [models]: a session reads its conversation and
 * thread through [chats], a pick goes through [ChatRepository.choose] and
 * [ModelRepository.setDefaultModel], and a transcript is written by the [TurnWriter] a send uses,
 * so a voice conversation is created, titled, moved up and brought back from the archive as a
 * typed one is.
 *
 * Each save is one write transaction, which checks and writes together, so it needs no lock with
 * [RoomChatRepository]'s sends: a send's transaction runs wholly before it or wholly after it.
 */
internal class RoomVoiceRepository(
    private val chats: ChatRepository,
    private val models: ModelRepository,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) : VoiceRepository {
    private val turns = TurnWriter(conversationDao = conversationDao, messageDao = messageDao, clock = clock)

    override fun observeSession(conversationId: Long?): Flow<VoiceSession?> {
        if (conversationId == null) {
            return models.observeDefaultModel().map { model ->
                VoiceSession(conversationId = null, model = model, next = VoiceLines.next(exchangesSoFar = 0))
            }
        }
        val conversation = chats.observeConversation(conversationId)
        val thread = chats.observeMessages(conversationId)
        return combine(conversation, thread) { found, messages ->
            found?.let {
                VoiceSession(
                    conversationId = it.id,
                    model = it.model,
                    persona = it.persona,
                    next = VoiceLines.next(exchangesSoFar = messages.count { message -> message is Message.User }),
                )
            }
        }.distinctUntilChanged()
    }

    override suspend fun saveTranscript(conversationId: Long?, newSession: ModelChoice?): VoiceTurn? {
        if (conversationId == null) {
            val choice = models.stored(newSession?.withoutCompare()) ?: return null
            val exchange = VoiceLines.next(exchangesSoFar = 0)
            return transactions.inWriteTransaction {
                val created = turns.create(exchange.transcript, choice)
                VoiceTurn(
                    conversationId = created.id,
                    transcriptId = turns.insertUserMessage(created.id, exchange.transcript),
                    exchange = exchange,
                )
            }
        }
        // Reading through chats first waits for its start-up recovery, so a reply that an earlier run
        // left thinking reads interrupted rather than on its way.
        chats.observeConversation(conversationId).first() ?: return null
        return transactions.inWriteTransaction {
            turns.continued(conversationId)?.let {
                val exchange = VoiceLines.next(exchangesSoFar = messageDao.countUserMessages(conversationId))
                VoiceTurn(
                    conversationId = conversationId,
                    transcriptId = turns.insertUserMessage(conversationId, exchange.transcript),
                    exchange = exchange,
                )
            }
        }
    }

    override suspend fun saveReply(turn: VoiceTurn): Boolean = transactions.inWriteTransaction {
        val conversation = conversationDao.get(turn.conversationId)
        val transcript = messageDao.last(turn.conversationId)
        if (conversation == null || transcript == null) return@inWriteTransaction false
        if (transcript.id != turn.transcriptId || transcript.role != MessageRole.USER) return@inWriteTransaction false
        // The transcript is the last message: the user messages before it are the exchanges before its own.
        val exchange = VoiceLines.next(exchangesSoFar = messageDao.countUserMessages(turn.conversationId) - 1)
        val thinking = clock.now().toEpochMilliseconds() - transcript.createdAt
        messageDao.insert(
            MessageEntity(
                conversationId = turn.conversationId,
                role = MessageRole.ASSISTANT,
                text = "",
                blocks = listOf(StoredBlock.Paragraph(exchange.reply)),
                modelId = conversation.modelId,
                status = MessageStatus.DONE,
                latencyMillis = thinking.coerceAtLeast(0),
                createdAt = transcript.createdAt,
                personaId = conversation.personaId,
            ),
        )
        true
    }

    override suspend fun choose(conversationId: Long?, choice: ModelChoice): Boolean {
        val spoken = choice.withoutCompare()
        val stored = models.stored(spoken) ?: return false
        if (conversationId != null && !chats.choose(conversationId, spoken)) return false
        models.setDefaultModel(ModelCatalogue[stored.modelId])
        return true
    }
}

/** The choice with compare off and no second model: voice mode answers with one model. */
private fun ModelChoice.withoutCompare(): ModelChoice = ModelChoice(model = model, persona = persona)
