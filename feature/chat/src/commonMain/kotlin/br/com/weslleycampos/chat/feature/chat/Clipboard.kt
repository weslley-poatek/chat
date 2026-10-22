package br.com.weslleycampos.chat.feature.chat

import androidx.compose.ui.platform.ClipEntry

/** [text] as the platform's plain-text clipboard item, for `Clipboard.setClipEntry`. */
internal expect fun plainTextClipEntry(text: String): ClipEntry
