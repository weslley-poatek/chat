package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.data.chat.models.Conversation
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.ReplyStatus

/**
 * A compare pair's "continue with this →": the conversation goes on with the model of one side,
 * on its own. Turning compare on and off and picking the second model are [ChatRepository.choose]
 * with [ModelChoice.toggledCompare] and [ModelChoice.picked]; a send while the conversation
 * compares is answered by a [Message.ComparePair].
 *
 * Kept apart from [ChatRepository], which it does not need: it only reads a pair that is already
 * written and changes the conversation's choice.
 */
interface CompareRepository {
    /**
     * Makes the model of the pair's side [side] answer the conversation's next message on its own,
     * as that side's "continue with this →" does: the conversation's model becomes that side's
     * model, with no persona and compare off, so its second model is dropped. Its place in "recent"
     * and History and its `updatedAt` stay, and the pair stays as it is in the thread.
     *
     * [pairMessageId] is the pair's [Message.ComparePair.id] and [side] the index of the side in its
     * [Message.ComparePair.sides]: `0` for the conversation's model, `1` for the one it was compared
     * with. Only a [ReplyStatus.DONE] side can be continued with, even while the other side still
     * streams; the next send then waits for that side, as for any reply in progress.
     *
     * Changes nothing and returns `false` when [conversationId] names no conversation,
     * [pairMessageId] is not one of its pairs, [side] is not `0` or `1`, that side is not
     * [ReplyStatus.DONE], or its model is no longer in the catalogue. [Conversation.compareOn] and
     * [Conversation.model] say what it changed.
     */
    suspend fun continueWith(conversationId: Long, pairMessageId: Long, side: Int): Boolean
}
