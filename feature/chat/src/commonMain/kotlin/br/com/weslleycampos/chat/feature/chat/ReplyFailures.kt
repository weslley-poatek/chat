package br.com.weslleycampos.chat.feature.chat

import br.com.weslleycampos.chat.data.chat.models.ReplyFailure
import br.com.weslleycampos.chat.feature.chat.resources.FeatureChatRes
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_reply_failed
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_reply_key_refused
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_reply_model_unavailable
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_reply_no_key
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_reply_rate_limited
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_reply_unreachable
import org.jetbrains.compose.resources.StringResource

/**
 * The toast that says why Fuelix gave no reply, in the chat and in voice mode alike: "add your
 * Fuelix key in Settings", "Fuelix is busy, try again shortly" and so on. The reply itself reads
 * "interrupted".
 */
val ReplyFailure.toast: StringResource
    get() = when (this) {
        ReplyFailure.NO_KEY -> FeatureChatRes.string.chat_toast_reply_no_key
        ReplyFailure.KEY_REFUSED -> FeatureChatRes.string.chat_toast_reply_key_refused
        ReplyFailure.MODEL_UNAVAILABLE -> FeatureChatRes.string.chat_toast_reply_model_unavailable
        ReplyFailure.RATE_LIMITED -> FeatureChatRes.string.chat_toast_reply_rate_limited
        ReplyFailure.UNREACHABLE -> FeatureChatRes.string.chat_toast_reply_unreachable
        ReplyFailure.FAILED -> FeatureChatRes.string.chat_toast_reply_failed
    }
