package br.com.weslleycampos.chat.core.database.entities

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * One message of a conversation, table `messages`, added in version 2. Deleting the conversation
 * deletes its messages.
 *
 * A conversation's messages are read in [id] order: SQLite assigns ids in insertion order and never
 * reuses one, so the order holds even when two messages share a [createdAt].
 *
 * @property id Assigned by SQLite on insert: pass `0`.
 * @property role Who wrote it; stored as `user` or `assistant`.
 * @property text What the user typed. Empty for an assistant message.
 * @property blocks The reply's paragraphs, code and lists, stored as one JSON array. Empty for a
 *   user message, and for a reply that has not revealed anything yet.
 * @property modelId The catalogue id of the model that wrote a reply. `null` for a user message.
 * @property status Where a reply is in its stream. A user message is always [MessageStatus.DONE].
 * @property latencyMillis How long a finished reply streamed for. `null` until it is done.
 * @property createdAt Epoch milliseconds.
 * @property personaId Version 3: the id of the [PersonaEntity] a reply was written as, or `null` for a
 *   user message and a reply from the model on its own. Rows from version 2 read `null`.
 * @property pairSide Version 6: for one side of a compare pair, which one: [PAIR_FIRST_SIDE], the
 *   conversation's model, or [PAIR_SECOND_SIDE], the model compared with it. A pair is two assistant
 *   rows written one right after the other in one transaction, first side first, each with its own
 *   model, blocks, status and latency; it answers one user message as one reply would. `null` for a
 *   user message and a single reply. Rows from version 5 read `null`.
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conversationId")],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: MessageRole,
    val text: String,
    val blocks: List<StoredBlock>,
    val modelId: String?,
    val status: MessageStatus,
    val latencyMillis: Long?,
    val createdAt: Long,
    val personaId: String? = null,
    val pairSide: Int? = null,
)

/** [MessageEntity.pairSide] of a compare pair's first side: the conversation's own model, the left card. */
const val PAIR_FIRST_SIDE = 0

/** [MessageEntity.pairSide] of a compare pair's second side: the model compared with it, the right card. */
const val PAIR_SECOND_SIDE = 1

/** The author of a message. [value] is what the `role` column holds. */
enum class MessageRole(val value: String) {
    USER("user"),
    ASSISTANT("assistant"),
}

/**
 * Where a message is in its life. [value] is what the `status` column holds, and the DAO's queries
 * name those values, so they never change.
 */
enum class MessageStatus(val value: String) {
    /** A reply that has been asked for and shows nothing yet. */
    THINKING("thinking"),

    /** A reply that is revealing its blocks. */
    STREAMING("streaming"),

    /** A user message, or a reply that revealed everything. */
    DONE("done"),

    /** A reply that stopped before it finished, because the app was closed or its stream failed. */
    INTERRUPTED("interrupted"),
}

/**
 * The part of a reply its stream rewrites on every tick, for `@Update(entity = MessageEntity::class)`.
 */
data class ReplyProgress(
    val id: Long,
    val blocks: List<StoredBlock>,
    val status: MessageStatus,
    val latencyMillis: Long?,
)
