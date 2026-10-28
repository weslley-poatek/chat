package br.com.weslleycampos.chat.feature.voice

import br.com.weslleycampos.chat.data.chat.ModelRepository
import br.com.weslleycampos.chat.data.chat.VoiceRepository
import br.com.weslleycampos.chat.data.chat.models.ChatModel
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.Persona
import br.com.weslleycampos.chat.data.chat.models.PersonaPresets
import br.com.weslleycampos.chat.data.chat.models.VoiceExchange
import br.com.weslleycampos.chat.data.chat.models.VoiceSession
import br.com.weslleycampos.chat.data.chat.models.VoiceTurn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest as runCoroutineTest

internal val Gemini: ChatModel = ModelCatalogue["gemini"]
internal val Gpt5: ChatModel = ModelCatalogue["gpt5"]

/** The code reviewer, on Claude Sonnet 4.5. */
internal val Reviewer: Persona = PersonaPresets.personas[0]

/** Four short canned exchanges, in turn, as the real repository rotates the design's lines. */
internal val Exchanges = listOf(
    VoiceExchange(transcript = "so the hook works", reply = "add a leading flag"),
    VoiceExchange(transcript = "what about unmount", reply = "clear the timer then"),
    VoiceExchange(transcript = "and a changing delay", reply = "restart the window"),
    VoiceExchange(transcript = "how do I test it", reply = "use fake timers"),
)

/** A conversation as the fake keeps it: what answers it and how many user messages it has. */
internal data class FakeVoiceConversation(val choice: ModelChoice, val userMessages: Int = 0, val answered: Int = 0)

/**
 * An in-memory [VoiceRepository] that keeps the real one's contract: a new session on the default
 * model plays the first exchange; a conversation's session follows its choice and moves to the next
 * exchange as soon as a transcript is saved; the first transcript creates the conversation on
 * `newSession` or the default; a reply answers a turn once; a pick sets the conversation's choice
 * and the default model.
 */
internal class FakeVoiceRepository(
    defaultModel: ChatModel = ModelCatalogue.default,
    conversations: Map<Long, FakeVoiceConversation> = emptyMap(),
) : VoiceRepository {
    val defaultModel = MutableStateFlow(defaultModel)
    val conversations = MutableStateFlow(conversations)

    /** Every `saveTranscript`, as (conversation id, new session), in order. */
    val transcripts = mutableListOf<Pair<Long?, ModelChoice?>>()

    /** Every turn `saveReply` answered, in order. */
    val replies = mutableListOf<VoiceTurn>()

    /** Every `choose`, as (conversation id, choice), in order. */
    val choices = mutableListOf<Pair<Long?, ModelChoice>>()

    /** A typed reply is on its way in every conversation: transcripts are refused (`null`). */
    var refuseTranscripts = false

    /** Every pick is refused (`false`), as one not in the catalogue would be. */
    var refuseChoices = false

    private var nextConversationId = (conversations.keys.maxOrNull() ?: 0L) + 1
    private var nextMessageId = 100L
    private val turns = mutableMapOf<Long, Long>()

    override fun observeSession(conversationId: Long?): Flow<VoiceSession?> = if (conversationId == null) {
        defaultModel.map { VoiceSession(conversationId = null, model = it, next = Exchanges[0]) }
    } else {
        conversations.map { all ->
            all[conversationId]?.let {
                VoiceSession(
                    conversationId = conversationId,
                    model = it.choice.model,
                    persona = it.choice.persona,
                    next = Exchanges[it.userMessages % Exchanges.size],
                )
            }
        }
    }.distinctUntilChanged()

    override suspend fun saveTranscript(conversationId: Long?, newSession: ModelChoice?): VoiceTurn? {
        transcripts += conversationId to newSession
        if (refuseTranscripts) return null
        val id = conversationId ?: nextConversationId++.also { id ->
            val choice = newSession?.let { ModelChoice(model = it.model, persona = it.persona) }
            conversations.update { it + (id to FakeVoiceConversation(choice ?: ModelChoice(defaultModel.value))) }
        }
        val conversation = conversations.value[id] ?: return null
        conversations.update { it + (id to conversation.copy(userMessages = conversation.userMessages + 1)) }
        val turn = VoiceTurn(
            conversationId = id,
            transcriptId = nextMessageId++,
            exchange = Exchanges[conversation.userMessages % Exchanges.size],
        )
        turns[turn.transcriptId] = id
        return turn
    }

    override suspend fun saveReply(turn: VoiceTurn): Boolean {
        val conversation = conversations.value[turn.conversationId] ?: return false
        if (turns.remove(turn.transcriptId) == null) return false
        replies += turn
        conversations.update { it + (turn.conversationId to conversation.copy(answered = conversation.answered + 1)) }
        return true
    }

    override suspend fun choose(conversationId: Long?, choice: ModelChoice): Boolean {
        choices += conversationId to choice
        if (refuseChoices) return false
        if (conversationId != null) {
            val conversation = conversations.value[conversationId] ?: return false
            conversations.update { it + (conversationId to conversation.copy(choice = choice)) }
        }
        defaultModel.value = choice.model
        return true
    }
}

internal class FakeModelRepository(
    favorites: List<ChatModel> = ModelRepository.DEFAULT_FAVORITE_IDS.map(ModelCatalogue::get),
    personas: List<Persona> = PersonaPresets.personas,
) : ModelRepository {
    val favorites = MutableStateFlow(favorites)
    val personas = MutableStateFlow(personas)
    val defaultModel = MutableStateFlow(ModelCatalogue.default)

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

/**
 * The view models a test started. Their phases loop forever on the test scheduler, and [runTest]
 * runs that scheduler until it is idle once the test is over, so each one is stopped first.
 */
internal class StartedViewModels {
    private val all = mutableListOf<VoiceViewModel>()

    fun add(viewModel: VoiceViewModel): VoiceViewModel = viewModel.also { all += it }

    fun runTest(body: suspend TestScope.() -> Unit): TestResult = runCoroutineTest {
        try {
            body()
        } finally {
            all.forEach { it.onStop() }
            all.clear()
        }
    }
}
