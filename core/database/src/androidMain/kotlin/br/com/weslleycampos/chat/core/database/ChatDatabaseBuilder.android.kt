package br.com.weslleycampos.chat.core.database

import android.content.Context
import androidx.room3.Room
import org.koin.core.annotation.Single

@Single
internal fun createChatDatabase(context: Context): ChatDatabase {
    val appContext = context.applicationContext
    return Room.databaseBuilder<ChatDatabase>(
        context = appContext,
        name = appContext.getDatabasePath(DATABASE_FILE_NAME).absolutePath,
    ).buildChatDatabase()
}
