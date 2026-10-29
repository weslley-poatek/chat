package br.com.weslleycampos.chat.feature.voice

import br.com.weslleycampos.chat.data.chat.ModelRepository
import br.com.weslleycampos.chat.data.chat.VoiceRepository
import br.com.weslleycampos.chat.data.chat.models.ChatModel
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.Persona
import br.com.weslleycampos.chat.data.chat.models.PersonaPresets
import br.com.weslleycampos.chat.data.chat.models.ReplyFailure
import br.com.weslleycampos.chat.data.chat.models.VoiceAnswer
import br.com.weslleycampos.chat.data.chat.models.VoiceExchange
import br.com.weslleycampos.chat.data.chat.models.VoiceSession
import br.com.weslleycampos.chat.data.chat.models.VoiceTurn
import br.com.weslleycampos.chat.feature.voice.speech.Heard
import br.com.weslleycampos.chat.feature.voice.speech.SpeechException
import br.com.weslleycampos.chat.feature.voice.speech.SpeechInput
import br.com.weslleycampos.chat.feature.voice.speech.SpeechOutput
import br.com.weslleycampos.chat.feature.voice.speech.SpeechProblem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.test.runTest as runCoroutineTest

internal val Gemini: ChatModel = ModelCatalogue["gemini-2.5-pro"]
internal val Gpt5: ChatModel = ModelCatalogue["gpt-5"]

/** The code reviewer, on Claude Sonnet 4.5. */
internal val Reviewer: Persona = PersonaPresets.personas[0]

/** What the fake's model says back, in turn in each conversation: four words, four, three and three. */
internal val Replies = listOf(
    "add a leading flag",
    "clear the timer then",
    "restart the window",
    "use fake timers",
)

/**
 * How long the fake's Fuelix takes to reply, the prototype's 2400 ms of thinking: thinking lasts
 * this long unless a test says otherwise.
 */
internal val AnswerTime: Duration = 2400.milliseconds

/** How long each word takes to say: the fake user's, as the fake device hears it, and the fake device's own. */
internal val SpokenWord: Duration = 300.milliseconds

/** How long the fake device takes to say [text]: [SpokenWord] a word. */
internal fun spokenTime(text: String): Duration = SpokenWord * text.split(' ').size

/** A transcript `saveTranscript` was asked to save: where, what, and on what choice for a new session. */
internal data class SavedTranscript(val conversationId: Long?, val text: String, val newSession: ModelChoice? = null)

/** A conversation as the fake keeps it: what answers it and how many replies it has. */
internal data class FakeVoiceConversation(val choice: ModelChoice, val answered: Int = 0)

/**
 * An in-memory [VoiceRepository] that keeps the real one's contract: a new session is on the default
 * model; a conversation's session follows its choice; the first transcript creates the conversation
 * on `newSession` or the default; a reply answers a turn once, [Replies] in turn, and anything else
 * is [VoiceAnswer.Dropped]; a pick sets the conversation's choice and the default model.
 *
 * [answerTime], [answerFailure] and [answerScript] stand in for Fuelix: how long a reply takes, why
 * it gives none, or any answer at all.
 */
