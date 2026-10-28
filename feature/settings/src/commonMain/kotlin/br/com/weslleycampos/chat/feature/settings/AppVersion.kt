package br.com.weslleycampos.chat.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/**
 * The installed app's version, as the platform reports it, for Settings' footer.
 *
 * @property name The version name, "1.0": `versionName` on Android, `CFBundleShortVersionString`
 *   (`MARKETING_VERSION`) on iOS. Empty when the platform reports none.
 * @property code The build number, "1": `versionCode` on Android, `CFBundleVersion`
 *   (`CURRENT_PROJECT_VERSION`) on iOS. Empty when the platform reports none.
 * @property platform "android" or "ios", as the prototype's `platformLabel`.
 */
@Immutable
data class AppVersion(val name: String, val code: String, val platform: String)

/** The app's version, read once from the platform: the package manager on Android, the main bundle on iOS. */
@Composable
internal expect fun rememberAppVersion(): AppVersion

/** The app's name as the footer writes it; the prototype's "ctx" is "Chat" here. */
private const val AppName = "Chat"

/** The prototype's separator, `" · "`. */
private const val Separator = " · "

/**
 * The footer, the prototype's "ctx 0.9.2 (418) · ios": "Chat 1.0 (1) · android". A part the
 * platform does not report is left out, so it never reads "Chat  () · ios".
 */
fun settingsFooter(version: AppVersion): String = buildString {
    append(AppName)
    if (version.name.isNotBlank()) append(" ").append(version.name.trim())
    if (version.code.isNotBlank()) append(" (").append(version.code.trim()).append(")")
    append(Separator).append(version.platform)
}
