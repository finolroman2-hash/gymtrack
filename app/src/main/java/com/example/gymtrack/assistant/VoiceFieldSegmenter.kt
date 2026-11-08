package com.example.gymtrack.assistant

/**
 * Asigna cada número al campo correcto (reps, peso, RIR) usando patrones + proximidad al léxico.
 * Evita que el primer número detectado se aplique a todos los campos.
 */
object VoiceFieldSegmenter {

    data class Span(
        val field: VoiceSetField,
        val value: String,
        val start: Int,
        val end: Int,
    )

    /** Solo reps, peso y RIR; el ejercicio lo resuelve [VoiceSetCommandParser] con fuzzy match. */
    fun segmentNumbersOnly(prepared: String): List<VoiceSetCommand> {
        if (prepared.length < 2) return emptyList()

        val spans = linkedMapOf<VoiceSetField, Span>()
        fun put(field: VoiceSetField, value: String, start: Int, end: Int) {
            val existing = spans[field]
            if (existing == null || (end - start) > (existing.end - existing.start)) {
                spans[field] = Span(field, value, start, end)
            }
        }

        extractByRegex(prepared, WEIGHT_REGEXES, VoiceSetField.WEIGHT, ::formatWeight).forEach {
            put(it.field, it.value, it.start, it.end)
        }
        extractByRegex(prepared, REPS_REGEXES, VoiceSetField.REPS) { num ->
            num.toInt().toString()
        }.forEach {
            put(it.field, it.value, it.start, it.end)
        }
        extractByRegex(prepared, RIR_REGEXES, VoiceSetField.RIR) { sanitizeRir(it.toInt()).toString() }.forEach {
            put(it.field, it.value, it.start, it.end)
        }

        bindOrphanNumbers(prepared, spans)

        val commands = spans.values.map { span ->
            when (span.field) {
                VoiceSetField.REPS -> VoiceSetCommand(VoiceSetField.REPS, span.value)
                VoiceSetField.WEIGHT -> VoiceSetCommand(VoiceSetField.WEIGHT, span.value)
                VoiceSetField.RIR -> VoiceSetCommand(VoiceSetField.RIR, span.value)
                else -> VoiceSetCommand(span.field, span.value)
            }
        }

        return commands
    }

    private fun bindOrphanNumbers(text: String, spans: MutableMap<VoiceSetField, Span>) {
        val words = text.split(" ").filter { it.isNotBlank() }
        if (words.isEmpty()) return

        val usedIndices = words.indices.filter { idx ->
            spans.values.any { span ->
                val start = wordIndexToCharStart(text, idx)
                val end = start + words[idx].length
                start < span.end && end > span.start
            }
        }.toSet()

        for (i in words.indices) {
            if (i in usedIndices) continue
            val num = parseNumberToken(words[i]) ?: continue
            val field = VoiceFieldLexicon.fieldAtWordIndex(words, i) ?: continue
            if (spans.containsKey(field)) continue

            val start = wordIndexToCharStart(text, i)
            val end = start + words[i].length
            val value = when (field) {
                VoiceSetField.WEIGHT -> formatWeight(num)
                VoiceSetField.RIR -> sanitizeRir(num.toInt()).toString()
                VoiceSetField.REPS -> num.toInt().toString()
                else -> continue
            }
            spans[field] = Span(field, value, start, end)
        }
    }

    /** Texto restante tras quitar campos numéricos; útil para fuzzy match de ejercicio. */
    fun stripForExerciseQuery(prepared: String): String = stripForExercise(prepared)