internal class FakeVoiceRepository(
    defaultModel: ChatModel = ModelCatalogue.default,
    conversations: Map<Long, FakeVoiceConversation> = emptyMap(),
) : VoiceRepository {
    val defaultModel = MutableStateFlow(defaultModel)
    val conversations = MutableStateFlow(conversations)

    /** Every `saveTranscript`, in order. */
    val transcripts = mutableListOf<SavedTranscript>()

    /** Every turn `answer` saved a reply to, in order. */
    val replies = mutableListOf<VoiceTurn>()

    /** Every turn `answer` was asked for, whatever it answered, in order. */
    val asked = mutableListOf<VoiceTurn>()

    /** Every `choose`, as (conversation id, choice), in order. */
    val choices = mutableListOf<Pair<Long?, ModelChoice>>()

    /** A typed reply is on its way in every conversation: transcripts are refused (`null`). */
    var refuseTranscripts = false

    /** Every pick is refused (`false`), as one not in the catalogue would be. */
    var refuseChoices = false

    /** How long Fuelix takes to reply, or to fail, on the test clock. */
    var answerTime: Duration = AnswerTime

    /** When set, Fuelix gives no reply, for this reason, once [answerTime] is up. */
    var answerFailure: ReplyFailure? = null

    /** When set, it answers every turn instead, saving nothing: a test's own Fuelix. */
    var answerScript: (suspend (VoiceTurn) -> VoiceAnswer)? = null

    private var nextConversationId = (conversations.keys.maxOrNull() ?: 0L) + 1
    private var nextMessageId = 100L
    private val turns = mutableMapOf<Long, Long>()

    override fun observeSession(conversationId: Long?): Flow<VoiceSession?> = if (conversationId == null) {
        defaultModel.map { VoiceSession(conversationId = null, model = it) }
    } else {
        conversations.map { all ->
            all[conversationId]?.let {
                VoiceSession(conversationId = conversationId, model = it.choice.model, persona = it.choice.persona)
            }
        }
    }.distinctUntilChanged()

    override suspend fun saveTranscript(
        conversationId: Long?,
        transcript: String,
        newSession: ModelChoice?,
    ): VoiceTurn? {
        transcripts += SavedTranscript(conversationId = conversationId, text = transcript, newSession = newSession)
        if (refuseTranscripts) return null
        val id = conversationId ?: nextConversationId++.also { id ->
            val choice = newSession?.let { ModelChoice(model = it.model, persona = it.persona) }
            conversations.update { it + (id to FakeVoiceConversation(choice ?: ModelChoice(defaultModel.value))) }
        }
        if (id !in conversations.value) return null
        val turn = VoiceTurn(
            conversationId = id,
            transcriptId = nextMessageId++,
            exchange = VoiceExchange(transcript = transcript),
        )
        turns[turn.transcriptId] = id
        return turn
    }

    override suspend fun answer(turn: VoiceTurn): VoiceAnswer {
        asked += turn
        answerScript?.let { return it(turn) }
        if (turn.transcriptId !in turns) return VoiceAnswer.Dropped
        delay(answerTime)
        val failure = answerFailure
        val conversation = conversations.value[turn.conversationId]
        return when {
            failure != null -> VoiceAnswer.Failed(failure)
            conversation == null || turns.remove(turn.transcriptId) == null -> VoiceAnswer.Dropped
            else -> {
                replies += turn
                conversations.update {
                    it + (turn.conversationId to conversation.copy(answered = conversation.answered + 1))
                }
                VoiceAnswer.Spoken(Replies[conversation.answered % Replies.size])
            }
        }
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

/**
 * A [SpeechInput] that plays the listens a test queues, one per `listen()`, in turn: [says],
 * [trailsOff], [hearsNothing], [fails] or any [queue]d script. With none queued, a listen hears
 * nothing and waits, as a device does while the user is quiet. It records how often the microphone
 * went on, whether it is on, and how often a listen was cut short.
 */
internal class FakeSpeechInput : SpeechInput {
    private val queued = ArrayDeque<suspend FlowCollector<Heard>.() -> Unit>()

    /** How many times the microphone went on. */
    var listens = 0
        private set

    /** How many listens were cancelled while on: by a tap, silence, mute, or the player stopping. */
    var cancellations = 0
        private set

    /** The microphone is on now. */
    var listening = false
        private set

    override fun listen(): Flow<Heard> = flow {
        val script = queued.removeFirstOrNull()
        listens++
        listening = true
        try {
            if (script == null) awaitCancellation() else script()
        } catch (e: CancellationException) {
            cancellations++
            throw e
        } finally {
            listening = false
        }
    }

    /** Queues a listen that runs [script]: what it emits is heard; what it throws, thrown. */
    fun queue(script: suspend FlowCollector<Heard>.() -> Unit) {
        queued += script
    }

    /** The user says [text], a word every [SpokenWord], and the device says they stopped with its last word. */
    fun says(text: String) = queue {
        speak(text)
        emit(Heard.Final(text))
    }

    /** The user says [text], a word every [SpokenWord], then nothing, and the device never says they stopped. */
    fun trailsOff(text: String) = queue {
        speak(text)
        awaitCancellation()
    }

    /** The device hears nothing, and says so after [after]. */
    fun hearsNothing(after: Duration = SpokenWord) = queue {
        delay(after)
        emit(Heard.Final(""))
    }

    /** The device cannot listen, for [problem]. */
    fun fails(problem: SpeechProblem) = queue { throw SpeechException(problem) }
}

/** Emits [text] as it is said: one more word every [SpokenWord], the first one [SpokenWord] in. */
internal suspend fun FlowCollector<Heard>.speak(text: String) {
    val words = text.split(' ')
    for (count in 1..words.size) {
        delay(SpokenWord)
        emit(Heard.Partial(words.take(count).joinToString(" ")))
    }
}

/**
 * A [SpeechOutput] that says a text a word every [SpokenWord]: as each word starts, how many have,
 * then all of them once the last one has been said, and completes ([spokenTime]). With [failure] it
 * cannot speak. It records what it was asked to say, whether it is speaking, and how often it was
 * stopped on the way.
 */
internal class FakeSpeechOutput : SpeechOutput {
    /** Every text it was asked to say, in order. */
    val texts = mutableListOf<String>()

    /** How many times speaking was stopped before its end. */
    var cancellations = 0
        private set

    /** It is speaking now. */
    var speaking = false
        private set

    /** When set, `speak` throws a [SpeechException] for it. */
    var failure: SpeechProblem? = null

    override fun speak(text: String): Flow<Int> = flow {
        texts += text
        failure?.let { throw SpeechException(it) }
        val words = text.split(' ').size
        speaking = true
        try {
            for (started in 1..words) {
                emit(started)
                delay(SpokenWord)
            }
            emit(words)
        } catch (e: CancellationException) {
            cancellations++
            throw e
        } finally {
            speaking = false
        }
    }
}

/**
 * An in-memory [ModelRepository] that keeps the real one's contract: the catalogue's models until a
 * refresh reads [listed], favorites in the models' order, and the presets as personas.
 */
internal class FakeModelRepository(
    models: List<ChatModel> = ModelCatalogue.models,
    favorites: List<ChatModel> = ModelRepository.DEFAULT_FAVORITE_IDS.map(ModelCatalogue::get),
    personas: List<Persona> = PersonaPresets.personas,
) : ModelRepository {
    val models = MutableStateFlow(models)
    val favorites = MutableStateFlow(favorites)
    val personas = MutableStateFlow(personas)
    val defaultModel = MutableStateFlow(ModelCatalogue.default)

    /** How many times `refreshModels` was called. */
    var refreshes = 0
        private set

    /** What Fuelix lists on the next refreshes; `null` while it lists nothing, so the list stays. */
    var listed: List<ChatModel>? = null

    /** When set, `refreshModels` throws it. */
    var refreshFailure: Exception? = null

    /** Every `setFavorite`, in order. */
    val favoriteWrites = mutableListOf<Pair<ChatModel, Boolean>>()

    override fun observeModels(): Flow<List<ChatModel>> = models

    override suspend fun refreshModels(): Boolean {
        refreshes++
        refreshFailure?.let { throw it }
        models.value = listed ?: return false
        return true
    }

    override fun observeFavorites(): Flow<List<ChatModel>> = favorites

    override suspend fun setFavorite(model: ChatModel, favorite: Boolean) {
        favoriteWrites += model to favorite
        favorites.update { current ->
            val ids = current.map { it.id }.toSet().let { if (favorite) it + model.id else it - model.id }
            // The listed ones in the list's order, then any no longer listed.
            (models.value + current + model).distinctBy { it.id }.filter { it.id in ids }
        }
    }

    override fun observePersonas(): Flow<List<Persona>> = personas

    override fun observeDefaultModel(): Flow<ChatModel> = defaultModel

    override suspend fun setDefaultModel(model: ChatModel) {
        defaultModel.value = model
    }
}

/**
 * The view models a test started. A listen with nothing queued waits forever on the test scheduler,
 * and [runTest] runs that scheduler until it is idle once the test is over, so each one is stopped
 * first.
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
