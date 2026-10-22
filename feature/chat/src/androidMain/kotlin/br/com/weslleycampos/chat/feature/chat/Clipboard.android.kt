package br.com.weslleycampos.chat.feature.chat

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry

/** The label the system's clipboard preview may show. */
private const val ClipLabel = "code"

internal actual fun plainTextClipEntry(text: String): ClipEntry = ClipEntry(ClipData.newPlainText(ClipLabel, text))
