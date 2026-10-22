@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package br.com.weslleycampos.chat.core.database

import androidx.room3.Room
import org.koin.core.annotation.Single
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@Single
internal fun createChatDatabase(): ChatDatabase = Room.databaseBuilder<ChatDatabase>(
    name = chatDatabasePath(),
).buildChatDatabase()

/** `chat.db` in the app's Documents directory. */
internal fun chatDatabasePath(): String {
    val directory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    val path = directory?.path ?: error("Unable to resolve the iOS Documents directory")
    return "$path/$DATABASE_FILE_NAME"
}
