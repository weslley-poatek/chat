package br.com.weslleycampos.chat.data.chat.models

import kotlin.time.Instant

/**
 * One chat thread on this device.
 *
 * @property title The first message, cut to [ConversationTitles.MAX_LENGTH] characters on one line.
 * @property model The model that answers in it.
 * @property createdAt When its first message was sent.
 * @property updatedAt When its latest message was sent; the "recent" list sorts on it.
 */
data class Conversation(
    val id: Long,
    val title: String,
    val model: ChatModel,
    val createdAt: Instant,
    val updatedAt: Instant,
)
