package br.com.weslleycampos.chat.feature.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** The prototype's `platformLabel` on Android. */
private const val Platform = "android"

@Composable
internal actual fun rememberAppVersion(): AppVersion {
    val context = LocalContext.current
    return remember(context) { context.appVersion() }
}

/** The installed package's `versionName` and `versionCode`, as `PackageManager` reports them. */
internal fun Context.appVersion(): AppVersion {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
    }
    return AppVersion(name = info.versionName.orEmpty(), code = info.longVersionCode.toString(), platform = Platform)
}
