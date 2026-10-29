package br.com.weslleycampos.chat.data.chat.models

/**
 * What the app knows about Fuelix's models without asking it: a [ChatModel] for any id ([of]), the
 * ids stored before Fuelix ([legacyIds]), and the [models] listed until the key's own list is read
 * (`ModelRepository.observeModels`). Read-only.
 */
object ModelCatalogue {
    /**
     * The default model for new chats until the user sets another (`ModelRepository.setDefaultModel`),
     * and what a blank model id reads as.
     */
    const val DEFAULT_MODEL_ID = "claude-sonnet-4-5-20250929"

    /**
     * The design prototype's eight models on their Fuelix ids, with its names, context windows and
     * costs: what the picker lists until Fuelix's own list is read. Fuelix has no DeepSeek V3.1, so
     * its slot is DeepSeek V4.1 Flash.
     */
    val models: List<ChatModel> = listOf(
        ChatModel(
            id = DEFAULT_MODEL_ID,
            name = "Claude Sonnet 4.5",
            shortName = "sonnet-4.5",
            vendor = ModelVendor.ANTHROPIC,
            contextWindow = "200k",
            cost = "$$",
        ),
        ChatModel(
            id = "claude-opus-4-1-20250805",
            name = "Claude Opus 4.1",
            shortName = "opus-4.1",
            vendor = ModelVendor.ANTHROPIC,
            contextWindow = "200k",
            cost = "$$$",
        ),
        ChatModel(
            id = "gpt-5",
            name = "GPT-5",
            shortName = "gpt-5",
            vendor = ModelVendor.OPENAI,
            contextWindow = "400k",
            cost = "$$",
        ),
        ChatModel(
            id = "o3",
            name = "o3",
            shortName = "o3",
            vendor = ModelVendor.OPENAI,
            contextWindow = "200k",
            cost = "$$$"
        ),
        ChatModel(
            id = "gemini-2.5-pro",
            name = "Gemini 2.5 Pro",
            shortName = "gemini-2.5-pro",
            vendor = ModelVendor.GOOGLE,
            contextWindow = "1M",
            cost = "$$",
        ),
        ChatModel(
            id = "llama-4-maverick-17b-128e",
            name = "Llama 4 Maverick",
            shortName = "llama-4",
            vendor = ModelVendor.META,
            contextWindow = "1M",
            cost = "$",
        ),
        ChatModel(
            id = "deepseek-v4.1-flash",
            name = "DeepSeek V4.1 Flash",
            shortName = "deepseek-v4.1-flash",
            vendor = ModelVendor.DEEPSEEK,
            cost = "$",
        ),
        ChatModel(
            id = "mistral-large",
            name = "Mistral Large",
            shortName = "mistral-large",
            vendor = ModelVendor.MISTRAL,
            contextWindow = "128k",
            cost = "$",
        ),
    )

    /**
     * The ids conversations, replies, personas, favorites and the default model were stored with
     * before Fuelix, and the Fuelix model each one reads as now. Nothing is rewritten: every read
     * goes through [resolve].
     */
    val legacyIds: Map<String, String> = mapOf(
        "sonnet" to DEFAULT_MODEL_ID,
        "opus" to "claude-opus-4-1-20250805",
        "gpt5" to "gpt-5",
        "gemini" to "gemini-2.5-pro",
        "llama" to "llama-4-maverick-17b-128e",
        "deepseek" to "deepseek-v4.1-flash",
        "mistral" to "mistral-large",
    )

    private val knownById: Map<String, ChatModel> = models.associateBy(ChatModel::id)

    /** Claude Sonnet 4.5, the prototype's initial `modelId`. */
    val default: ChatModel = knownById.getValue(DEFAULT_MODEL_ID)

    /** The Fuelix id [id] stands for: the model a legacy id became, else [id] itself. */
    fun resolve(id: String): String = legacyIds[id] ?: id

    /** The model [id] names, a legacy id resolved, or `null` for a blank one. Any other id is a model. */
    fun find(id: String): ChatModel? = id.trim().takeIf(String::isNotEmpty)?.let { of(resolve(it)) }

    /** The model [id] names, or [default] for a blank one, as the prototype's `model(id)`. */
    operator fun get(id: String): ChatModel = find(id) ?: default

    /**
     * [id] as a model: one of [models] with its design values, or any other with its name and short
     * name read from the id ([ModelNames]) and its vendor from its prefix ([ModelVendor.of]).
     */
    fun of(id: String): ChatModel = knownById[id] ?: ChatModel(
        id = id,
        name = ModelNames.name(id),
        shortName = ModelNames.shortName(id),
        vendor = ModelVendor.of(id),
    )

    /**
     * Whether [id] is a model that chats, rather than one that embeds, draws, transcribes, speaks or
     * moderates, or one of Fuelix's batch-only aliases (`…-anthropic`): the picker lists only these.
     */
    fun isChatModel(id: String): Boolean {
        val lower = id.lowercase()
        return NON_CHAT_MARKERS.none(lower::contains) && !lower.endsWith(BATCH_SUFFIX)
    }

    private val NON_CHAT_MARKERS = listOf(
        "embed", "whisper", "tts", "image", "dall-e", "guard", "moderation", "voxtral", "transcribe", "realtime",
        "audio", "rerank",
    )

    private const val BATCH_SUFFIX = "-anthropic"
}
