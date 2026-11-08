package com.example.gymtrack.assistant

import com.example.gymtrack.data.local.entities.ExerciseEntity
import com.example.gymtrack.domain.voice.ExerciseMatchCandidate
import com.example.gymtrack.domain.voice.ExerciseMatchTarget
import com.example.gymtrack.domain.voice.FuzzyExerciseMatchResult
import com.example.gymtrack.domain.voice.MatchExerciseByVoiceUseCase
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

enum class VoiceSetField {
    EXERCISE,
    REPS,
    WEIGHT,
    RIR,
    NOTE,
    NONE,
}

data class VoiceSetCommand(
    val field: VoiceSetField,
    val value: String,
    val exerciseId: Long? = null,
    val exerciseName: String? = null,
)

data class VoiceSetParseResult(
    val commands: List<VoiceSetCommand>,
    /** Empate entre ejercicios similares; la UI debe pedir al usuario que elija uno. */
    val ambiguousExercises: List<ExerciseMatchCandidate> = emptyList(),
) {
    val hasAny: Boolean get() =
        commands.any { it.field != VoiceSetField.NONE } || ambiguousExercises.isNotEmpty()
}

/**
 * Interpreta frases de voz (simples o compuestas) y devuelve todos los campos detectados.
 */
object VoiceSetCommandParser {

    private val exerciseMatcher = MatchExerciseByVoiceUseCase()

    private const val MULTI_SYSTEM_PROMPT = """Eres un parser de comandos de voz para registrar series de gym en español.
Responde SOLO JSON válido (sin markdown):
{"commands":[{"field":"exercise|reps|weight|rir|note","value":"..."}, ...]}

Reglas:
- Extrae TODOS los campos que puedas de la frase en un solo mensaje.
- Omite campos que no estén claros; no inventes.
- "ejercicio biceps barra 5 reps rir 3 con 20kg" → exercise + reps + rir + weight.
- "peso 40 placas 8 reps" o "40 kilos y 20 repeticiones" → weight y reps con valores distintos.
- Sinónimos peso: peso, kilos, kg, placas, carga, discos, libras. Sinónimos reps: reps, repeticiones, series.
- "5 placas" o "placa 5" → weight=5 (placas = peso, valor tal cual).
- Números en palabras cuentan: "treinta y cinco kilos" → weight=35; "doce repeticiones" → reps=12.
- Decimales: "treinta y dos y medio kg" → weight=32.5; "veinte coma cinco" → 20.5.
- "7reps rir 3 en biceps arriba" → reps=7, rir=3, exercise (mejor coincidencia en la lista).
- RIR transcrito como reel/riel/reir/ril/reil = field rir. RIR admite cualquier entero ≥ 0, también mayores que 10 ("rir 12" → rir=12).
- Para exercise: value = nombre EXACTO de la lista de ejercicios (el más parecido).
- Si no hay nada claro: {"commands":[]}"""

    /**
     * Versión multi-hipótesis: recibe TODAS las alternativas del STT (ordenadas por confianza)
     * y elige la que produce la interpretación más completa antes de aplicar el pipeline normal.
     */
    suspend fun parseAll(
        alternatives: List<String>,
        exercises: List<ExerciseEntity>,
        selectedExerciseId: Long?,
        reps: String,
        weight: String,
        rir: String,
        note: String,
        useCloudParser: Boolean = true,
    ): VoiceSetParseResult {
        val candidates = alternatives.map { it.trim() }.filter { it.length >= 2 }.distinct()
        if (candidates.isEmpty()) return VoiceSetParseResult(emptyList())

        // maxByOrNull conserva la primera en caso de empate → respeta el orden de confianza del STT.
        val chosen = candidates
            .maxByOrNull { alt -> localResultScore(parseAllLocally(alt, exercises)) }
            ?: candidates.first()

        return parseAll(chosen, exercises, selectedExerciseId, reps, weight, rir, note, useCloudParser)
    }

