package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.core.database.TransactionRunner
import br.com.weslleycampos.chat.core.database.daos.ConversationDao
import br.com.weslleycampos.chat.core.database.daos.MessageDao
import br.com.weslleycampos.chat.data.chat.replies.ReplyStreamer
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.Single
import kotlin.random.Random
import kotlin.time.Clock

/**
 * The app's one [ChatRepository], on the system clock and a random source, streaming replies in a
 * scope as long-lived as the process. A reply whose stream fails is already marked interrupted by
 * then, so the handler keeps that failure from taking the app down with it.
 */
@Single
internal fun chatRepository(
    conversationDao: ConversationDao,
    messageDao: MessageDao,
    transactions: TransactionRunner,
): ChatRepository = RoomChatRepository(
    conversationDao = conversationDao,
    messageDao = messageDao,
    transactions = transactions,
    streamer = ReplyStreamer(messageDao = messageDao, clock = Clock.System, random = Random.Default),
    clock = Clock.System,
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> }),
)
