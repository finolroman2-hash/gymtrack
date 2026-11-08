package com.example.gymtrack.assistant

import java.text.Normalizer
import java.util.Locale

/**
 * Léxico de sinónimos y variantes fonéticas (STT) por campo.
 * Fuente única para expandir la frase antes de extraer valores numéricos.
 */
object VoiceFieldLexicon {

    const val MARK_REPS = "__campo_reps__"
    const val MARK_WEIGHT = "__campo_peso__"
    const val MARK_RIR = "__campo_rir__"
    const val MARK_EXERCISE = "__campo_ejercicio__"

    private data class LexEntry(val canonical: String, val variants: List<String>)

    private val ENTRIES = listOf(
        LexEntry(
            MARK_REPS,
            listOf(
                "reps", "rep", "repeticiones", "repeticion", "repetes", "repe", "repes",
                "rapper", "rap", "repses", "repx", "repc", "repses",
                "series", "serie", "seri", "set", "sets",
                "hice", "hizo", "hago", "hacer", "hacemos",
            ),
        ),
        LexEntry(
            MARK_WEIGHT,
            listOf(
                "peso", "pesos", "pezo", "peso",
                "kilo", "kilos", "kg", "kgs", "kilo", "quilo", "quilos",
                "placa", "placas", "plaka", "plakas",
                "libra", "libras", "lbs", "lb",
                "carga", "cargo", "cargas",
                "disco", "discos", "barra", "barras",
                "mancuerna", "mancuernas", "mancuerna",
                "lastre", "lastres", "pesas",
            ),
        ),
        LexEntry(
            MARK_RIR,
            listOf(
                "rir", "reel", "riel", "reil", "rill", "ril", "reir", "rey", "real",
                "reserva", "reservas", "rirre", "rire", "rid", "rin",
            ),
        ),
        LexEntry(
            MARK_EXERCISE,
            listOf("ejercicio", "ejercicios", "ejersicio", "mover", "movimiento"),
        ),
    )

    private val UNIT_WORDS = mapOf(
        "cero" to 0, "uno" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4,
        "cinco" to 5, "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9,
    )

    /** "un"/"una" solo son número cuando preceden a una unidad ("una placa", "un kilo"). */
    private val CONDITIONAL_UNIT_WORDS = mapOf("un" to 1, "una" to 1)

    private val SPECIAL_WORDS = mapOf(
        "diez" to 10, "once" to 11, "doce" to 12, "trece" to 13, "catorce" to 14,
        "quince" to 15, "dieciseis" to 16, "diecisiete" to 17, "dieciocho" to 18,
        "diecinueve" to 19, "veinte" to 20, "veintiun" to 21, "veintiuno" to 21,
        "veintiuna" to 21, "veintidos" to 22, "veintitres" to 23, "veinticuatro" to 24,
        "veinticinco" to 25, "veintiseis" to 26, "veintisiete" to 27, "veintiocho" to 28,
        "veintinueve" to 29,
    )

    private val TENS_WORDS = mapOf(
        "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50, "sesenta" to 60,
        "setenta" to 70, "ochenta" to 80, "noventa" to 90,
    )

    fun prepare(raw: String): String {
        var s = expandNumbers(raw)
        s = expandFieldMarkers(s)
        s = s.replace(Regex("""(\d+)reps?\b"""), "$1 $MARK_REPS")
        s = s.replace(Regex("""(\d+)repeticiones?\b"""), "$1 $MARK_REPS")
        s = s.replace(Regex("""(\d+)\s*(?:kg|kilos?|kgs)\b"""), "$1 $MARK_WEIGHT ")
        s = s.replace(Regex("""(\d+)kg\b"""), "$1 $MARK_WEIGHT ")
        s = s.replace(Regex("""(\d+)\s*(?:rir|reel|riel|reil|rill|ril|reir)\b"""), "$1 $MARK_RIR ")
        s = s.replace(Regex("""(?:rir|reel|riel|reil|rill|ril|reir)(\d+)\b"""), "$MARK_RIR $1")
        s = s.replace(Regex("""(\d+)(?:rir|reel|riel|reil|rill|ril|reir)\b"""), "$1 $MARK_RIR ")
        s = s.replace(Regex("""\bcon\s+(\d+(?:[.,]\d+)?)\s*(?:kg|kilos?)?\b"""), " $MARK_WEIGHT $1 ")
        s = s.replace(Regex("""\by\s+(\d+)\b"""), " y $1 ")
        return s.replace(Regex("\\s+"), " ").trim()
    }

