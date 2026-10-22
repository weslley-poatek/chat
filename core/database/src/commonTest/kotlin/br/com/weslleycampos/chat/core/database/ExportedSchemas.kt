package br.com.weslleycampos.chat.core.database

/** Set by the `chat.room` convention on every test task, Android host and iOS simulator alike. */
private const val SCHEMA_DIRECTORY_VARIABLE = "ROOM_SCHEMA_DIRECTORY"

/** The absolute path of `core/database/schemas`, which holds one folder per database class. */
internal val schemaDirectory: String
    get() = checkNotNull(environmentVariable(SCHEMA_DIRECTORY_VARIABLE)) {
        "$SCHEMA_DIRECTORY_VARIABLE is not set: run the tests through Gradle"
    }

/** The schema Room exported for [version] of [ChatDatabase], or `null` if there is none. */
internal fun readExportedSchema(version: Int): String? {
    val databaseClass = checkNotNull(ChatDatabase::class.qualifiedName)
    return readTextFile("$schemaDirectory/$databaseClass/$version.json")
}

internal expect fun environmentVariable(name: String): String?

/** The file's UTF-8 text, or `null` if it does not exist. */
internal expect fun readTextFile(path: String): String?
