package br.com.weslleycampos.chat.core.database

import androidx.room3.Room

internal actual fun inMemoryChatDatabase(): ChatDatabase =
    Room.inMemoryDatabaseBuilder<ChatDatabase>().buildChatDatabase()
