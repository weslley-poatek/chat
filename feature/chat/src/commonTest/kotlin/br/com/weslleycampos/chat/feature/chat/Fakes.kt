package br.com.weslleycampos.chat.feature.chat

import br.com.weslleycampos.chat.data.chat.ChatRepository
import br.com.weslleycampos.chat.data.chat.models.Block
import br.com.weslleycampos.chat.data.chat.models.ChatModel
import br.com.weslleycampos.chat.data.chat.models.Conversation
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ReplyStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    var answer: (suspend (conversationId: Long?, text: String) -> Long?)? = null
    private var nextId = 1L

    override fun observeConversation(id: Long): Flow<Conversation?> =
        conversations.map { all -> all.firstOrNull { it.id == id } }.distinctUntilChanged()

    override fun observeMessages(conversationId: Long): Flow<List<Message>> =
        messages.map { it[conversationId].orEmpty() }.distinctUntilChanged()

    override fun observeRecentConversations(): Flow<List<Conversation>> = conversations
        .map { all -> all.sortedByDescending { it.updatedAt }.take(ChatRepository.RECENT_CONVERSATIONS) }
        .distinctUntilChanged()

    override suspend fun send(conversationId: Long?, text: String): Long? {
        sends += conversationId to text
        answer?.let { return it(conversationId, text) }
        return play(conversationId, text.trim())
    }

    /** A conversation of [model] with one done exchange: the user's [title], then [ReplyBlocks]. */
    fun seed(title: String, model: ChatModel = ModelCatalogue.default, updatedAt: Instant = Now): Conversation {
        val conversation = Conversation(
            id = nextId++,
            title = title,
            model = model,
            createdAt = updatedAt,
            updatedAt = updatedAt,
        )
        conversations.update { it + conversation }
        val user = Message.User(id = nextId++, conversationId = conversation.id, text = title, createdAt = updatedAt)
        val reply = Message.Assistant(
            id = nextId++,
            conversationId = conversation.id,
            model = model,
            blocks = ReplyBlocks,
            status = ReplyStatus.DONE,
            latency = ReplyLatency,
            createdAt = updatedAt,
        )
        messages.update { it + (conversation.id to listOf(user, reply)) }
        return conversation
    }

    private fun play(conversationId: Long?, text: String): Long? {
        if (text.isEmpty()) return null
        val conversation = if (conversationId == null) {
            Conversation(id = nextId++, title = text, model = ModelCatalogue.default, createdAt = Now, updatedAt = Now)
                .also { created -> conversations.update { it + created } }
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
