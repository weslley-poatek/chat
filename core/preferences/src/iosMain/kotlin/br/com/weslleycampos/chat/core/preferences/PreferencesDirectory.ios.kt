@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package br.com.weslleycampos.chat.core.preferences

import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

internal fun preferencesDirectory(): String {
    val fileManager = NSFileManager.defaultManager
    val base = fileManager.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask).firstOrNull() as? NSURL
        ?: error("Unable to resolve iOS Application Support directory")
    val directory = base.URLByAppendingPathComponent("chat")
        ?: error("Unable to resolve iOS preferences directory")
    check(
        fileManager.createDirectoryAtURL(directory, withIntermediateDirectories = true, attributes = null, error = null)
    ) {
        "Unable to create iOS preferences directory"
    }
    check(directory.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)) {
        "Unable to exclude iOS preferences directory from backup"
    }
    return directory.path ?: error("Unable to resolve iOS preferences directory path")
}
