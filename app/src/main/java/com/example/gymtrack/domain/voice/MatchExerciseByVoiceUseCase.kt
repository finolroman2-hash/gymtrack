package com.example.gymtrack.domain.voice

/**
 * Objetivo de dominio mínimo (sin dependencias Android/Room).
 */
data class ExerciseMatchTarget(
    val id: Long,
    val name: String,
)

data class ExerciseMatchCandidate(
    val id: Long,
    val name: String,
    val score: Float,
)

sealed class FuzzyExerciseMatchResult {
    data class Match(val candidate: ExerciseMatchCandidate) : FuzzyExerciseMatchResult()
    data class Ambiguous(val candidates: List<ExerciseMatchCandidate>) : FuzzyExerciseMatchResult()
    data object Unrecognized : FuzzyExerciseMatchResult()
}

/**
 * Resuelve el ejercicio más probable a partir de texto de voz (exacto, parcial o ruidoso).
 */
class MatchExerciseByVoiceUseCase(
    /** Umbral mínimo de confianza; bajar = más permisivo, subir = más estricto. */
    private val minConfidence: Float = DEFAULT_MIN_CONFIDENCE,
    /**
     * Ratio mínimo runnerUp/top para considerar ambigüedad.
     * 0.67 ≈ reparto 40/60 (el segundo tiene ≥67% del score del primero).
     */
    private val ambiguityRelativeRatio: Float = DEFAULT_AMBIGUITY_RELATIVE_RATIO,
    /** También ambiguo si top − runnerUp ≤ este gap absoluto (p. ej. 0.60 vs 0.45). */
    private val ambiguityAbsoluteGap: Float = DEFAULT_AMBIGUITY_ABSOLUTE_GAP,
) {

    operator fun invoke(
        rawQuery: String,
        exercises: List<ExerciseMatchTarget>,
    ): FuzzyExerciseMatchResult {
        if (exercises.isEmpty() || rawQuery.isBlank()) return FuzzyExerciseMatchResult.Unrecognized

        val query = FuzzyTextNormalizer.stripStopWords(rawQuery)
        if (query.length < 2) return FuzzyExerciseMatchResult.Unrecognized

        val queryTokens = FuzzyTextNormalizer.tokenize(query)
        if (queryTokens.isEmpty()) return FuzzyExerciseMatchResult.Unrecognized

        val scored = exercises
            .map { target ->
                ExerciseMatchCandidate(
                    id = target.id,
                    name = target.name,
                    score = scoreQueryAgainstName(query, queryTokens, target.name),
                )
            }
            .filter { it.score >= minConfidence }
            .sortedWith(
                compareByDescending<ExerciseMatchCandidate> { it.score }
                    .thenByDescending { FuzzyTextNormalizer.tokenize(it.name).size }
                    .thenBy { it.name.length },
            )

        if (scored.isEmpty()) return FuzzyExerciseMatchResult.Unrecognized

        var leaders = findAmbiguousLeaders(scored)
        if (leaders.size == 1) {
            findSharedWordCompetitors(scored, queryTokens)?.let { shared ->
                if (shared.size > leaders.size) leaders = shared
            }
        }

        return when {
            leaders.size == 1 -> FuzzyExerciseMatchResult.Match(leaders.first())
            else -> FuzzyExerciseMatchResult.Ambiguous(leaders)
        }
    }

    /**
     * Candidatos que compiten de cerca con el líder: no solo 50/50, también ~60/40 o ~55/45.
     */
    private fun findAmbiguousLeaders(
        scored: List<ExerciseMatchCandidate>,
    ): List<ExerciseMatchCandidate> {
        val topScore = scored.first().score
        return scored.filter { candidate ->
            val gap = topScore - candidate.score
            val relativeOk = candidate.score >= topScore * ambiguityRelativeRatio
            val absoluteOk = gap <= ambiguityAbsoluteGap
            relativeOk || absoluteOk
        }
    }

    /**
     * Varios ejercicios comparten la palabra dicha ("press" → press banca / press militar).
     * Solo aplica cuando la frase es una sola palabra; si añades más, se desambigua sola.
     */
    private fun findSharedWordCompetitors(
        scored: List<ExerciseMatchCandidate>,
        queryTokens: List<String>,
    ): List<ExerciseMatchCandidate>? {
        if (queryTokens.size != 1 || scored.size < 2) return null
        val queryWord = queryTokens.single()
        val competitors = scored.filter { candidate ->
            candidate.score >= minConfidence &&
                FuzzyTextNormalizer.tokenize(candidate.name).any { nameToken ->
                    FuzzyStringSimilarity.tokenPairScore(queryWord, nameToken) >= TOKEN_COVERAGE_MIN
                }
        }
        return competitors.takeIf { it.size >= 2 }
    }

    private fun scoreQueryAgainstName(
        query: String,
        queryTokens: List<String>,
        exerciseName: String,
    ): Float {
        val normalizedName = FuzzyTextNormalizer.normalize(exerciseName)
        if (normalizedName.isBlank()) return 0f

        val nameTokens = FuzzyTextNormalizer.tokenize(exerciseName)
        if (nameTokens.isEmpty()) return 0f

        val tokenScore = FuzzyStringSimilarity.tokenMatchScore(queryTokens, nameTokens)
        val fullScore = FuzzyStringSimilarity.similarity(query, normalizedName)

        val matchedNameTokens = nameTokens.count { nameToken ->
            queryTokens.any { queryToken ->
                FuzzyStringSimilarity.tokenPairScore(queryToken, nameToken) >= TOKEN_COVERAGE_MIN
            }
        }
        val allNameTokensCovered = matchedNameTokens == nameTokens.size
        val coverageBonus = if (allNameTokensCovered) FULL_NAME_COVERAGE_BONUS else 0f

        /** Si solo coincide el prefijo compartido (p. ej. "press" en "press banca" y "press militar"). */
        val sharedPrefixOnly = matchedNameTokens > 0 &&
            matchedNameTokens < nameTokens.size &&
            queryTokens.size <= matchedNameTokens
        val prefixAmbiguityPenalty = if (sharedPrefixOnly) SHARED_PREFIX_PENALTY else 0f

        val queryCoversName = normalizedName.length >= 3 &&
            (query.contains(normalizedName) || normalizedName.contains(query))
        val substringBonus = if (queryCoversName) SUBSTRING_BONUS else 0f

        return (tokenScore * TOKEN_WEIGHT + fullScore * FULL_STRING_WEIGHT + coverageBonus + substringBonus - prefixAmbiguityPenalty)
            .coerceIn(0f, 1f)
    }

    companion object {
        /** Calibración: por debajo de este score → ejercicio no reconocido. */
        const val DEFAULT_MIN_CONFIDENCE = 0.4f

        /** 0.67 → ambiguo hasta ~40/60 (segundo ≥ 67% del primero). */
        const val DEFAULT_AMBIGUITY_RELATIVE_RATIO = 0.67f

        /** Ambiguo si la diferencia de score es ≤ 0.20 (p. ej. 0.58 vs 0.42). */
        const val DEFAULT_AMBIGUITY_ABSOLUTE_GAP = 0.20f

        private const val TOKEN_WEIGHT = 0.72f
        private const val FULL_STRING_WEIGHT = 0.18f
        private const val FULL_NAME_COVERAGE_BONUS = 0.08f
        private const val SUBSTRING_BONUS = 0.06f
        /** Acerca scores cuando solo coincide la palabra común (nombres con prefijo repetido). */
        private const val SHARED_PREFIX_PENALTY = 0.04f
        private const val TOKEN_COVERAGE_MIN = 0.55f
    }
}
