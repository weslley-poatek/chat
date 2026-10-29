package br.com.weslleycampos.chat.feature.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import br.com.weslleycampos.chat.core.ui.ChatTheme
import br.com.weslleycampos.chat.core.ui.components.ChatToast
import br.com.weslleycampos.chat.core.ui.components.ChatVoicePhase
import br.com.weslleycampos.chat.core.ui.components.ChatVoiceSphere
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.PersonaPresets
import br.com.weslleycampos.chat.data.chat.models.ReplyFailure
import br.com.weslleycampos.chat.data.chat.models.VoiceExchange
import br.com.weslleycampos.chat.feature.chat.palette
import br.com.weslleycampos.chat.feature.chat.toast
import br.com.weslleycampos.chat.feature.voice.resources.FeatureVoiceRes
import br.com.weslleycampos.chat.feature.voice.resources.voice_allow_microphone
import br.com.weslleycampos.chat.feature.voice.resources.voice_skip
import org.jetbrains.compose.resources.stringResource

/** The caption's `padding:0 28px 18px` and `min-height:118px`. */
private val CaptionSidePadding = 28.dp
private val CaptionBottomPadding = 18.dp
private val CaptionMinHeight = 118.dp

/** The transcript's `min-height:46px`, two lines. */
private val TranscriptMinHeight = 46.dp

/**
 * The Voice mode frame (frames.md §2.6), the home screen: the bar with History, the model chip and
 * Settings; the particle sphere and its glow, which a tap moves to the next phase; the phase label
 * and the line being heard or said; and mute, end and "Aa". A toast says why Fuelix gave no reply,
 * over the sphere's lower edge. It fills the screen under the status bar, the controls clear of the
 * navigation bar or home indicator.
 *
 * The accent is what answers the session: wrap the screen in
 * `ChatTheme(palette = uiState.model.palette)`.
 */
@Composable
fun VoiceScreen(
    uiState: VoiceUiState,
    onHistoryClick: () -> Unit,
    onModelClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSphereTap: () -> Unit,
    onToggleMute: () -> Unit,
    onEnd: () -> Unit,
    onTextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(color = ChatTheme.colors.surface.primary)
            .clipToBounds()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        VoiceTopBar(
            chipLabel = uiState.chipLabel,
            onHistoryClick = onHistoryClick,
            onModelClick = onModelClick,
            onSettingsClick = onSettingsClick,
        )
        val skip = stringResource(FeatureVoiceRes.string.voice_skip)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onSphereTap)
                .semantics { contentDescription = skip },
        ) {
            // The glow is the sphere's own, centred on it: 0.12 of the accent, 0.22 while speaking.
            ChatVoiceSphere(modifier = Modifier.fillMaxSize(), phase = uiState.phase, muted = uiState.muted)
            ChatToast(
                message = uiState.toast?.let { stringResource(it) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = ChatTheme.spacing.large),
            )
        }
        val allow = stringResource(FeatureVoiceRes.string.voice_allow_microphone)
        VoiceCaptionText(
            phaseLabel = stringResource(uiState.phaseLabel),
            caption = if (uiState.microphone) uiState.caption else VoiceCaption(shown = "", rest = allow),
        )
        VoiceControls(
            muted = uiState.muted,
            muteLabel = stringResource(uiState.muteLabel),
            onToggleMute = onToggleMute,
            onEnd = onEnd,
            onTextClick = onTextClick,
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        )
    }
}

/**
 * The phase label, mono 11 in capitals and the accent, and under it the line: the words heard or
 * said so far in `--fg2`, the rest of the sentence in `--fg3` (frame lines 455–458).
 */
@Composable
private fun VoiceCaptionText(phaseLabel: String, caption: VoiceCaption) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CaptionMinHeight)
            .padding(start = CaptionSidePadding, end = CaptionSidePadding, bottom = CaptionBottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = phaseLabel.uppercase(),
            // A screen reader hears the phase change, read as the word rather than spelled out.
            modifier = Modifier.semantics {
                contentDescription = phaseLabel
                liveRegion = LiveRegionMode.Polite
            },
            style = ChatTheme.typography.eyebrow,
            color = ChatTheme.colors.accent.primary,
        )
        Spacer(modifier = Modifier.height(ChatTheme.spacing.medium))
        val rest = ChatTheme.colors.text.tertiary
        Text(
            text = buildAnnotatedString {
                append(caption.shown)
                withStyle(SpanStyle(color = rest)) { append(caption.rest) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TranscriptMinHeight),
            style = ChatTheme.typography.headlineSmall.copy(lineBreak = LineBreak.Paragraph),
            color = ChatTheme.colors.text.secondary,
            textAlign = TextAlign.Center,
        )
    }
}

/** The prototype's `USER_T` and `MODEL_T` (lines 608–609), the first exchange. */
private val PreviewExchange = VoiceExchange(
    transcript = "So the debounce hook works, but I also want it to fire on the leading edge of a burst, " +
        "not just the trailing one.",
    reply = "Add a leading flag. On the first change in a burst set the value immediately, then suppress " +
        "until the window closes. I will put the timer in a ref so it survives renders.",
)

/** Words in at 230 ms a word: 3 s into listening, and every word of `MODEL_T` as speaking ends. */
private const val PreviewListeningWords = 13
private const val PreviewSpeakingWords = 33

data class VoicePreviewCase(val name: String, val uiState: VoiceUiState, val darkTheme: Boolean) {
    val displayName: String get() = "$name · ${if (darkTheme) "Dark" else "Light"}"
}

/** Every state of the Voice mode frame, in light and dark. */
class VoiceScreenPreviewParameterProvider : PreviewParameterProvider<VoicePreviewCase> {
    private val listening = VoiceUiState(exchange = PreviewExchange, words = PreviewListeningWords)
    private val states = listOf(
        "Listening · starting" to listening.copy(words = 0),
        "Listening" to listening,
        "Thinking" to listening.copy(phase = ChatVoicePhase.Thinking, words = 0),
        "Speaking" to listening.copy(phase = ChatVoicePhase.Speaking, words = PreviewListeningWords),
        "Speaking · whole" to listening.copy(phase = ChatVoicePhase.Speaking, words = PreviewSpeakingWords),
        "Muted" to listening.copy(muted = true),
        "Persona" to listening.copy(choice = ModelChoice(PersonaPresets.personas.first())),
        "Gemini" to listening.copy(choice = ModelChoice(ModelCatalogue["gemini-2.5-pro"])),
        "No key" to listening.copy(toast = ReplyFailure.NO_KEY.toast),
        "No microphone" to VoiceUiState(microphone = false),
        "Loading" to VoiceUiState(),
    )

    override val values: Sequence<VoicePreviewCase> = sequence {
        for (dark in listOf(false, true)) {
            for ((name, state) in states) {
                yield(VoicePreviewCase(name = name, uiState = state, darkTheme = dark))
            }
        }
    }

    override fun getDisplayName(index: Int): String = values.elementAt(index).displayName
}

@Preview(name = "Voice mode", widthDp = 402, heightDp = 874)
@Composable
private fun VoiceScreenPreview(
    @PreviewParameter(VoiceScreenPreviewParameterProvider::class) case: VoicePreviewCase,
) {
    ChatTheme(palette = case.uiState.model.palette, darkTheme = case.darkTheme) {
        VoiceScreen(
            uiState = case.uiState,
            onHistoryClick = {},
            onModelClick = {},
            onSettingsClick = {},
            onSphereTap = {},
            onToggleMute = {},
            onEnd = {},
            onTextClick = {},
        )
    }
}