    fun fieldAtWordIndex(words: List<String>, index: Int, maxDistance: Int = 4): VoiceSetField? {
        var bestField: VoiceSetField? = null
        var bestDistance = Int.MAX_VALUE
        for (d in 0..maxDistance) {
            val before = index - d
            val after = index + d
            if (before >= 0) {
                fieldForToken(words[before])?.let { field ->
                    if (d < bestDistance) {
                        bestDistance = d
                        bestField = field
                    }
                }
            }
            if (after < words.size) {
                fieldForToken(words[after])?.let { field ->
                    if (d < bestDistance) {
                        bestDistance = d
                        bestField = field
                    }
                }
            }
        }
        return bestField
    }

    fun fieldForToken(token: String): VoiceSetField? {
        val t = normalize(token)
        if (t.isBlank()) return null
        for (entry in ENTRIES) {
            if (matchesAnyVariant(t, entry.variants)) {
                return when (entry.canonical) {
                    MARK_REPS -> VoiceSetField.REPS
                    MARK_WEIGHT -> VoiceSetField.WEIGHT
                    MARK_RIR -> VoiceSetField.RIR
                    MARK_EXERCISE -> VoiceSetField.EXERCISE
                    else -> null
                }
            }
        }
        return null
    }

    fun isFieldMarker(token: String): Boolean =
        token == MARK_REPS || token == MARK_WEIGHT || token == MARK_RIR || token == MARK_EXERCISE

    fun matchesAnyVariant(word: String, variants: List<String>): Boolean {
        val w = normalize(word)
        if (w.isBlank()) return false
        for (v in variants) {
            val vn = normalize(v)
            if (w == vn) return true
            val maxEdit = when {
                vn.length <= 3 -> 0
                vn.length <= 5 -> 1
                else -> 2
            }
            if (levenshtein(w, vn) <= maxEdit) return true
            if (vn.length >= 4 && (w.contains(vn) || vn.contains(w))) return true
        }
        return false
    }

    /**
     * Normaliza y convierte números en palabras a dígitos: compuestos ("treinta y cinco" → 35,
     * "ciento veinte" → 120) y decimales ("dos y medio" → 2.5, "treinta coma cinco" → 30.5).
     */
    fun expandNumbers(raw: String): String {
        var s = expandNumberWords(normalize(raw))
        s = s.replace(Regex("""\b(\d+)\s+(?:coma|punto)\s+(\d+)\b"""), "$1.$2")
        s = s.replace(Regex("""\b(\d+)\s+y\s+(?:medio|media)\b"""), "$1.5")
        return s
    }

    private fun expandNumberWords(text: String): String {
        val words = text.split(" ").filter { it.isNotBlank() }
        val out = mutableListOf<String>()
        var i = 0
        while (i < words.size) {
            val parsed = parseNumberAt(words, i)
            if (parsed != null) {
                out += parsed.first.toString()
                i = parsed.second
            } else {
                out += words[i]
                i++
            }
        }
        return out.joinToString(" ")
    }

    /** Devuelve (valor, índice siguiente) si en [start] empieza un número en palabras. */
    private fun parseNumberAt(words: List<String>, start: Int): Pair<Int, Int>? {
        val w = words[start]
        if (w == "cien") return 100 to start + 1
        if (w == "ciento") {
            val rest = if (start + 1 < words.size) parseBelowHundred(words, start + 1) else null
            return if (rest != null) (100 + rest.first) to rest.second else 100 to start + 1
        }
        return parseBelowHundred(words, start)
    }

    private fun parseBelowHundred(words: List<String>, start: Int): Pair<Int, Int>? {
        val w = words[start]
        SPECIAL_WORDS[w]?.let { return it to start + 1 }
        TENS_WORDS[w]?.let { tens ->
            if (start + 2 < words.size && words[start + 1] == "y") {
                val unit = UNIT_WORDS[words[start + 2]] ?: CONDITIONAL_UNIT_WORDS[words[start + 2]]
                if (unit != null && unit > 0) return (tens + unit) to start + 3
            }
            return tens to start + 1
        }
        UNIT_WORDS[w]?.let { return it to start + 1 }
        CONDITIONAL_UNIT_WORDS[w]?.let { unit ->
            val next = words.getOrNull(start + 1) ?: return null
            if (fieldForToken(next) != null) return unit to start + 1
        }
        return null
    }

    private fun expandFieldMarkers(text: String): String {
        val words = text.split(" ").filter { it.isNotBlank() }.toMutableList()
        for (i in words.indices) {
            for (entry in ENTRIES) {
                if (matchesAnyVariant(words[i], entry.variants)) {
                    words[i] = entry.canonical
                    break
                }
            }
        }
        return words.joinToString(" ")
    }

    private fun normalize(s: String): String {
        val trimmed = s.trim().lowercase(Locale.ROOT)
        val decomposed = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
        return decomposed
            .replace(Regex("[^a-z0-9 .,\\-]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val dp = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            var prev = dp[0]
            dp[0] = i + 1
            for (j in b.indices) {
                val tmp = dp[j + 1]
                dp[j + 1] = minOf(dp[j + 1] + 1, dp[j] + 1, prev + if (a[i] == b[j]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }
}
