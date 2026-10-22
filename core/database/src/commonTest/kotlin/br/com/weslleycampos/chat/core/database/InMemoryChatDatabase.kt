package br.com.weslleycampos.chat.core.database

/**
 * A new, empty [ChatDatabase] in memory, built with the app's driver and dispatcher. Close it when
 * the test ends.
 *
 * Room declares its in-memory builder per platform rather than in common code, so each test source
 * set supplies the same one-line body.
 */
internal expect fun inMemoryChatDatabase(): ChatDatabase
