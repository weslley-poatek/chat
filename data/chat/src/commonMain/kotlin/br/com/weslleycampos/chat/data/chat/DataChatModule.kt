package br.com.weslleycampos.chat.data.chat

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

/**
 * The chat's data: [ChatRepository], [ForkRepository], [CompareRepository], [VoiceRepository],
 * [ModelRepository] and [ChatDataEraser] on the DAOs of `CoreDatabaseModule` and the plain DataStore
 * of `PreferencesModule`, which the app's root module includes next to this one.
 */
@Module
@ComponentScan
class DataChatModule
