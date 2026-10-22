package br.com.weslleycampos.chat.data.chat.models

/** The eight models of the design prototype's `MODELS`, in its order. Read-only. */
object ModelCatalogue {
    /**
     * The default model for new chats until the user sets another (`ModelRepository.setDefaultModel`),
     * and what a model id the catalogue no longer has reads as.
     */
    const val DEFAULT_MODEL_ID = "sonnet"

    val models: List<ChatModel> = listOf(
        ChatModel(
            id = "sonnet",
            name = "Claude Sonnet 4.5",
            shortName = "sonnet-4.5",
            provider = "Anthropic",
            contextWindow = "200k",
            cost = "$$",
            hue = 35,
        ),
        ChatModel(
            id = "opus",
            name = "Claude Opus 4.1",
            shortName = "opus-4.1",
            provider = "Anthropic",
            contextWindow = "200k",
            cost = "$$$",
            hue = 35,
        ),
        ChatModel(
            id = "gpt5",
            name = "GPT-5",
            shortName = "gpt-5",
            provider = "OpenAI",
            contextWindow = "400k",
            cost = "$$",
            hue = 150,
        ),
        ChatModel(
            id = "o3",
            name = "o3",
            shortName = "o3",
            provider = "OpenAI",
            contextWindow = "200k",
            cost = "$$$",
            hue = 150,
        ),
        ChatModel(
            id = "gemini",
            name = "Gemini 2.5 Pro",
            shortName = "gemini-2.5-pro",
            provider = "Google",
            contextWindow = "1M",
            cost = "$$",
            hue = 255,
        ),
        ChatModel(
            id = "llama",
            name = "Llama 4 Maverick",
            shortName = "llama-4",
            provider = "Meta",
            contextWindow = "1M",
            cost = "$",
            hue = 290,
        ),
        ChatModel(
            id = "deepseek",
            name = "DeepSeek V3.1",
            shortName = "deepseek-v3.1",
            provider = "DeepSeek",
            contextWindow = "128k",
            cost = "$",
            hue = 220,
        ),
        ChatModel(
            id = "mistral",
            name = "Mistral Large 3",
            shortName = "mistral-large",
            provider = "Mistral",
            contextWindow = "128k",
            cost = "$",
            hue = 80,
        ),
    )

    /** Claude Sonnet 4.5, the prototype's initial `modelId`. */
    val default: ChatModel = models.first { it.id == DEFAULT_MODEL_ID }

    /** The model with this [id], or `null` when the catalogue has none. */
    fun find(id: String): ChatModel? = models.firstOrNull { it.id == id }

    /** The model with this [id], or [default] when the catalogue has none, as the prototype's `model(id)`. */
    operator fun get(id: String): ChatModel = find(id) ?: default
}
