package br.com.weslleycampos.chat.core.database

import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/** The database file, in the app's database directory on Android and its Documents on iOS. */
internal const val DATABASE_FILE_NAME = "chat.db"

/**
 * The configuration every [ChatDatabase] is built with, on disk or in memory: the SQLite that ships
 * inside the app rather than the platform's, so both platforms run the same engine, and queries on
 * [Dispatchers.IO].
 */
internal fun RoomDatabase.Builder<ChatDatabase>.buildChatDatabase(): ChatDatabase = this
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()
