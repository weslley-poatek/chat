package br.com.weslleycampos.chat.core.database

import br.com.weslleycampos.chat.core.database.daos.CompareDao
import br.com.weslleycampos.chat.core.database.daos.ConversationDao
import br.com.weslleycampos.chat.core.database.daos.ForkDao
import br.com.weslleycampos.chat.core.database.daos.HistoryDao
import br.com.weslleycampos.chat.core.database.daos.MessageDao
import br.com.weslleycampos.chat.core.database.daos.PersonaDao
import org.koin.core.annotation.Single

@Single
internal fun compareDao(database: ChatDatabase): CompareDao = database.compareDao()

@Single
internal fun conversationDao(database: ChatDatabase): ConversationDao = database.conversationDao()

@Single
internal fun forkDao(database: ChatDatabase): ForkDao = database.forkDao()

@Single
internal fun historyDao(database: ChatDatabase): HistoryDao = database.historyDao()

@Single
internal fun messageDao(database: ChatDatabase): MessageDao = database.messageDao()

@Single
internal fun personaDao(database: ChatDatabase): PersonaDao = database.personaDao()

@Single
internal fun transactionRunner(database: ChatDatabase): TransactionRunner = RoomTransactionRunner(database)
