package br.com.weslleycampos.chat.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import br.com.weslleycampos.chat.core.ui.resources.CoreUiRes
import br.com.weslleycampos.chat.core.ui.resources.ibm_plex_mono_regular
import br.com.weslleycampos.chat.core.ui.resources.ibm_plex_sans_medium
import br.com.weslleycampos.chat.core.ui.resources.ibm_plex_sans_regular
import br.com.weslleycampos.chat.core.ui.resources.ibm_plex_sans_semibold
import org.jetbrains.compose.resources.Font

@Immutable
data class ChatTypography(
    val displayLarge: TextStyle,
    val displayMedium: TextStyle,
    val displaySmall: TextStyle,
    val headlineLarge: TextStyle,
    val headlineMedium: TextStyle,
    val headlineSmall: TextStyle,
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
    val titleSmall: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val bodySmall: TextStyle,
    val labelLarge: TextStyle,
    val labelMedium: TextStyle,
    val labelSmall: TextStyle,
    val input: TextStyle,
    val code: TextStyle,
    val eyebrow: TextStyle,
    val caption: TextStyle,
)

private val BaseLineHeight = 1.45.em

val sans: FontFamily
    @Composable get() = FontFamily(
        Font(resource = CoreUiRes.font.ibm_plex_sans_regular, weight = FontWeight.Normal),
        Font(resource = CoreUiRes.font.ibm_plex_sans_medium, weight = FontWeight.Medium),
        Font(resource = CoreUiRes.font.ibm_plex_sans_semibold, weight = FontWeight.SemiBold),
    )

val mono: FontFamily
    @Composable get() = FontFamily(
        Font(resource = CoreUiRes.font.ibm_plex_mono_regular, weight = FontWeight.Normal),
    )

val chatTypography: ChatTypography
    @Composable get() = ChatTypography(
        displayLarge = TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
            lineHeight = 1.15.em,
            letterSpacing = (-0.02).em,
        ),
        displayMedium = TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = BaseLineHeight,
            letterSpacing = (-0.02).em,
        ),
        displaySmall = TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = BaseLineHeight,
            letterSpacing = (-0.02).em,
        ),
        headlineLarge = TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            lineHeight = BaseLineHeight,
            letterSpacing = (-0.01).em,
        ),
        headlineMedium = TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = BaseLineHeight,
        ),
        headlineSmall = TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = BaseLineHeight,
        ),
        titleLarge = TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            lineHeight = BaseLineHeight,
        ),
        titleMedium = TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = BaseLineHeight,
        ),
        titleSmall = TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = BaseLineHeight,
        ),
        bodyLarge = TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = BaseLineHeight,
        ),
        bodyMedium = TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = BaseLineHeight,
        ),
        bodySmall = TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = BaseLineHeight,
        ),
        labelLarge = TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = BaseLineHeight,
        ),
        labelMedium = TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = BaseLineHeight,
        ),
        labelSmall = TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            lineHeight = BaseLineHeight,
        ),
        input = TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = BaseLineHeight,
        ),
        code = TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Normal,
            fontSize = 12.5f.sp,
            lineHeight = 1.55.em,
        ),
        eyebrow = TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            lineHeight = BaseLineHeight,
            letterSpacing = 0.14.em,
        ),
        caption = TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Normal,
            fontSize = 10.sp,
            lineHeight = BaseLineHeight,
        ),
    )

val LocalChatTypography = staticCompositionLocalOf<ChatTypography> {
    error("No ChatTypography provided. Wrap your content in ChatTheme { ... }")
}
