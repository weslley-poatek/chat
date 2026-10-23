package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.data.chat.models.Conversation
import br.com.weslleycampos.chat.data.chat.models.ForkOrigin
import br.com.weslleycampos.chat.data.chat.models.ReplyStatus

/**
 * The message action row's "⑂ fork here": a new conversation that starts as a copy of another one
 * up to one of its messages. From then on a fork is an ordinary conversation that [ChatRepository]
 * observes, sends in and lists in History and "recent"; its [Conversation.fork] says where it came
 * from, for the fork banner, until [dismissForkBanner] hides the banner.
 *
 * Kept apart from [ChatRepository], which it does not need: a fork streams nothing, and copies only
 * finished messages.
 */
interface ForkRepository {
    /**
     * Forks the conversation at [messageId], and returns the new conversation's id.
     *
     * In one transaction it creates a conversation with copies of the source's messages up to and
     * including [messageId], in their order, under new ids: the user's messages and the replies,
     * each with its text, blocks, model, persona, status, latency and time as stored. The fork has
     * the source's title, model and persona, is created and updated now (so it tops History and
     * "recent"), is neither pinned nor archived, and records its [ForkOrigin]: the source's title and
     * the message's position in its thread, counting from 1. The source is not changed.
     *
     * A fork at a user message ends on that message: no reply is started, and the next send
     * continues the fork as in any conversation, its canned replies going on from the ones copied.
     *
     * Forks nothing and returns `null` when [conversationId] names no conversation, when
     * [messageId] is not one of its messages, or when a reply up to [messageId]
     * [ReplyStatus.isInProgress]: a reply still on its way is never copied. Only the last message
     * of a conversation can be on its way, so a fork at the message before it still works, without
     * that reply.
     */
    suspend fun fork(conversationId: Long, messageId: Long): Long?

    /**
     * Hides the fork banner of the conversation for good, as its "×" does: [Conversation.fork]
     * keeps its origin with [ForkOrigin.bannerDismissed] set, every time the fork is opened again.
     * It does not move the conversation in "recent" or History. Returns whether it changed anything:
     * `false` for an unknown conversation, one that is not a fork, or one whose banner is already
     * hidden.
     */
    suspend fun dismissForkBanner(conversationId: Long): Boolean
}
