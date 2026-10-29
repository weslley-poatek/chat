package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.core.database.TransactionRunner
import br.com.weslleycampos.chat.core.database.daos.ConversationDao
import br.com.weslleycampos.chat.core.database.daos.MessageDao
import br.com.weslleycampos.chat.core.database.entities.ConversationEntity
import br.com.weslleycampos.chat.core.database.entities.MessageEntity
import br.com.weslleycampos.chat.core.database.entities.MessageRole
import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.data.chat.models.Block
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.ReplyFailure
import br.com.weslleycampos.chat.data.chat.models.VoiceAnswer
import br.com.weslleycampos.chat.data.chat.models.VoiceExchange
import br.com.weslleycampos.chat.data.chat.models.VoiceSession
import br.com.weslleycampos.chat.data.chat.models.VoiceTurn
import br.com.weslleycampos.chat.data.chat.replies.MarkdownBlocks
import br.com.weslleycampos.chat.data.chat.replies.ReplyPrompts
import br.com.weslleycampos.chat.data.chat.replies.toReplyFailure
import br.com.weslleycampos.chat.data.fuelix.FuelixClient
import br.com.weslleycampos.chat.data.fuelix.FuelixException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/**
 * [VoiceRepository] on Room, next to [chats] and [models]: a session reads its conversation and
 * thread through [chats], a pick goes through [ChatRepository.choose] and
 * [ModelRepository.setDefaultModel], and a transcript is written by the [TurnWriter] a send uses,
 * so a voice conversation is created, titled, moved up and brought back from the archive as a
 * typed one is. Replies come from [fuelix].
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
    private val fuelix: FuelixClient,
    private val clock: Clock,
) : VoiceRepository {
    private val turns = TurnWriter(conversationDao = conversationDao, messageDao = messageDao, clock = clock)

    override fun observeSession(conversationId: Long?): Flow<VoiceSession?> {
        if (conversationId == null) {
            return models.observeDefaultModel().map { model -> VoiceSession(conversationId = null, model = model) }
        }
        return chats.observeConversation(conversationId)
            .map { found -> found?.let { VoiceSession(it.id, it.model, it.persona) } }
            .distinctUntilChanged()
    }

    override suspend fun saveTranscript(
        conversationId: Long?,
        transcript: String,
        newSession: ModelChoice?,
    ): VoiceTurn? {
        val heard = transcript.trim()
        if (heard.isEmpty()) return null
        val exchange = VoiceExchange(transcript = heard)
        if (conversationId == null) {
            val choice = models.stored(newSession?.withoutCompare()) ?: return null
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
                VoiceTurn(
                    conversationId = conversationId,
                    transcriptId = turns.insertUserMessage(conversationId, exchange.transcript),
                    exchange = exchange,
                )
            }
        }
    }

    override suspend fun answer(turn: VoiceTurn): VoiceAnswer {
        val conversation = conversationDao.get(turn.conversationId) ?: return VoiceAnswer.Dropped
        val thread = messageDao.observeByConversation(turn.conversationId).first()
        if (thread.lastOrNull()?.isTranscriptOf(turn) != true) return VoiceAnswer.Dropped
        val persona = conversation.personaId?.let { id -> models.observePersonas().first().firstOrNull { it.id == id } }
        val prompt = listOfNotNull(ReplyPrompts.VOICE_PROMPT, persona?.prompt).joinToString("\n\n")
        val model = ModelCatalogue.resolve(conversation.modelId)
        val reply = try {
            fuelix.streamChat(model, ReplyPrompts.messages(thread, model, prompt))
                .fold(StringBuilder()) { text, piece -> text.append(piece) }
                .toString()
                .trim()
        } catch (e: FuelixException) {
            return VoiceAnswer.Failed(e.toReplyFailure())
        }
        return when {
            reply.isEmpty() -> VoiceAnswer.Failed(ReplyFailure.FAILED)
            save(turn, reply, writer = conversation) -> VoiceAnswer.Spoken(reply.split(Whitespace).joinToString(" "))
            else -> VoiceAnswer.Dropped
        }
    }

    /**
     * Saves [reply] to [turn], credited to the model and persona of [writer], the conversation as it
     * was asked, while its transcript is still the conversation's last message.
     */
    private suspend fun save(
        turn: VoiceTurn,
        reply: String,
        writer: ConversationEntity,
    ): Boolean = transactions.inWriteTransaction {
        val conversation = conversationDao.get(turn.conversationId)
        val transcript = messageDao.last(turn.conversationId)
        if (conversation == null || transcript?.isTranscriptOf(turn) != true) return@inWriteTransaction false
        val thinking = clock.now().toEpochMilliseconds() - transcript.createdAt
        messageDao.insert(
            MessageEntity(
                conversationId = turn.conversationId,
                role = MessageRole.ASSISTANT,
                text = "",
                blocks = MarkdownBlocks.parse(reply).map(Block::toStoredBlock),
                modelId = writer.modelId,
                status = MessageStatus.DONE,
                latencyMillis = thinking.coerceAtLeast(0),
                createdAt = transcript.createdAt,
                personaId = writer.personaId,
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

/** Whether this row is [turn]'s transcript: a user message with its id. */
private fun MessageEntity.isTranscriptOf(turn: VoiceTurn): Boolean = id == turn.transcriptId && role == MessageRole.USER

/** What separates a spoken reply's words. */
private val Whitespace = Regex("\\s+")

/** The choice with compare off and no second model: voice mode answers with one model. */
private fun ModelChoice.withoutCompare(): ModelChoice = ModelChoice(model = model, persona = persona)
