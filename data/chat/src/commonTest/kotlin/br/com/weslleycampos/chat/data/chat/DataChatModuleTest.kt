package br.com.weslleycampos.chat.data.chat

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import br.com.weslleycampos.chat.core.database.TransactionRunner
import br.com.weslleycampos.chat.core.database.daos.ConversationDao
import br.com.weslleycampos.chat.core.database.daos.ForkDao
import br.com.weslleycampos.chat.core.database.daos.HistoryDao
import br.com.weslleycampos.chat.core.database.daos.MessageDao
import br.com.weslleycampos.chat.core.database.daos.PersonaDao
import br.com.weslleycampos.chat.core.preferences.qualifiers.UnencryptedPreferences
import br.com.weslleycampos.chat.data.chat.fakes.FakeChatDatabase
import br.com.weslleycampos.chat.data.chat.fakes.FakePreferences
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.dsl.koinApplication
import org.koin.plugin.module.dsl.module
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

/**
 * `DataChatModule` next to fakes of what `CoreDatabaseModule` and `PreferencesModule` provide in the
 * app, so the Koin compiler plugin checks the graph while this compiles and no database is opened.
 */
class DataChatModuleTest {
    @Test
    fun koinProvidesOneRoomChatRepositoryOnTheDatabasesDaos() {
        val koin = koinApplication {
            module<FakeDatabaseModule>()
            module<DataChatModule>()
        }.koin
        try {
            assertIs<RoomChatRepository>(koin.get<ChatRepository>())
            assertSame(koin.get<ChatRepository>(), koin.get<ChatRepository>())
        } finally {
            koin.close()
        }
    }

    @Test
    fun koinProvidesOneRoomForkRepositoryOnTheDatabasesDaos() {
        val koin = koinApplication {
            module<FakeDatabaseModule>()
            module<DataChatModule>()
        }.koin
        try {
            assertIs<RoomForkRepository>(koin.get<ForkRepository>())
            assertSame(koin.get<ForkRepository>(), koin.get<ForkRepository>())
        } finally {
            koin.close()
        }
    }

    @Test
    fun koinProvidesOneLocalModelRepositoryOnThePersonaDaoAndThePlainPreferences() {
        val koin = koinApplication {
            module<FakeDatabaseModule>()
            module<DataChatModule>()
        }.koin
        try {
            assertIs<LocalModelRepository>(koin.get<ModelRepository>())
            assertSame(koin.get<ModelRepository>(), koin.get<ModelRepository>())
        } finally {
            koin.close()
        }
    }
}

@Module
internal class FakeDatabaseModule {
    @Single
    fun database(): FakeChatDatabase = FakeChatDatabase()

    @Single
    fun conversationDao(database: FakeChatDatabase): ConversationDao = database.conversationDao

    @Single
    fun forkDao(database: FakeChatDatabase): ForkDao = database.forkDao

    @Single
    fun historyDao(database: FakeChatDatabase): HistoryDao = database.historyDao

    @Single
    fun messageDao(database: FakeChatDatabase): MessageDao = database.messageDao

    @Single
    fun personaDao(database: FakeChatDatabase): PersonaDao = database.personaDao

    @Single
    fun transactionRunner(database: FakeChatDatabase): TransactionRunner = database.transactions

    @Single
    @UnencryptedPreferences
    fun preferences(): DataStore<Preferences> = FakePreferences()
}
