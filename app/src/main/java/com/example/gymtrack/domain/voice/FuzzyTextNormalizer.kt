package com.example.gymtrack.domain.voice

import java.text.Normalizer
import java.util.Locale

/**
 * Preprocesamiento de texto para comparación difusa (entrada STT y nombres en BD).
 */
object FuzzyTextNormalizer {

    private val STOP_WORDS = Regex(
        """\b(ejercicio|ejercicios|hacer|serie|series|de|con|el|la|los|las|un|una|en|y|e|a|al|del|para|por)\b""",
    )

    /** Minúsculas, sin tildes, sin puntuación ruidosa, espacios colapsados. */
    fun normalize(text: String): String {
        val trimmed = text.trim().lowercase(Locale.ROOT)
        val decomposed = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
        return decomposed
            .replace(Regex("[^a-z0-9\\-]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** Tokens útiles (palabras de 2+ caracteres). */
    fun tokenize(text: String): List<String> =
        normalize(text).split(" ").filter { it.length > 1 }

    /** Quita conectores habituales en frases de voz antes de comparar nombres. */
    fun stripStopWords(text: String): String =
        normalize(text)
            .replace(STOP_WORDS, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
