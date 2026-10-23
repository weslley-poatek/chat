package br.com.weslleycampos.chat.feature.history

import br.com.weslleycampos.chat.data.chat.ChatRepository
import br.com.weslleycampos.chat.data.chat.models.ChatModel
import br.com.weslleycampos.chat.data.chat.models.Conversation
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/** 14:02 UTC on the prototype's "today", Sep 14. */
internal val Now: Instant = Instant.parse("2026-09-14T14:02:00Z")

internal class FixedClock(var now: Instant = Now) : Clock {
    override fun now(): Instant = now
}

/** Times on [clock], in UTC. */
internal fun testTimes(clock: Clock = FixedClock()) = HistoryTimes(clock = clock, zone = { TimeZone.UTC })

internal val Gemini: ChatModel = ModelCatalogue["gemini"]
internal val Gpt5: ChatModel = ModelCatalogue["gpt5"]
internal val Llama: ChatModel = ModelCatalogue["llama"]

/**
 * An in-memory [ChatRepository] that keeps the real one's History contract: the list leaves archived
 * conversations out, newest first, the later id on a tie; the trimmed query matches a title, the
 * user's text, the short name or, while comparing, the compared model's; pin, archive and delete
 * say whether they changed anything. History never sends or chooses.
 */
internal class FakeChatRepository : ChatRepository {
    val conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val messages = MutableStateFlow<Map<Long, List<Message>>>(emptyMap())

    /** Every query `observeHistory` was asked for, in order. */
    val queries = mutableListOf<String>()

    /** Every `setPinned`, `archive` and `delete`, in order. */
    val pins = mutableListOf<Pair<Long, Boolean>>()
    val archives = mutableListOf<Long>()
    val deletes = mutableListOf<Long>()

    /** When set, every pin, archive and delete throws it. */
    var failure: Exception? = null
    private var nextId = 1L

    override fun observeConversation(id: Long): Flow<Conversation?> =
        conversations.map { all -> all.firstOrNull { it.id == id } }.distinctUntilChanged()

    override fun observeMessages(conversationId: Long): Flow<List<Message>> =
        messages.map { it[conversationId].orEmpty() }.distinctUntilChanged()

    override fun observeRecentConversations(): Flow<List<Conversation>> =
        observeHistory().map { it.take(ChatRepository.RECENT_CONVERSATIONS) }.distinctUntilChanged()

    override fun observeHistory(query: String): Flow<List<Conversation>> {
        queries += query
        return combine(conversations, messages) { all, threads ->
            val text = query.trim()
            fun matches(conversation: Conversation) = text.isEmpty() ||
                conversation.title.contains(text, ignoreCase = true) ||
                conversation.model.shortName.contains(text, ignoreCase = true) ||
                conversation.comparing?.shortName?.contains(text, ignoreCase = true) == true ||
                threads[conversation.id].orEmpty().any { message ->
                    message is Message.User && message.text.contains(text, ignoreCase = true)
                }
            all.filter { !it.archived && matches(it) }
                .sortedWith(compareByDescending<Conversation> { it.updatedAt }.thenByDescending { it.id })
        }.distinctUntilChanged()
    }

    override fun observeConversationCount(): Flow<Int> =
        conversations.map { all -> all.count { !it.archived } }.distinctUntilChanged()

    /** History never sends. */
    override suspend fun send(conversationId: Long?, text: String, newChat: ModelChoice?): Long? = null

    /** History never chooses a model. */
    override suspend fun choose(conversationId: Long, choice: ModelChoice): Boolean = false

    override suspend fun setPinned(conversationId: Long, pinned: Boolean): Boolean {
        pins += conversationId to pinned
        return rewrite(conversationId, { it.pinned != pinned }) { it.copy(pinned = pinned) }
    }

    override suspend fun archive(conversationId: Long): Boolean {
        archives += conversationId
        return rewrite(conversationId, { !it.archived }) { it.copy(archived = true) }
    }

    override suspend fun delete(conversationId: Long): Boolean {
        deletes += conversationId
        failure?.let { throw it }
        if (conversations.value.none { it.id == conversationId }) return false
        messages.update { it - conversationId }
        conversations.update { all -> all.filterNot { it.id == conversationId } }
        return true
    }

    private fun rewrite(id: Long, changes: (Conversation) -> Boolean, update: (Conversation) -> Conversation): Boolean {
        failure?.let { throw it }
        if (conversations.value.none { it.id == id && changes(it) }) return false
        conversations.update { all -> all.map { if (it.id == id) update(it) else it } }
        return true
    }

    /** A conversation titled [title] on [model], last updated at [updatedAt], with [title] as the user's message. */
    fun seed(
        title: String,
        model: ChatModel = ModelCatalogue.default,
        updatedAt: Instant = Now,
        pinned: Boolean = false,
    ): Conversation {
        val conversation = Conversation(
            id = nextId++,
            title = title,
            model = model,
            createdAt = updatedAt,
            updatedAt = updatedAt,
            pinned = pinned,
        )
        conversations.update { it + conversation }
        val user = Message.User(id = nextId++, conversationId = conversation.id, text = title, createdAt = updatedAt)
        messages.update { it + (conversation.id to listOf(user)) }
        return conversation
    }
}
