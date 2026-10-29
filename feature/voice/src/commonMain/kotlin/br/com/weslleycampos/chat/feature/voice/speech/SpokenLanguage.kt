package br.com.weslleycampos.chat.feature.voice.speech

import br.com.weslleycampos.chat.data.chat.models.VoiceLanguage
import br.com.weslleycampos.chat.data.chat.models.VoiceLanguages

/**
 * The language whose voice says [text]: the one language, or while bilingual the one [text] is
 * written in ([writtenIn]), since the model answers in the language the user spoke.
 */
internal fun VoiceLanguages.voiceFor(text: String): VoiceLanguage = if (bilingual) writtenIn(text, all) else primary

/**
 * Which of [among] [text] is written in, by how many of each language's common words it has, and for
 * Portuguese its accented words. Code and technical terms, English in both, count for neither. The
 * first of [among] when nothing tells them apart.
 */
internal fun writtenIn(text: String, among: List<VoiceLanguage>): VoiceLanguage {
    val words = text.lowercase().split(NotAWord).filter(String::isNotEmpty)
    return among.maxBy { language -> words.count { it in CommonWords[language].orEmpty() || language.accents(it) } }
}

/** What separates words: anything but letters, Portuguese's accented ones included, and apostrophes. */
private val NotAWord = Regex("[^a-zà-ÿ']+")

/** Whether [word] has an accent only Portuguese, of the languages, writes. */
private fun VoiceLanguage.accents(word: String): Boolean =
    this == VoiceLanguage.PORTUGUESE_BRAZIL && word.any { it in PortugueseAccents }

private const val PortugueseAccents = "ãõçáéíóúâêôà"

/**
 * Each language's most common words that the other does not write, so "a", "do", "no", "as" and
 * "me", in both, are left out.
 */
private val CommonWords: Map<VoiceLanguage, Set<String>> = mapOf(
    VoiceLanguage.PORTUGUESE_BRAZIL to setOf(
        "o", "os", "um", "uma", "uns", "umas", "de", "da", "dos", "das", "em", "na", "nos", "nas", "que",
        "e", "é", "não", "para", "pra", "com", "por", "pelo", "pela", "se", "mas", "como", "mais", "você",
        "isso", "isto", "esse", "essa", "este", "esta", "ele", "ela", "eu", "tem", "está", "são", "ao",
        "ou", "também", "quando", "porque", "já", "só", "muito", "seu", "sua", "pode", "fazer", "vai",
        "bem", "então", "aqui", "sim", "ser", "foi", "há", "depois", "antes", "ainda", "cada", "onde",
        "qual", "sobre", "seja", "precisa", "fica", "faz", "melhor",
    ),
    VoiceLanguage.ENGLISH_US to setOf(
        "the", "is", "are", "was", "were", "to", "of", "and", "in", "on", "for", "with", "that", "this",
        "it", "its", "it's", "you", "your", "i", "be", "not", "can", "does", "if", "or", "at", "by",
        "from", "what", "when", "how", "but", "so", "just", "then", "there", "have", "has", "will",
        "would", "should", "could", "an", "they", "we", "my", "which", "about", "into", "than", "don't",
        "use", "make", "like", "need", "want", "also", "here", "one", "all",
    ),
)
