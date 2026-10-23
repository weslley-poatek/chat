package br.com.weslleycampos.chat.core.database

import androidx.room3.AutoMigration
import androidx.room3.ColumnTypeConverters
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import br.com.weslleycampos.chat.core.database.daos.ConversationDao
import br.com.weslleycampos.chat.core.database.daos.ForkDao
import br.com.weslleycampos.chat.core.database.daos.HistoryDao
import br.com.weslleycampos.chat.core.database.daos.MessageDao
import br.com.weslleycampos.chat.core.database.daos.PersonaDao
import br.com.weslleycampos.chat.core.database.entities.ConversationEntity
import br.com.weslleycampos.chat.core.database.entities.MessageEntity
import br.com.weslleycampos.chat.core.database.entities.PersonaEntity
import br.com.weslleycampos.chat.core.database.entities.PlaceholderEntity

/**
 * Raised by one with every schema change, next to a new `@AutoMigration` into it.
 *
 * - 1: `placeholder` only.
 * - 2: `conversations` and `messages`.
 * - 3: `personas`, and a nullable `personaId` on `conversations` and `messages`.
 * - 4: `pinned` and `archived` on `conversations`, both `0` on the rows already there.
 * - 5: a fork's origin on `conversations`: nullable `forkedFromTitle` and `forkedAtMessage`, and
 *   `forkBannerDismissed`, `0` on the rows already there.
 */
internal const val DATABASE_VERSION = 5

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
        PersonaEntity::class,
    ],
    version = DATABASE_VERSION,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
    ],
    exportSchema = true,
)
@ConstructedBy(ChatDatabaseConstructor::class)
@ColumnTypeConverters(ChatTypeConverters::class)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao

    abstract fun forkDao(): ForkDao

    abstract fun historyDao(): HistoryDao

    abstract fun messageDao(): MessageDao

    abstract fun personaDao(): PersonaDao
}

/**
 * Room's compiler writes the `actual` on every target, so the database is created without the
 * reflection Kotlin/Native lacks.
 */
@Suppress("KotlinNoActualForExpect")
internal expect object ChatDatabaseConstructor : RoomDatabaseConstructor<ChatDatabase> {
    override fun initialize(): ChatDatabase
}
