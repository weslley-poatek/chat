@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package br.com.weslleycampos.chat.core.database

import kotlinx.cinterop.toKString
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.posix.getenv

internal actual fun environmentVariable(name: String): String? = getenv(name)?.toKString()

internal actual fun readTextFile(path: String): String? =
    NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null)
