package br.com.weslleycampos.chat.feature.voice

import br.com.weslleycampos.chat.core.ui.components.ChatVoicePhase
import br.com.weslleycampos.chat.data.chat.models.VoiceExchange
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** The prototype's `words` interval (line 748): one more word of the line shows every 230 ms. */
val WordInterval: Duration = 230.milliseconds

private val ListeningDuration = 6500.milliseconds
private val ThinkingDuration = 2400.milliseconds
private val SpeakingDuration = 7000.milliseconds

/**
 * How long the phase lasts unless a tap skips it, the prototype's `schedulePhase` (line 750):
 * listening 6500 ms, thinking 2400 ms, speaking 7000 ms.
 */
val ChatVoicePhase.duration: Duration
    get() = when (this) {
        ChatVoicePhase.Listening -> ListeningDuration
        ChatVoicePhase.Thinking -> ThinkingDuration
        ChatVoicePhase.Speaking -> SpeakingDuration
    }

/** The phase that follows, the prototype's `advancePhase` (line 751): listening, thinking, speaking, listening… */
val ChatVoicePhase.next: ChatVoicePhase
    get() = when (this) {
        ChatVoicePhase.Listening -> ChatVoicePhase.Thinking
        ChatVoicePhase.Thinking -> ChatVoicePhase.Speaking
        ChatVoicePhase.Speaking -> ChatVoicePhase.Listening
    }

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
 * - listening: the [exchange]'s transcript, its first [words] words shown and the rest to come;
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
