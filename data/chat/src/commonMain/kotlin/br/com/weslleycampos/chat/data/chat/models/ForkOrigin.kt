package br.com.weslleycampos.chat.data.chat.models

/**
 * Where a forked conversation came from, for the fork banner under Chat's top bar:
 * "⑂ forked from “[sourceTitle]” at message [messageNumber]".
 *
 * It is kept with the fork when the fork is made, not read from the conversation it came from, so
 * the banner reads the same after that conversation is deleted or changes.
 *
 * @property sourceTitle The title of the conversation it was forked from, as it was at the fork.
 * @property messageNumber The position of the message it was forked at in that conversation's
 *   thread, counting from 1 over the user's messages and the replies alike: the prototype's
 *   `i + 1`. The fork's thread started that many messages long.
 * @property bannerDismissed Whether the banner's "×" hid it. It stays hidden whenever the fork is
 *   opened again, the app restarted included.
 */
data class ForkOrigin(
    val sourceTitle: String,
    val messageNumber: Int,
    val bannerDismissed: Boolean = false,
)