    private fun localResultScore(result: VoiceSetParseResult): Float {
        var score = 0f
        result.commands.forEach { cmd ->
            score += when (cmd.field) {
                VoiceSetField.REPS, VoiceSetField.WEIGHT, VoiceSetField.RIR -> 1f
                VoiceSetField.EXERCISE -> 1.5f
                VoiceSetField.NOTE -> 0.5f
                VoiceSetField.NONE -> 0f
            }
        }
        if (result.ambiguousExercises.isNotEmpty()) score += 0.75f
        return score
    }

    suspend fun parseAll(
        utterance: String,
        exercises: List<ExerciseEntity>,
        selectedExerciseId: Long?,
        reps: String,
        weight: String,
        rir: String,
        note: String,
        useCloudParser: Boolean = true,
    ): VoiceSetParseResult {
        val trimmed = utterance.trim()
        if (trimmed.length < 2) return VoiceSetParseResult(emptyList())

        val local = parseAllLocally(trimmed, exercises)
        if (!useCloudParser) return local

        if (local.commands.size >= 2 && local.ambiguousExercises.isEmpty()) return local

        if (PibbleLlmRotator.hasAnyProviderConfigured() && shouldUseLlm(trimmed, local.commands)) {
            val llm = parseAllLlm(trimmed, exercises, selectedExerciseId, reps, weight, rir, note)
            val merged = mergeCommands(local.commands, llm)
            if (merged.isNotEmpty()) {
                return VoiceSetParseResult(merged, local.ambiguousExercises)
            }
        }

        if (local.hasAny) return local

        if (!PibbleLlmRotator.hasAnyProviderConfigured()) {
            return VoiceSetParseResult(emptyList())
        }

        return VoiceSetParseResult(
            parseAllLlm(trimmed, exercises, selectedExerciseId, reps, weight, rir, note),
        )
    }

    private fun shouldUseLlm(utterance: String, local: List<VoiceSetCommand>): Boolean {
        if (local.isEmpty()) return true
        if (local.size >= 2) return false
        val n = VoiceFieldLexicon.prepare(utterance)
        val hintCount = listOf(
            n.contains(VoiceFieldLexicon.MARK_REPS),
            n.contains(VoiceFieldLexicon.MARK_WEIGHT),
            n.contains(VoiceFieldLexicon.MARK_RIR),
            n.contains(VoiceFieldLexicon.MARK_EXERCISE),
            n.any { it.isLetter() },
        ).count { it }
        return utterance.length >= 12 && hintCount >= 2
    }

    private fun mergeCommands(
        primary: List<VoiceSetCommand>,
        secondary: List<VoiceSetCommand>,
    ): List<VoiceSetCommand> {
        val map = linkedMapOf<VoiceSetField, VoiceSetCommand>()
        secondary.forEach { if (it.field != VoiceSetField.NONE) map[it.field] = it }
        primary.forEach { if (it.field != VoiceSetField.NONE) map[it.field] = it }
        return map.values.toList()
    }

