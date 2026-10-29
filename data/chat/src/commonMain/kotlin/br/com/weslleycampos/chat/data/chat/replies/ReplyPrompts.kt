package br.com.weslleycampos.chat.data.chat.replies

import br.com.weslleycampos.chat.core.database.entities.MessageEntity
import br.com.weslleycampos.chat.core.database.entities.MessageRole
import br.com.weslleycampos.chat.core.database.entities.PAIR_FIRST_SIDE
import br.com.weslleycampos.chat.core.database.entities.PAIR_SECOND_SIDE
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.Persona
import br.com.weslleycampos.chat.data.chat.models.VoiceLanguages
import br.com.weslleycampos.chat.data.chat.toBlock
import br.com.weslleycampos.chat.data.fuelix.FuelixMessage
import br.com.weslleycampos.chat.data.fuelix.FuelixRole

/**
 * What a reply's request sends Fuelix: the conversation so far as chat messages, after the system
 * prompt (a persona's, voice mode's).
 *
 * The thread is its rows in order. A user message is sent as typed; a reply as its blocks written
 * back as Markdown ([MarkdownBlocks.write]), leaving out one that showed nothing. Of a compare pair,
 * a model reads its own side, or the first side when neither is its own, so each side of a pair
 * goes on its own line of thought.
 */
internal object ReplyPrompts {
    /**
     * Voice mode's system prompt: the device says the reply aloud, so it is short plain speech in a
     * language of [languages] its voice can say. With one language, in that one, whatever language
     * the conversation so far is in; bilingual, in the language of the user's last message, code and
     * technical terms staying in English as developers say them.
     */
    fun voicePrompt(languages: VoiceLanguages): String {
        val language = if (languages.bilingual) {
            "The user speaks ${languages.all.joinToString(" and ") { it.promptName }}: answer in the " +
                "language of their last message, keeping code and technical terms in English"
        } else {
            "Answer in ${languages.primary.promptName}"
        }
        return "You are speaking aloud in a voice conversation. $language, in one to three short " +
            "sentences of plain spoken words: no Markdown, lists or code."
    }

    /**
     * The requests of reply rows [replyIds] of [thread], one or a pair's two: each from the model
     * its row was saved with ([ModelCatalogue.resolve]d, the default for none), with its persona's
     * prompt, one of [personas], as the system prompt. A row not in [thread] is left out.
     */
    fun requests(
        replyIds: List<Long>,
        thread: List<MessageEntity>,
        personas: Map<String, Persona>,
    ): List<ReplyRequest> = replyIds.mapNotNull { id ->
        val reply = thread.firstOrNull { it.id == id } ?: return@mapNotNull null
        request(
            reply = reply,
            model = ModelCatalogue.resolve(reply.modelId.orEmpty()).ifEmpty { ModelCatalogue.DEFAULT_MODEL_ID },
            thread = thread,
            system = reply.personaId?.let(personas::get)?.prompt,
        )
    }

    /** The request of reply row [reply], which [model] writes after the rows of [thread] before it. */
    fun request(reply: MessageEntity, model: String, thread: List<MessageEntity>, system: String?): ReplyRequest =
        ReplyRequest(
            replyId = reply.id,
            model = model,
            messages = messages(thread.takeWhile { it.id < reply.id && !it.isPairSideOf(reply) }, model, system),
        )

    /** [thread] as the messages [model] reads, [system] first when there is one. */
    fun messages(thread: List<MessageEntity>, model: String, system: String?): List<FuelixMessage> = buildList {
        system?.takeIf(String::isNotBlank)?.let { add(FuelixMessage(FuelixRole.SYSTEM, it)) }
        var index = 0
        while (index < thread.size) {
            val row = thread[index]
            val partner = thread.getOrNull(index + 1)
            val read = if (row.pairSide == PAIR_FIRST_SIDE && partner?.pairSide == PAIR_SECOND_SIDE) {
                index++
                if (ModelCatalogue.resolve(partner.modelId.orEmpty()) == model) partner else row
            } else {
                row
            }
            read.toMessage()?.let(::add)
            index++
        }
    }

    private fun MessageEntity.toMessage(): FuelixMessage? = when (role) {
        MessageRole.USER -> FuelixMessage(FuelixRole.USER, text)
        MessageRole.ASSISTANT -> MarkdownBlocks.write(blocks.map { it.toBlock() })
            .takeIf(String::isNotBlank)
            ?.let { FuelixMessage(FuelixRole.ASSISTANT, it) }
    }

    /** The first side of the pair [reply] is the second side of: it is answered beside it, not read. */
    private fun MessageEntity.isPairSideOf(reply: MessageEntity): Boolean =
        reply.pairSide == PAIR_SECOND_SIDE && pairSide == PAIR_FIRST_SIDE && id == reply.id - 1
}

/** A reply to stream: its row, the model that writes it, and what that model reads. */
internal class ReplyRequest(val replyId: Long, val model: String, val messages: List<FuelixMessage>)
