package br.com.weslleycampos.chat.core.database

import br.com.weslleycampos.chat.core.database.entities.StoredBlock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

// Kept out of `ChatTypeConverters`: Room's processor reads that class, and KSP cannot see the
// serializers the serialization plugin generates.

private const val PARAGRAPH = "p"
private const val CODE = "code"
private const val LIST = "list"

private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

private val blockListSerializer: KSerializer<List<BlockJson>> = ListSerializer(BlockJson.serializer())

/** The `messages.blocks` column for [blocks]: a JSON array in the design prototype's block shape. */
internal fun encodeBlocks(blocks: List<StoredBlock>): String =
    json.encodeToString(blockListSerializer, blocks.map(StoredBlock::toJson))

/** The blocks in a `messages.blocks` value. A block whose `type` this version does not know is left out. */
internal fun decodeBlocks(value: String): List<StoredBlock> =
    json.decodeFromString(blockListSerializer, value).mapNotNull(BlockJson::toStoredBlock)

/** One element of the array: the prototype's `{type, lang?, text?, items?}`. */
@Serializable
private data class BlockJson(
    val type: String,
    val lang: String? = null,
    val text: String? = null,
    val items: List<String>? = null,
)

private fun StoredBlock.toJson(): BlockJson = when (this) {
    is StoredBlock.Paragraph -> BlockJson(type = PARAGRAPH, text = text)
    is StoredBlock.Code -> BlockJson(type = CODE, lang = language, text = text)
    is StoredBlock.NumberedList -> BlockJson(type = LIST, items = items)
}

private fun BlockJson.toStoredBlock(): StoredBlock? = when (type) {
    PARAGRAPH -> StoredBlock.Paragraph(text = text.orEmpty())
    CODE -> StoredBlock.Code(language = lang.orEmpty(), text = text.orEmpty())
    LIST -> StoredBlock.NumberedList(items = items.orEmpty())
    else -> null
}
