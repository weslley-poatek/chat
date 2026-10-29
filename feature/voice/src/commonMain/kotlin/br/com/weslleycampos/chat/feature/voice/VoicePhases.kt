package br.com.weslleycampos.chat.feature.voice

import br.com.weslleycampos.chat.core.ui.components.ChatVoicePhase
import br.com.weslleycampos.chat.data.chat.models.VoiceExchange
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** The prototype's `words` interval (line 748): one more word of the line shows every 230 ms. */
val WordInterval: Duration = 230.milliseconds

/** How long listening waits with no new word, once some were heard, before it takes them as said. */
val SilenceAfterSpeech: Duration = 2000.milliseconds

/** The breath between a listen that heard nothing and the next one. */
val ListenAgainPause: Duration = 500.milliseconds

/** How long speaking stays on the whole reply once its last word shows. */
private val SpeakingHold = 1000.milliseconds

/**
 * How long speaking [reply] lasts unless a tap skips it, on a device that cannot say it: a word
 * every [WordInterval], then [SpeakingHold] on the whole reply.
 */
fun speakingDuration(reply: String): Duration = WordInterval * reply.split(' ').size + SpeakingHold

/**
 * The caption under the sphere, the prototype's `voiceTranscript` and `voiceTranscriptRest`
 * (lines 856–858): [shown] in `--fg2`, then [rest], the words still to come, in `--fg3`.
 */
data class VoiceCaption(val shown: String, val rest: String) {
    companion object {
        /** Nothing yet: before the session's first exchange is read. */
        val Empty = VoiceCaption(shown = "", rest = "")
    }
}

/**
 * The caption for [phase], [words] words in:
 * - listening: the words heard so far, its first [words] shown;
 * - thinking: the whole transcript shown;
 * - speaking: the reply, its first [words] words shown and the rest to come.
 *
 * Words are split on spaces, as the prototype splits them. The rest starts with the space between
 * the last shown word and the next one, so the line reads as one sentence; with no word shown yet,
 * it is the whole line.
 */
fun voiceCaption(phase: ChatVoicePhase, exchange: VoiceExchange?, words: Int): VoiceCaption {
    if (exchange == null) return VoiceCaption.Empty
    val line = when (phase) {
        ChatVoicePhase.Listening -> exchange.transcript
        ChatVoicePhase.Thinking -> return VoiceCaption(shown = exchange.transcript, rest = "")
        ChatVoicePhase.Speaking -> exchange.reply
    }
    val all = line.split(' ')
    val count = words.coerceIn(0, all.size)
    val shown = all.take(count).joinToString(" ")
    val toCome = all.drop(count).joinToString(" ")
    val rest = when {
        toCome.isEmpty() -> ""
        shown.isEmpty() -> toCome
        else -> " $toCome"
    }
    return VoiceCaption(shown = shown, rest = rest)
}
