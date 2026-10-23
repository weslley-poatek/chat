package br.com.weslleycampos.chat.feature.chat

import br.com.weslleycampos.chat.core.ui.theme.ChatPalette
import br.com.weslleycampos.chat.data.chat.models.ChatModel
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.Persona
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

// The chat's text formats, as the prototype writes them: pure, so the tests pin each one.

/** The prototype's `draftRows` cap: the composer grows to six lines, then scrolls. */
const val MaxDraftRows = 6

private const val MillisPerTenth = 100
private const val TenthsPerSecond = 10

/** The meta row's separator, the prototype's `" · "`. */
internal const val MetaSeparator = " · "

private val MonthAbbreviations = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/** The prototype's `draftRows`: one row per line of [draft], from one to [MaxDraftRows]. */
fun draftRows(draft: String): Int = draft.split('\n').size.coerceIn(1, MaxDraftRows)

/** The prototype's `now()`: local `HH:MM` on a 24-hour clock, as the message meta shows it. */
fun formatClockTime(instant: Instant, timeZone: TimeZone): String {
    val time = instant.toLocalDateTime(timeZone)
    return "${time.hour.twoDigits()}:${time.minute.twoDigits()}"
}

/**
 * A "recent" row's time, as the prototype's history items: `HH:MM` for a conversation updated today
 * in [timeZone], else the month and day, "Sep 13".
 */
fun formatRecentTime(instant: Instant, now: Instant, timeZone: TimeZone): String {
    val time = instant.toLocalDateTime(timeZone)
    if (time.date == now.toLocalDateTime(timeZone).date) return formatClockTime(instant, timeZone)
    return "${MonthAbbreviations[time.month.ordinal]} ${time.date.day}"
}

/**
 * The prototype's `lat`, `(ms / 1000).toFixed(1) + 's'`: tenths of a second, a half rounding up,
 * "0.8s", "5.2s".
 */
fun formatLatency(latency: Duration): String {
    val tenths = (latency.inWholeMilliseconds + MillisPerTenth / 2) / MillisPerTenth
    return "${tenths / TenthsPerSecond}.${tenths % TenthsPerSecond}s"
}

/**
 * An assistant message's meta, the prototype's `` `${short}${persona} · ${time} · ${lat}` ``: the
 * persona the reply was written as, lowercased, after the short name; the latency once the reply is
 * done, or [interruptedLabel] for one that stopped on the way. "sonnet-4.5 · code reviewer · 14:02 · 0.8s".
 */
fun assistantMeta(
    modelShortName: String,
    personaName: String?,
    time: String,
    latency: String?,
    interruptedLabel: String?,
): String = listOfNotNull(modelShortName, personaName?.lowercase(), time, latency, interruptedLabel)
    .joinToString(MetaSeparator)

/**
 * The model chip, the prototype's `chipLabel`: "sonnet-4.5 ⇄ gpt-5" while two models are compared
 * ([ModelChoice.comparing]), "Code reviewer · sonnet-4.5" while a persona answers, else the model's
 * full name, "Claude Sonnet 4.5". With compare on and no second model yet it is the model's name.
 */
fun chipLabel(choice: ModelChoice): String {
    val persona = choice.persona
    val comparing = choice.comparing
    return when {
        comparing != null -> compareLabel(choice.model, comparing)
        persona != null -> "${persona.name}$MetaSeparator${choice.model.shortName}"
        else -> choice.model.name
    }
}

/**
 * The empty state's meta line, the prototype's `modelMetaLine`: "Anthropic · 200k context · $$",
 * ending " · persona: Code reviewer" while a persona answers.
 */
fun modelMetaLine(model: ChatModel, persona: Persona?): String = listOfNotNull(
    model.provider,
    "${model.contextWindow} context",
    model.cost,
    persona?.let { "persona: ${it.name}" },
).joinToString(MetaSeparator)

/**
 * The parts of an empty-state tip: the prototype draws its leading words, written `*so*` in the
 * strings, in `--fg2` and the rest in `--fg3`. Each pair is a run and whether it is emphasised.
 */
fun tipRuns(tip: String): List<Pair<String, Boolean>> = tip.split('*')
    .mapIndexed { index, run -> run to (index % 2 == 1) }
    .filter { (run, _) -> run.isNotEmpty() }

/**
 * The accent of a model: the `:core:ui` palette named after its id (`sonnet` → `SONNET`), or the
 * default for an id the palettes do not know.
 */
val ChatModel.palette: ChatPalette
    get() = ChatPalette.entries.firstOrNull { it.name.equals(id, ignoreCase = true) } ?: ChatPalette.Default

/** The device's clock and time zone, which the message meta and the "recent" rows are written in. */
class ChatTimes(
    private val clock: Clock,
    private val timeZone: () -> TimeZone,
) {
    fun messageTime(instant: Instant): String = formatClockTime(instant, timeZone())

    fun recentTime(instant: Instant): String = formatRecentTime(instant, clock.now(), timeZone())
}

private fun Int.twoDigits(): String = toString().padStart(length = 2, padChar = '0')
