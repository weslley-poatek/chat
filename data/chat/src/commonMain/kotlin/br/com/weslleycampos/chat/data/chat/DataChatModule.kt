package br.com.weslleycampos.chat.data.chat

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

/**
 * The chat thread's data: [ChatRepository] on the DAOs of `CoreDatabaseModule`, which the app's
 * root module includes next to this one.
 */
@Module
@ComponentScan
class DataChatModule
