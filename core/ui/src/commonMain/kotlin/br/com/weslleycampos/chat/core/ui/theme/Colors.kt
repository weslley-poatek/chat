package br.com.weslleycampos.chat.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import br.com.weslleycampos.chat.core.ui.resources.CoreUiRes
import br.com.weslleycampos.chat.core.ui.resources.palette_deepseek
import br.com.weslleycampos.chat.core.ui.resources.palette_gemini
import br.com.weslleycampos.chat.core.ui.resources.palette_gpt5
import br.com.weslleycampos.chat.core.ui.resources.palette_llama
import br.com.weslleycampos.chat.core.ui.resources.palette_mistral
import br.com.weslleycampos.chat.core.ui.resources.palette_o3
import br.com.weslleycampos.chat.core.ui.resources.palette_opus
import br.com.weslleycampos.chat.core.ui.resources.palette_sonnet
import org.jetbrains.compose.resources.StringResource

internal const val DisabledAlpha = 0.38f

val White = Color(0xFFFFFFFF)
val Black = Color(0xFF000000)

val Slate0 = Color(0xFFF9FAFC)
val Slate1 = Color(0xFFEEF0F3)
val Slate2 = Color(0xFFE9EBEF)
val Slate3 = Color(0xFFE2E5E8)
val Slate4 = Color(0xFFD2D4D8)
val Slate5 = Color(0xFFA1A5AB)
val Slate6 = Color(0xFF83868C)
val Slate7 = Color(0xFF767B82)
val Slate8 = Color(0xFF6E7278)
val Slate9 = Color(0xFF6B6F76)
val Slate10 = Color(0xFF494D54)
val Slate11 = Color(0xFF2B2E32)
val Slate12 = Color(0xFF222428)
val Slate13 = Color(0xFF16181C)
val Slate14 = Color(0xFF13161B)
val Slate15 = Color(0xFF0E0F12)
val Slate16 = Color(0xFF0B0C0F)

val Red0 = Color(0xFFF97770)
val Red1 = Color(0xFFC53637)

val Coral0 = Color(0xFFFF9173)
val Coral1 = Color(0xFFFC8E73)
val Coral2 = Color(0xFFEC785B)
val Coral3 = Color(0xFFC25237)
val Coral4 = Color(0xFFB75037)

val Amber0 = Color(0xFFF2AD73)
val Amber1 = Color(0xFFAB5900)

val Gold0 = Color(0xFFE9AB2B)
val Gold1 = Color(0xFFDFA635)
val Gold2 = Color(0xFFCE9200)
val Gold3 = Color(0xFFA86D00)
val Gold4 = Color(0xFF9E6800)

val Green0 = Color(0xFF8ED09C)
val Green1 = Color(0xFF67D283)
val Green2 = Color(0xFF68CA80)
val Green3 = Color(0xFF4CB86A)
val Green4 = Color(0xFF1B9247)
val Green5 = Color(0xFF218A45)
val Green6 = Color(0xFF137738)

val Teal0 = Color(0xFF84D5CF)
val Teal1 = Color(0xFF007570)

val Sky0 = Color(0xFF00CCF9)
val Sky1 = Color(0xFF00C5EE)
val Sky2 = Color(0xFF00B2DE)
val Sky3 = Color(0xFF008CB7)
val Sky4 = Color(0xFF0085AC)

val Azure0 = Color(0xFF84CFF3)
val Azure1 = Color(0xFF006E9E)

val Blue0 = Color(0xFF72BAFF)
val Blue1 = Color(0xFF71B4FF)
val Blue2 = Color(0xFF59A0F9)
val Blue3 = Color(0xFF337BD0)
val Blue4 = Color(0xFF3475C4)

val Violet0 = Color(0xFFB7A5FF)
val Violet1 = Color(0xFFB0A0FF)
val Violet2 = Color(0xFF9E8CF4)
val Violet3 = Color(0xFF7B67CC)
val Violet4 = Color(0xFF7563C0)

val Purple0 = Color(0xFFC2A7F4)
val Purple1 = Color(0xFF6D41A9)

@Immutable
data class ChatSurfaceColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val knob: Color = White,
    val scrim: Color = Black.copy(alpha = 0.55f),
)

@Immutable
data class ChatBorderColors(
    val primary: Color,
) {
    val disabled: Color get() = primary.copy(alpha = DisabledAlpha)
}

@Immutable
data class ChatTextColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val onAccent: Color,
) {
    val disabled: Color get() = primary.copy(alpha = DisabledAlpha)
}

@Immutable
data class ChatAccentColors(
    val primary: Color,
    val glow: Color,
    val particle: Color,
)

@Immutable
data class ChatStatusColors(
    val danger: Color,
    val success: Color,
)

@Immutable
data class ChatSyntaxColors(
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val function: Color,
    val type: Color,
)

