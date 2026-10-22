package br.com.weslleycampos.chat.feature.chat

import br.com.weslleycampos.chat.data.chat.ChatRepository
import br.com.weslleycampos.chat.data.chat.ModelRepository
import br.com.weslleycampos.chat.data.chat.models.Block
import br.com.weslleycampos.chat.data.chat.models.ChatModel
import br.com.weslleycampos.chat.data.chat.models.Conversation
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.Persona
import br.com.weslleycampos.chat.data.chat.models.PersonaPresets
import br.com.weslleycampos.chat.data.chat.models.ReplyStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** 14:02 UTC on the prototype's "today", Sep 14. */
internal val Now: Instant = Instant.parse("2026-09-14T14:02:00Z")

internal class FixedClock(var now: Instant = Now) : Clock {
    override fun now(): Instant = now
}

/** Times on [clock], in UTC. */
internal fun testTimes(clock: Clock = FixedClock()) = ChatTimes(clock = clock, timeZone = { TimeZone.UTC })

internal val Gemini: ChatModel = ModelCatalogue["gemini"]
internal val Gpt5: ChatModel = ModelCatalogue["gpt5"]
internal val Llama: ChatModel = ModelCatalogue["llama"]
internal val Mistral: ChatModel = ModelCatalogue["mistral"]

/** The presets: "Code reviewer" on sonnet, "Terse" on gpt5, "Paper digest" on gemini. */
internal val Reviewer: Persona = PersonaPresets.personas[0]
internal val Terse: Persona = PersonaPresets.personas[1]

/** The fake's reply: the prototype's first canned answer. Block 3 is the code. */
internal val ReplyBlocks = listOf(
    Block.Paragraph("Two likely causes, in order of probability:"),
    Block.NumberedList(listOf("A stale value.", "A reassigned timeout id.")),
    Block.Paragraph("Minimal fix:"),
    Block.Code(language = "ts", text = "return () => clearTimeout(id);"),
    Block.Paragraph("If it still reads stale, log value inside the callback."),
)
internal const val ReplyCodeIndex = 3

/** The fake reply thinks this long, then reveals a block every [TickTime], then is done. */
internal val ThinkingTime: Duration = 400.milliseconds
internal val TickTime: Duration = 100.milliseconds
internal val ReplyLatency: Duration = 800.milliseconds

/** From the send to the reply being done. */
internal val ReplyTime: Duration = ThinkingTime + TickTime * ReplyBlocks.size

/**
 * An in-memory [ChatRepository] that keeps the real one's contract: [send] saves the message and a
 * thinking reply, returns the conversation's id, and plays the reply on [scope]. [answer], when
 * set, replaces all of that.
 */
internal class FakeChatRepository(private val scope: CoroutineScope? = null) : ChatRepository {
    val conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val messages = MutableStateFlow<Map<Long, List<Message>>>(emptyMap())
    val sends = mutableListOf<Pair<Long?, String>>()

    /** The `newChat` choice of every send, in order. */
    val newChatChoices = mutableListOf<ModelChoice?>()
    var answer: (suspend (conversationId: Long?, text: String) -> Long?)? = null

    /** Every `choose`, in order. */
    val chooses = mutableListOf<Pair<Long, ModelChoice>>()

    /** When set, `choose` waits for it, then saves the choice only if it answers `true`. */
    var chooseAnswer: (suspend (conversationId: Long, choice: ModelChoice) -> Boolean)? = null
    private var nextId = 1L

    override fun observeConversation(id: Long): Flow<Conversation?> =
        conversations.map { all -> all.firstOrNull { it.id == id } }.distinctUntilChanged()

    override fun observeMessages(conversationId: Long): Flow<List<Message>> =
        messages.map { it[conversationId].orEmpty() }.distinctUntilChanged()

    override fun observeRecentConversations(): Flow<List<Conversation>> = conversations
        .map { all ->
            all.filterNot { it.archived }.sortedByDescending { it.updatedAt }.take(ChatRepository.RECENT_CONVERSATIONS)
        }
        .distinctUntilChanged()

    /** Like the real one: not archived, newest first, the trimmed query in a title, user text or short name. */
    override fun observeHistory(query: String): Flow<List<Conversation>> =
        combine(conversations, messages) { all, threads ->
            val text = query.trim()
            fun matches(conversation: Conversation) = text.isEmpty() ||
                conversation.title.contains(text, ignoreCase = true) ||
                conversation.model.shortName.contains(text, ignoreCase = true) ||
                threads[conversation.id].orEmpty().any { message ->
                    message is Message.User && message.text.contains(text, ignoreCase = true)
                }
            all.filter { !it.archived && matches(it) }
                .sortedWith(compareByDescending<Conversation> { it.updatedAt }.thenByDescending { it.id })
        }.distinctUntilChanged()

    override fun observeConversationCount(): Flow<Int> =
        conversations.map { all -> all.count { !it.archived } }.distinctUntilChanged()

    override suspend fun send(conversationId: Long?, text: String, newChat: ModelChoice?): Long? {
        sends += conversationId to text
        newChatChoices += newChat
        answer?.let { return it(conversationId, text) }
        return play(conversationId, text.trim(), newChat ?: ModelChoice(ModelCatalogue.default))
    }