    private suspend fun parseAllLlm(
        utterance: String,
        exercises: List<ExerciseEntity>,
        selectedExerciseId: Long?,
        reps: String,
        weight: String,
        rir: String,
        note: String,
    ): List<VoiceSetCommand> {
        return try {
            val forParsing = utteranceForParsing(utterance)
            val exerciseLines = exercises.joinToString("\n") { "- ${it.id}: ${it.name}" }
            val selectedName = exercises.firstOrNull { it.id == selectedExerciseId }?.name ?: "(ninguno)"
            val userPrompt = buildString {
                appendLine("Frase: \"$utterance\"")
                if (forParsing != normalize(utterance)) {
                    appendLine("Normalizada RIR: \"$forParsing\"")
                }
                appendLine()
                appendLine("Ejercicios (value = nombre EXACTO):")
                appendLine(exerciseLines)
                appendLine()
                appendLine("Estado actual: ejercicio=$selectedName, reps=${reps.ifBlank { "-" }}, peso=${weight.ifBlank { "-"}}, rir=${rir.ifBlank { "-"}}, nota=${note.ifBlank { "-"}}")
            }
            val raw = PibbleLlmRotator.generateContent(MULTI_SYSTEM_PROMPT, userPrompt, maxOutputTokens = 220)
            refineExerciseCommands(parseLlmCommandsJson(raw, exercises), utterance, exercises)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun parseAllLocally(
        utterance: String,
        exercises: List<ExerciseEntity>,
    ): VoiceSetParseResult {
        val prepared = VoiceFieldLexicon.prepare(utterance)
        val commands = VoiceFieldSegmenter.segmentNumbersOnly(prepared).toMutableList()

        // Si tras quitar los campos numéricos no queda texto, la frase no menciona
        // ningún ejercicio: no intentar fuzzy match (evita falsos positivos tipo
        // "5 placas" → marcador de peso → "Peso muerto").
        val exerciseQuery = VoiceFieldSegmenter.stripForExerciseQuery(prepared)
        if (exerciseQuery.isBlank()) return VoiceSetParseResult(commands)

        return when (val match = matchExercise(exerciseQuery, exercises)) {
            is FuzzyExerciseMatchResult.Match ->
                VoiceSetParseResult(commands + toExerciseCommand(match.candidate))
            is FuzzyExerciseMatchResult.Ambiguous ->
                VoiceSetParseResult(commands, match.candidates)
            FuzzyExerciseMatchResult.Unrecognized ->
                VoiceSetParseResult(commands)
        }
    }

    private fun matchExercise(
        query: String,
        exercises: List<ExerciseEntity>,
    ): FuzzyExerciseMatchResult {
        val targets = exercises.map { ExerciseMatchTarget(id = it.id, name = it.name) }
        return exerciseMatcher(query, targets)
    }

    private fun toExerciseCommand(candidate: ExerciseMatchCandidate): VoiceSetCommand =
        VoiceSetCommand(
            field = VoiceSetField.EXERCISE,
            value = candidate.name,
            exerciseId = candidate.id,
            exerciseName = candidate.name,
        )

    private fun toExerciseCommand(ex: ExerciseEntity): VoiceSetCommand =
        VoiceSetCommand(
            field = VoiceSetField.EXERCISE,
            value = ex.name,
            exerciseId = ex.id,
            exerciseName = ex.name,
        )

    /** Si la IA devuelve un nombre corto, re-evalúa con la frase completa del usuario. */
    private fun refineExerciseCommands(
        commands: List<VoiceSetCommand>,
        utterance: String,
        exercises: List<ExerciseEntity>,
    ): List<VoiceSetCommand> {
        val better = when (val result = matchExercise(utterance, exercises)) {
            is FuzzyExerciseMatchResult.Match -> toExerciseCommand(result.candidate)
            else -> return commands
        }
        return commands.map { cmd ->
            if (cmd.field != VoiceSetField.EXERCISE) cmd
            else {
                val fromUtterance = normalize(better.exerciseName.orEmpty())
                val fromLlm = normalize(cmd.exerciseName ?: cmd.value)
                if (fromUtterance.length > fromLlm.length) better else cmd
            }
        }.let { list ->
            if (list.none { it.field == VoiceSetField.EXERCISE }) list + better else list
        }
    }

    private fun parseLlmCommandsJson(raw: String, exercises: List<ExerciseEntity>): List<VoiceSetCommand> {
        val jsonStr = extractJsonObject(raw) ?: return emptyList()
        return runCatching {
            val root = JSONObject(jsonStr)
            val arr: JSONArray = when {
                root.has("commands") -> root.getJSONArray("commands")
                root.has("field") -> JSONArray().put(root)
                else -> return emptyList()
            }
            val out = linkedMapOf<VoiceSetField, VoiceSetCommand>()
            for (i in 0 until arr.length()) {
                val cmd = parseSingleLlmCommand(arr.getJSONObject(i), exercises) ?: continue
                if (cmd.field != VoiceSetField.NONE) out[cmd.field] = cmd
            }
            out.values.toList()
        }.getOrElse { emptyList() }
    }

    private fun parseSingleLlmCommand(obj: JSONObject, exercises: List<ExerciseEntity>): VoiceSetCommand? {
        val fieldStr = obj.optString("field", "none").lowercase(Locale.ROOT)
        val value = obj.optString("value", "").trim()
        val field = when (fieldStr) {
            "exercise", "ejercicio" -> VoiceSetField.EXERCISE
            "reps", "repeticiones" -> VoiceSetField.REPS
            "weight", "peso" -> VoiceSetField.WEIGHT
            "rir", "reel", "riel", "reil", "ril", "reir" -> VoiceSetField.RIR
            "note", "nota" -> VoiceSetField.NOTE
            else -> VoiceSetField.NONE
        }
        return when (field) {
            VoiceSetField.EXERCISE -> matchExerciseCommand(value, exercises)
                ?: matchExerciseCommand(value.ifBlank { obj.toString() }, exercises)
            VoiceSetField.REPS -> extractInt(value)?.let { VoiceSetCommand(VoiceSetField.REPS, it.toString()) }
            VoiceSetField.WEIGHT -> extractFloat(value)?.let {
                VoiceSetCommand(VoiceSetField.WEIGHT, formatWeight(it))
            }
            VoiceSetField.RIR -> extractInt(value)?.let {
                VoiceSetCommand(VoiceSetField.RIR, sanitizeRir(it).toString())
            }
            VoiceSetField.NOTE -> if (value.isNotBlank()) VoiceSetCommand(VoiceSetField.NOTE, value) else null
            VoiceSetField.NONE -> null
        }
    }

    private fun matchExerciseCommand(query: String, exercises: List<ExerciseEntity>): VoiceSetCommand? =
        when (val result = matchExercise(query, exercises)) {
            is FuzzyExerciseMatchResult.Match -> toExerciseCommand(result.candidate)
            else -> null
        }

    private fun sanitizeRir(n: Int): Int = n.coerceAtLeast(0)

    private fun normalizeRirPhonetics(n: String): String =
        RIR_PHONETIC_RE.replace(n, " rir ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun utteranceForParsing(raw: String): String = normalizeRirPhonetics(normalize(raw))

    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return text.substring(start, end + 1)
    }

    private fun extractInt(value: String): Int? = extractIntFromSpeech(normalize(value))

    private fun extractFloat(value: String): Float? = extractFloatFromSpeech(normalize(value))

    private fun extractIntFromSpeech(n: String): Int? {
        val expanded = VoiceFieldLexicon.expandNumbers(n)
        return Regex("""(\d+)""").find(expanded)?.value?.toIntOrNull()
    }

    private fun extractFloatFromSpeech(n: String): Float? {
        val expanded = VoiceFieldLexicon.expandNumbers(n)
        Regex("""(\d+[.,]\d+)""").find(expanded)?.value
            ?.replace(',', '.')?.toFloatOrNull()?.let { return it }
        return Regex("""(\d+)""").find(expanded)?.value?.toFloatOrNull()
    }

    private fun formatWeight(v: Float): String =
        if (v == v.toLong().toFloat()) v.toLong().toString() else "%.1f".format(Locale.US, v)

    private fun normalize(s: String): String {
        val trimmed = s.trim().lowercase(Locale.ROOT)
        val decomposed = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
        return decomposed
            .replace('¿', ' ')
            .replace('¡', ' ')
            .replace(Regex("[^a-z0-9 .,\\-]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private val RIR_PHONETIC_RE = Regex("""\b(reel|riel|reil|rill|ril|reir|rid|rin)\b""")
}