@Immutable
data class ChatColors(
    val surface: ChatSurfaceColors,
    val border: ChatBorderColors,
    val text: ChatTextColors,
    val accent: ChatAccentColors,
    val status: ChatStatusColors,
    val syntax: ChatSyntaxColors,
    val isDark: Boolean,
)

val CoralDark = ChatColors(
    surface = ChatSurfaceColors(
        primary = Slate15,
        secondary = Slate13,
        tertiary = Slate12,
    ),
    border = ChatBorderColors(primary = Slate11),
    text = ChatTextColors(
        primary = Slate2,
        secondary = Slate5,
        tertiary = Slate9,
        onAccent = Slate16,
    ),
    accent = ChatAccentColors(
        primary = Coral1,
        glow = Coral2,
        particle = Coral0,
    ),
    status = ChatStatusColors(
        danger = Red0,
        success = Green2,
    ),
    syntax = ChatSyntaxColors(
        keyword = Purple0,
        string = Green0,
        comment = Slate8,
        number = Amber0,
        function = Azure0,
        type = Teal0,
    ),
    isDark = true,
)

val CoralLight = ChatColors(
    surface = ChatSurfaceColors(
        primary = Slate0,
        secondary = Slate1,
        tertiary = Slate3,
    ),
    border = ChatBorderColors(primary = Slate4),
    text = ChatTextColors(
        primary = Slate14,
        secondary = Slate10,
        tertiary = Slate7,
        onAccent = White,
    ),
    accent = ChatAccentColors(
        primary = Coral4,
        glow = Coral2,
        particle = Coral3,
    ),
    status = ChatStatusColors(
        danger = Red1,
        success = Green5,
    ),
    syntax = ChatSyntaxColors(
        keyword = Purple1,
        string = Green6,
        comment = Slate6,
        number = Amber1,
        function = Azure1,
        type = Teal1,
    ),
    isDark = false,
)

val GreenDark = CoralDark.copy(
    accent = ChatAccentColors(
        primary = Green2,
        glow = Green3,
        particle = Green1,
    ),
)

val GreenLight = CoralLight.copy(
    accent = ChatAccentColors(
        primary = Green5,
        glow = Green3,
        particle = Green4,
    ),
)

val BlueDark = CoralDark.copy(
    accent = ChatAccentColors(
        primary = Blue1,
        glow = Blue2,
        particle = Blue0,
    ),
)

val BlueLight = CoralLight.copy(
    accent = ChatAccentColors(
        primary = Blue4,
        glow = Blue2,
        particle = Blue3,
    ),
)

val VioletDark = CoralDark.copy(
    accent = ChatAccentColors(
        primary = Violet1,
        glow = Violet2,
        particle = Violet0,
    ),
)

val VioletLight = CoralLight.copy(
    accent = ChatAccentColors(
        primary = Violet4,
        glow = Violet2,
        particle = Violet3,
    ),
)

val SkyDark = CoralDark.copy(
    accent = ChatAccentColors(
        primary = Sky1,
        glow = Sky2,
        particle = Sky0,
    ),
)

val SkyLight = CoralLight.copy(
    accent = ChatAccentColors(
        primary = Sky4,
        glow = Sky2,
        particle = Sky3,
    ),
)

val GoldDark = CoralDark.copy(
    accent = ChatAccentColors(
        primary = Gold1,
        glow = Gold2,
        particle = Gold0,
    ),
)

val GoldLight = CoralLight.copy(
    accent = ChatAccentColors(
        primary = Gold4,
        glow = Gold2,
        particle = Gold3,
    ),
)

enum class ChatPalette(
    val displayName: StringResource,
    val light: ChatColors,
    val dark: ChatColors,
) {
    SONNET(displayName = CoreUiRes.string.palette_sonnet, light = CoralLight, dark = CoralDark),
    OPUS(displayName = CoreUiRes.string.palette_opus, light = CoralLight, dark = CoralDark),
    GPT5(displayName = CoreUiRes.string.palette_gpt5, light = GreenLight, dark = GreenDark),
    O3(displayName = CoreUiRes.string.palette_o3, light = GreenLight, dark = GreenDark),
    GEMINI(displayName = CoreUiRes.string.palette_gemini, light = BlueLight, dark = BlueDark),
    LLAMA(displayName = CoreUiRes.string.palette_llama, light = VioletLight, dark = VioletDark),
    DEEPSEEK(displayName = CoreUiRes.string.palette_deepseek, light = SkyLight, dark = SkyDark),
    MISTRAL(displayName = CoreUiRes.string.palette_mistral, light = GoldLight, dark = GoldDark);

    fun colors(dark: Boolean): ChatColors = if (dark) this.dark else light

    companion object {
        val Default: ChatPalette = SONNET
    }
}

val LocalChatPalette = staticCompositionLocalOf<ChatPalette> {
    error("No ChatPalette provided. Wrap your content in ChatTheme { ... }")
}
val LocalChatColors = staticCompositionLocalOf<ChatColors> {
    error("No ChatColors provided. Wrap your content in ChatTheme { ... }")
}
