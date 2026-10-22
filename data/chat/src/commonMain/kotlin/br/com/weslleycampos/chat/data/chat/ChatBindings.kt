package br.com.weslleycampos.chat.data.chat

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import br.com.weslleycampos.chat.core.database.TransactionRunner
import br.com.weslleycampos.chat.core.database.daos.ConversationDao
import br.com.weslleycampos.chat.core.database.daos.MessageDao
import br.com.weslleycampos.chat.core.database.daos.PersonaDao
import br.com.weslleycampos.chat.core.preferences.qualifiers.UnencryptedPreferences
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
    models: ModelRepository,
): ChatRepository = RoomChatRepository(
    conversationDao = conversationDao,
    messageDao = messageDao,
    transactions = transactions,
    models = models,
    streamer = ReplyStreamer(messageDao = messageDao, clock = Clock.System, random = Random.Default),
    clock = Clock.System,
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> }),
)

/**
 * The app's one [ModelRepository]: personas in Room, favorites and the default model in the plain
 * DataStore. The presets are seeded in a scope as long-lived as the process.
 */
@Single
internal fun modelRepository(
    personaDao: PersonaDao,
    @UnencryptedPreferences preferences: DataStore<Preferences>,
): ModelRepository = LocalModelRepository(
    personaDao = personaDao,
    preferences = preferences,
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
)
