package br.com.weslleycampos.chat.core.database

import androidx.room3.AutoMigration
import androidx.room3.ColumnTypeConverters
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import br.com.weslleycampos.chat.core.database.daos.ConversationDao
import br.com.weslleycampos.chat.core.database.daos.MessageDao
import br.com.weslleycampos.chat.core.database.entities.ConversationEntity
import br.com.weslleycampos.chat.core.database.entities.MessageEntity
import br.com.weslleycampos.chat.core.database.entities.PlaceholderEntity

/**
 * Raised by one with every schema change, next to a new `@AutoMigration` into it.
 *
 * - 1: `placeholder` only.
 * - 2: `conversations` and `messages`.
 */
internal const val DATABASE_VERSION = 2

/**
 * The app's single on-device database: everything Chat keeps lives here and survives restarts.
 *
 * Every schema change raises [DATABASE_VERSION], adds an `@AutoMigration` from the previous version
 * to `autoMigrations`, and commits the schema Room exports to `core/database/schemas`.
 * There is no destructive fallback, so a missing migration fails the first open instead of
 * deleting the user's history.
 */
@Database(
    entities = [
        PlaceholderEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
    ],
    version = DATABASE_VERSION,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
    ],
    exportSchema = true,
)
@ConstructedBy(ChatDatabaseConstructor::class)
@ColumnTypeConverters(ChatTypeConverters::class)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao

    abstract fun messageDao(): MessageDao
}

/**
 * Room's compiler writes the `actual` on every target, so the database is created without the
 * reflection Kotlin/Native lacks.
 */
@Suppress("KotlinNoActualForExpect")
internal expect object ChatDatabaseConstructor : RoomDatabaseConstructor<ChatDatabase> {
    override fun initialize(): ChatDatabase
}
