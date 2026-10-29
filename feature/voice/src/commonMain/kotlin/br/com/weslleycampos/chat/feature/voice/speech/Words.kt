package br.com.weslleycampos.chat.feature.voice.speech

/**
 * Where each of [text]'s words, split on spaces as the caption splits them, starts: 0, then the
 * index after each space. A synthesizer's range tells which word it is saying from these.
 */
internal fun wordStarts(text: String): List<Int> =
    listOf(0) + text.indices.filter { text[it] == ' ' }.map { it + 1 }
