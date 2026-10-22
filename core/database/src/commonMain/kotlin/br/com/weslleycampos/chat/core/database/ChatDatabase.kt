package br.com.weslleycampos.chat.core.database

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import br.com.weslleycampos.chat.core.database.entities.PlaceholderEntity

/** Raised by one with every schema change, next to a new `@AutoMigration` into it. */
internal const val DATABASE_VERSION = 1

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
    ],
    version = DATABASE_VERSION,
    exportSchema = true,
)
@ConstructedBy(ChatDatabaseConstructor::class)
abstract class ChatDatabase : RoomDatabase()

/**
 * Room's compiler writes the `actual` on every target, so the database is created without the
 * reflection Kotlin/Native lacks.
 */
@Suppress("KotlinNoActualForExpect")
internal expect object ChatDatabaseConstructor : RoomDatabaseConstructor<ChatDatabase> {
    override fun initialize(): ChatDatabase
}
