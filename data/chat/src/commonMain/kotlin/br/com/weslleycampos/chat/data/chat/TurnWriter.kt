package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.core.database.daos.ConversationDao
import br.com.weslleycampos.chat.core.database.daos.MessageDao
import br.com.weslleycampos.chat.core.database.entities.ConversationEntity
import br.com.weslleycampos.chat.core.database.entities.MessageEntity
import br.com.weslleycampos.chat.core.database.entities.MessageRole
import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.core.database.entities.PAIR_FIRST_SIDE
import br.com.weslleycampos.chat.core.database.entities.PAIR_SECOND_SIDE
import br.com.weslleycampos.chat.data.chat.models.ConversationTitles
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.replies.CannedReplies
import br.com.weslleycampos.chat.data.chat.replies.StreamedReply
import kotlin.time.Clock

/**
 * The writes that start a turn, run inside the caller's transaction: the conversation (created,
 * or moved to the top and back from the archive) and the user's message. A send ([startNew],
 * [continueIn]) adds a thinking reply, or the two thinking sides of a compare pair; voice mode
 * ([RoomVoiceRepository]) saves its transcript with [create] or [continued] and [insertUserMessage],
 * and its reply later.
 */
internal class TurnWriter(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val clock: Clock,
) {
    /** A new conversation titled after [text], answered by [choice], and its first turn. */
    suspend fun startNew(text: String, choice: StoredChoice): Turn = startTurn(create(text, choice), text)

    /** The conversation's next turn, or `null` when it is gone or its reply is on its way. */
    suspend fun continueIn(conversationId: Long, text: String): Turn? =
        continued(conversationId)?.let { startTurn(it, text) }

    /** A new conversation titled after [text], answered by [choice]. */
    suspend fun create(text: String, choice: StoredChoice): ConversationEntity {
        val now = clock.now().toEpochMilliseconds()
        val created = ConversationEntity(
            title = ConversationTitles.from(text),
            modelId = choice.modelId,
            personaId = choice.personaId,
            createdAt = now,
            updatedAt = now,
            compareOn = choice.compareOn,
            compareModelId = choice.compareModelId,
        )
        return created.copy(id = conversationDao.insert(created))
    }

    /**
     * The conversation, moved to the top of "recent" and History and back from the archive, or
     * `null` when it is gone or its reply is on its way.
     */
    suspend fun continued(conversationId: Long): ConversationEntity? {
        val existing = conversationDao.get(conversationId)
        if (existing == null || messageDao.hasUnfinishedReply(conversationId)) return null
        conversationDao.setUpdatedAt(conversationId, clock.now().toEpochMilliseconds())
        if (existing.archived) conversationDao.setArchived(conversationId, archived = false)
        return existing
    }

    /** Saves [text] as the user's message in the conversation, sent at [createdAt], and returns its id. */
    suspend fun insertUserMessage(
        conversationId: Long,
        text: String,
        createdAt: Long = clock.now().toEpochMilliseconds(),
    ): Long = messageDao.insert(
        MessageEntity(
            conversationId = conversationId,
            role = MessageRole.USER,
            text = text,
            blocks = emptyList(),
            modelId = null,
            status = MessageStatus.DONE,
            latencyMillis = null,
            createdAt = createdAt,
        ),
    )

    /**
     * Saves the user's message and its thinking reply: from the conversation's model and persona, or,
     * while it compares its model with a second one, a pair of sides with no persona, the
     * conversation's model first.
     */
    private suspend fun startTurn(conversation: ConversationEntity, text: String): Turn {
        val now = clock.now().toEpochMilliseconds()
        val repliesBefore = messageDao.countReplies(conversation.id)
        insertUserMessage(conversation.id, text, createdAt = now)
        val reply = thinking(conversation.id, createdAt = now)
        val second = conversation.comparedModelId()
        val replyIds = if (second == null) {
            listOf(messageDao.insert(reply.copy(modelId = conversation.modelId, personaId = conversation.personaId)))
        } else {
            listOf(
                messageDao.insert(reply.copy(modelId = conversation.modelId, pairSide = PAIR_FIRST_SIDE)),
                messageDao.insert(reply.copy(modelId = second, pairSide = PAIR_SECOND_SIDE)),
            )
        }
        return Turn(conversationId = conversation.id, replyIds = replyIds, repliesBefore = repliesBefore)
    }

    /** A thinking reply in the conversation, before its model, persona and pair side are set. */
    private fun thinking(conversationId: Long, createdAt: Long): MessageEntity = MessageEntity(
        conversationId = conversationId,
        role = MessageRole.ASSISTANT,
        text = "",
        blocks = emptyList(),
        modelId = null,
        status = MessageStatus.THINKING,
        latencyMillis = null,
        createdAt = createdAt,
    )
}

/**
 * The model the conversation's next reply is compared with: its second model while compare is on,
 * no persona answers, and the catalogue still has that model, which is not the conversation's own.
 * `null` for a single reply.
 */
private fun ConversationEntity.comparedModelId(): String? = compareModelId?.takeIf {
    compareOn && personaId == null && it != modelId && ModelCatalogue.find(it) != null
}

/**
 * A send's rows: [replyIds] holds its reply, or a compare pair's two sides in their order, which
 * play the canned reply that comes after [repliesBefore] replies and its alternative.
 */
internal class Turn(val conversationId: Long, val replyIds: List<Long>, val repliesBefore: Int) {
    fun replies(): List<StreamedReply> = replyIds.mapIndexed { side, id ->
        val blocks = if (side == PAIR_FIRST_SIDE) {
            CannedReplies.next(repliesSoFar = repliesBefore)
        } else {
            CannedReplies.alternative(repliesSoFar = repliesBefore)
        }
        StreamedReply(replyId = id, blocks = blocks)
    }
}