    private fun stripForExercise(prepared: String): String {
        var s = prepared
        for (mark in listOf(
            VoiceFieldLexicon.MARK_REPS,
            VoiceFieldLexicon.MARK_WEIGHT,
            VoiceFieldLexicon.MARK_RIR,
            VoiceFieldLexicon.MARK_EXERCISE,
        )) {
            s = s.replace(mark, " ")
        }
        s = s.replace(Regex("""(\d+(?:[.,]\d+)?)\s*(?:kg|kilos?|kgs|lbs?|libras?|placas?)\b"""), " ")
        s = s.replace(Regex("""(\d+)\s*reps?\b"""), " ")
        s = s.replace(Regex("""(\d+)\s*repeticiones?\b"""), " ")
        s = s.replace(Regex("""(?:rir|reel|riel|reil|rill|ril|reir|reserva)\s*\d+"""), " ")
        s = s.replace(Regex("""\d+\s*(?:rir|reel|riel|reil|rill|ril|reir)"""), " ")
        s = s.replace(Regex("""\b(en|de|con|el|la|un|una|serie|hacer|hice|y|e)\b"""), " ")
        s = s.replace(Regex("""\b\d+(?:[.,]\d+)?\b"""), " ")
        return s.replace(Regex("\\s+"), " ").trim()
    }

    private fun extractByRegex(
        text: String,
        patterns: List<Regex>,
        field: VoiceSetField,
        format: (Float) -> String,
    ): List<Span> {
        val out = mutableListOf<Span>()
        for (re in patterns) {
            for (match in re.findAll(text)) {
                val raw = match.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: continue
                val num = parseNumberToken(raw) ?: continue
                out += Span(field, format(num), match.range.first, match.range.last + 1)
            }
        }
        return out
    }

    private fun wordIndexToCharStart(text: String, wordIndex: Int): Int {
        var w = 0
        var i = 0
        while (i < text.length && w < wordIndex) {
            while (i < text.length && text[i].isWhitespace()) i++
            while (i < text.length && !text[i].isWhitespace()) i++
            w++
        }
        while (i < text.length && text[i].isWhitespace()) i++
        return i
    }

    private fun parseNumberToken(token: String): Float? {
        val t = token.replace(',', '.')
        t.toFloatOrNull()?.let { return it }
        t.toIntOrNull()?.let { return it.toFloat() }
        return null
    }

    private fun formatWeight(v: Float): String =
        if (v == v.toLong().toFloat()) v.toLong().toString() else "%.1f".format(java.util.Locale.US, v)

    /** Sin tope superior: se aceptan RIR mayores que 10 si el usuario los dicta. */
    private fun sanitizeRir(n: Int): Int = n.coerceAtLeast(0)

    private val WEIGHT_REGEXES = listOf(
        Regex("""(\d+(?:[.,]\d+)?)\s*(?:${VoiceFieldLexicon.MARK_WEIGHT}|kg|kilos?|kgs|lbs?|libras?|placas?|discos?)\b"""),
        Regex("""(?:${VoiceFieldLexicon.MARK_WEIGHT}|peso|carga|placas?|kilos?)\s*(?:de\s+)?(\d+(?:[.,]\d+)?)"""),
        Regex("""(\d+(?:[.,]\d+)?)\s*(?:de\s+)?(?:${VoiceFieldLexicon.MARK_WEIGHT}|peso|carga|placas?)"""),
        Regex("""\bcon\s+(\d+(?:[.,]\d+)?)\s*(?:kg|kilos?)?\b"""),
    )

    private val REPS_REGEXES = listOf(
        Regex("""(\d+)\s*(?:${VoiceFieldLexicon.MARK_REPS}|reps?|repeticiones?|series?)\b"""),
        Regex("""(?:${VoiceFieldLexicon.MARK_REPS}|reps?|repeticiones?)\s*(?:de\s+)?(\d+)"""),
        Regex("""(?:hice|hago|hacer)\s+(\d+)\b"""),
    )

    private val RIR_REGEXES = listOf(
        Regex("""(?:${VoiceFieldLexicon.MARK_RIR}|rir|reel|riel|reil|rill|ril|reir|reserva)\s*(\d+)"""),
        Regex("""(\d+)\s*(?:${VoiceFieldLexicon.MARK_RIR}|rir|reel|riel|reil|rill|ril|reir)"""),
    )
}
