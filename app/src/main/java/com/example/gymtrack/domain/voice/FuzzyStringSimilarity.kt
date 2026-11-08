package com.example.gymtrack.domain.voice

/**
 * Similitud basada en Distancia de Levenshtein (edición mínima entre dos cadenas).
 *
 * Matemática: D(a,b) = mínimo de inserciones, borrados o sustituciones para transformar [a] en [b].
 * Similitud normalizada: 1 − D(a,b) / max(|a|, |b|) → rango [0.0, 1.0].
 *
 * Con tokens, "biceps mancuernas" encaja con "biceps man" porque cada palabra del nombre
 * busca su mejor par en la frase (p. ej. "man" ≈ "mancuernas" por prefijo + Levenshtein).
 */
object FuzzyStringSimilarity {

    fun similarity(a: String, b: String): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        if (a == b) return 1f
        val distance = levenshteinDistance(a, b)
        val maxLen = maxOf(a.length, b.length)
        return (1f - distance.toFloat() / maxLen).coerceIn(0f, 1f)
    }

    /**
     * Promedio del mejor match de cada token del nombre contra cualquier token de la consulta.
     * Prioriza cubrir todas las palabras del ejercicio guardado.
     */
    fun tokenMatchScore(queryTokens: List<String>, nameTokens: List<String>): Float {
        if (nameTokens.isEmpty()) return 0f
        if (queryTokens.isEmpty()) return 0f
        var sum = 0f
        for (nameToken in nameTokens) {
            val best = queryTokens.maxOf { queryToken -> tokenPairScore(queryToken, nameToken) }
            sum += best
        }
        return sum / nameTokens.size
    }

    fun tokenPairScore(queryToken: String, nameToken: String): Float {
        if (queryToken == nameToken) return 1f
        val lev = similarity(queryToken, nameToken)
        val prefix = prefixSimilarity(queryToken, nameToken)
        return maxOf(lev, prefix)
    }

    private fun prefixSimilarity(a: String, b: String): Float {
        val (shorter, longer) = if (a.length <= b.length) a to b else b to a
        if (shorter.length < 3) return 0f
        return when {
            longer.startsWith(shorter) ->
                0.72f + 0.28f * (shorter.length.toFloat() / longer.length)
            longer.contains(shorter) ->
                0.65f + 0.25f * (shorter.length.toFloat() / longer.length)
            else -> 0f
        }
    }

    fun levenshteinDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val dp = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            var prev = dp[0]
            dp[0] = i + 1
            for (j in b.indices) {
                val tmp = dp[j + 1]
                dp[j + 1] = minOf(
                    dp[j + 1] + 1,
                    dp[j] + 1,
                    prev + if (a[i] == b[j]) 0 else 1,
                )
                prev = tmp
            }
        }
        return dp[b.length]
    }
}
