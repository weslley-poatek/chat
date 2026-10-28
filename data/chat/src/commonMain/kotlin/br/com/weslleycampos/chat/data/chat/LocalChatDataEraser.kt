package br.com.weslleycampos.chat.data.chat

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import br.com.weslleycampos.chat.core.database.daos.ConversationDao

/**
 * [ChatDataEraser] on Room and the plain DataStore: one statement deletes the conversations, their
 * messages going with them through the foreign key's cascade, then one edit removes
 * [LocalModelRepository]'s keys. The `personas` table is left as it is.
 */
internal class LocalChatDataEraser(
    private val conversationDao: ConversationDao,
    private val preferences: DataStore<Preferences>,
) : ChatDataEraser {
    override suspend fun eraseAll() {
        conversationDao.deleteAll()
        preferences.edit { stored ->
            stored.remove(LocalModelRepository.FavoritesKey)
            stored.remove(LocalModelRepository.DefaultModelKey)
        }
    }
}
