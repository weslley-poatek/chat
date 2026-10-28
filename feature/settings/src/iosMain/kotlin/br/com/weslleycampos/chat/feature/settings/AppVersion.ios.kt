package br.com.weslleycampos.chat.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSBundle

/** The prototype's `platformLabel` on iOS. */
private const val Platform = "ios"

/** The bundle's version name, Xcode's `MARKETING_VERSION`. */
internal const val ShortVersionKey = "CFBundleShortVersionString"

/** The bundle's build number, Xcode's `CURRENT_PROJECT_VERSION`. */
internal const val BuildVersionKey = "CFBundleVersion"

@Composable
internal actual fun rememberAppVersion(): AppVersion = remember {
    appVersion { key -> NSBundle.mainBundle.objectForInfoDictionaryKey(key) }
}

/** The version from an Info.plist [lookup], such as the main bundle's; a key it lacks reads as empty. */
internal fun appVersion(lookup: (String) -> Any?): AppVersion = AppVersion(
    name = (lookup(ShortVersionKey) as? String).orEmpty(),
    code = (lookup(BuildVersionKey) as? String).orEmpty(),
    platform = Platform,
)
