package br.com.weslleycampos.chat.core.database

import br.com.weslleycampos.chat.core.database.daos.ConversationDao
import br.com.weslleycampos.chat.core.database.daos.MessageDao
import org.koin.core.annotation.Single

@Single
internal fun conversationDao(database: ChatDatabase): ConversationDao = database.conversationDao()

@Single
internal fun messageDao(database: ChatDatabase): MessageDao = database.messageDao()

@Single
internal fun transactionRunner(database: ChatDatabase): TransactionRunner = RoomTransactionRunner(database)
