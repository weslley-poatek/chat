package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.core.database.TransactionRunner
import br.com.weslleycampos.chat.core.database.daos.ConversationDao
import br.com.weslleycampos.chat.core.database.daos.MessageDao
import br.com.weslleycampos.chat.data.chat.fakes.FakeChatDatabase
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.dsl.koinApplication
import org.koin.plugin.module.dsl.module
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

/**
 * `DataChatModule` next to fakes of what `CoreDatabaseModule` provides in the app, so the Koin
 * compiler plugin checks the graph while this compiles and no database is opened.
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
}

@Module
internal class FakeDatabaseModule {
    @Single
    fun database(): FakeChatDatabase = FakeChatDatabase()

    @Single
    fun conversationDao(database: FakeChatDatabase): ConversationDao = database.conversationDao

    @Single
    fun messageDao(database: FakeChatDatabase): MessageDao = database.messageDao

    @Single
    fun transactionRunner(database: FakeChatDatabase): TransactionRunner = database.transactions
}