    /** Like the real one: a model drops the persona, a persona answers with its own model. */
    override suspend fun choose(conversationId: Long, choice: ModelChoice): Boolean {
        chooses += conversationId to choice
        if (chooseAnswer?.invoke(conversationId, choice) == false) return false
        if (conversations.value.none { it.id == conversationId }) return false
        val model = choice.persona?.model ?: choice.model
        conversations.update { all ->
            all.map { if (it.id == conversationId) it.copy(model = model, persona = choice.persona) else it }
        }
        return true
    }

    override suspend fun setPinned(conversationId: Long, pinned: Boolean): Boolean =
        rewrite(conversationId, { it.pinned != pinned }) { it.copy(pinned = pinned) }

    override suspend fun archive(conversationId: Long): Boolean =
        rewrite(conversationId, { !it.archived }) { it.copy(archived = true) }

    override suspend fun delete(conversationId: Long): Boolean {
        if (conversations.value.none { it.id == conversationId }) return false
        messages.update { it - conversationId }
        conversations.update { all -> all.filterNot { it.id == conversationId } }
        return true
    }

    private fun rewrite(id: Long, changes: (Conversation) -> Boolean, update: (Conversation) -> Conversation): Boolean {
        if (conversations.value.none { it.id == id && changes(it) }) return false
        conversations.update { all -> all.map { if (it.id == id) update(it) else it } }
        return true
    }

    /**
     * A conversation of [model], or of [persona] on its model, with one done exchange: the user's
     * [title], then [ReplyBlocks] written by the same.
     */
    fun seed(
        title: String,
        model: ChatModel = ModelCatalogue.default,
        persona: Persona? = null,
        updatedAt: Instant = Now,
    ): Conversation {
        val conversation = Conversation(
            id = nextId++,
            title = title,
            model = persona?.model ?: model,
            persona = persona,
            createdAt = updatedAt,
            updatedAt = updatedAt,
        )
        conversations.update { it + conversation }
        val user = Message.User(id = nextId++, conversationId = conversation.id, text = title, createdAt = updatedAt)
        val reply = Message.Assistant(
            id = nextId++,
            conversationId = conversation.id,
            model = conversation.model,
            persona = persona,
            blocks = ReplyBlocks,
            status = ReplyStatus.DONE,
            latency = ReplyLatency,
            createdAt = updatedAt,
        )
        messages.update { it + (conversation.id to listOf(user, reply)) }
        return conversation
    }

    private fun play(conversationId: Long?, text: String, newChat: ModelChoice): Long? {
        if (text.isEmpty()) return null
        val conversation = if (conversationId == null) {
            Conversation(
                id = nextId++,
                title = text,
                model = newChat.persona?.model ?: newChat.model,
                persona = newChat.persona,
                createdAt = Now,
                updatedAt = Now,
            ).also { created -> conversations.update { it + created } }
        } else {
            conversations.value.firstOrNull { it.id == conversationId } ?: return null
        }
        val thread = messages.value[conversation.id].orEmpty()
        if ((thread.lastOrNull() as? Message.Assistant)?.status?.isInProgress == true) return null
        val user = Message.User(id = nextId++, conversationId = conversation.id, text = text, createdAt = Now)
        val replyId = nextId++
        fun reply(blocks: List<Block>, status: ReplyStatus, latency: Duration? = null) = Message.Assistant(
            id = replyId,
            conversationId = conversation.id,
            model = conversation.model,
            persona = conversation.persona,
            blocks = blocks,
            status = status,
            latency = latency,
            createdAt = Now,
        )
        messages.update { it + (conversation.id to thread + user + reply(emptyList(), ReplyStatus.THINKING)) }
        checkNotNull(scope) { "Pass a scope to play replies" }.launch {
            delay(ThinkingTime)
            for (count in 1..ReplyBlocks.size) {
                update(reply(ReplyBlocks.take(count), ReplyStatus.STREAMING))
                delay(TickTime)
            }
            update(reply(ReplyBlocks, ReplyStatus.DONE, ReplyLatency))
        }
        return conversation.id
    }

    private fun update(reply: Message.Assistant) = messages.update { all ->
        all + (reply.conversationId to all.getValue(reply.conversationId).map { if (it.id == reply.id) reply else it })
    }
}

/**
 * An in-memory [ModelRepository] that keeps the real one's contract: favorites in catalogue order,
 * the presets as personas, and every read a flow that emits only on change.
 */
internal class FakeModelRepository(
    defaultModel: ChatModel = ModelCatalogue.default,
    favorites: List<ChatModel> = ModelRepository.DEFAULT_FAVORITE_IDS.map(ModelCatalogue::get),
    personas: List<Persona> = PersonaPresets.personas,
) : ModelRepository {
    val defaultModel = MutableStateFlow(defaultModel)
    val favorites = MutableStateFlow(favorites)
    val personas = MutableStateFlow(personas)

    /** Every `setFavorite`, in order. */
    val favoriteWrites = mutableListOf<Pair<ChatModel, Boolean>>()

    override fun observeFavorites(): Flow<List<ChatModel>> = favorites

    override suspend fun setFavorite(model: ChatModel, favorite: Boolean) {
        favoriteWrites += model to favorite
        favorites.update { current ->
            val ids = current.map { it.id }.toSet().let { if (favorite) it + model.id else it - model.id }
            ModelCatalogue.models.filter { it.id in ids }
        }
    }

    override fun observePersonas(): Flow<List<Persona>> = personas

    override fun observeDefaultModel(): Flow<ChatModel> = defaultModel

    override suspend fun setDefaultModel(model: ChatModel) {
        defaultModel.value = model
    }
}
