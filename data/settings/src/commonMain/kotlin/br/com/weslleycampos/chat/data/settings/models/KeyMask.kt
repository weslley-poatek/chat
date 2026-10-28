package br.com.weslleycampos.chat.data.settings.models

/**
 * What the "api keys" group shows of a stored key: the prototype's first 7 characters, "…" and the
 * last 4, as its `save` does, for every real key. A shorter key shows less, so the mask never shows
 * more than half of a key, nor a short key whole:
 *
 * | Key length | Shown | Example |
 * | --- | --- | --- |
 * | [FULL_MIN_LENGTH] (22) or more | first 7 + "…" + last 4 | `sk-ant-…4f2a` |
 * | [SUFFIX_MIN_LENGTH] (8) to 21 | "…" + last 4 | `…4f2a` |
 * | 1 to 7 | "…" | `…` |
 *
 * Lengths are counted as `String.length` does. Real keys are ASCII and at least 32 characters long,
 * so they all take the first form.
 */
internal object KeyMask {
    const val ELLIPSIS = "…"
    const val SHOWN_PREFIX = 7
    const val SHOWN_SUFFIX = 4

    /** The shortest key whose first 7 and last 4 are at most half of it. */
    const val FULL_MIN_LENGTH = 2 * (SHOWN_PREFIX + SHOWN_SUFFIX)

    /** The shortest key whose last 4 are at most half of it. */
    const val SUFFIX_MIN_LENGTH = 2 * SHOWN_SUFFIX

    fun of(key: String): String = when {
        key.length >= FULL_MIN_LENGTH -> key.take(SHOWN_PREFIX) + ELLIPSIS + key.takeLast(SHOWN_SUFFIX)
        key.length >= SUFFIX_MIN_LENGTH -> ELLIPSIS + key.takeLast(SHOWN_SUFFIX)
        else -> ELLIPSIS
    }
}
