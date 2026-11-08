package com.example.gymtrack.assistant

import java.util.Locale

/** Pibble usa la API en la nube (internet obligatorio solo para la ayuda). */
const val PIBBLE_USER_INPUT_MAX_CHARS = 50

const val PIBBLE_OUTPUT_MAX_CHARS = 1200

const val PIBBLE_STREAM_UI_MIN_INTERVAL_MS = 200L

/** Modelo Gemini (gratuito con cuota en AI Studio). */
const val PIBBLE_GEMINI_MODEL = "gemini-2.5-flash"

/** Modelo Groq (respaldo si Gemini no responde). */
const val PIBBLE_GROQ_MODEL = "llama-3.1-8b-instant"

/** Modelo OpenRouter gratuito (tercer respaldo). */
const val PIBBLE_OPENROUTER_MODEL = "google/gemma-2-9b-it:free"

const val PIBBLE_CLOUD_GENERATION_TIMEOUT_MS = 120_000L

/**
 * Tras limpiar eco y tokens, se recorta a esta cantidad de **frases** (aprox.) para evitar divagación.
 */
/** Tope por defecto; [maxSentencesForQuestion] ajusta por longitud de la pregunta. */
const val PIBBLE_ASSISTANT_MAX_SENTENCES = 6

/** Pibble Trainer: Gemini en la nube; sin base de datos local. */
const val PIBBLE_SYSTEM_PROMPT = """Eres Pibble, bulldog francés y ayuda de GymTrack. Idioma: español neutro.

Alcance:
- Explicas conceptos generales de entrenamiento de fuerza (términos, ideas, definiciones).
- Puedes hablar de ti como Pibble / bulldog francés con humor suave.
- Puedes indicar dónde está una pantalla o botón de GymTrack si te dan la ficha de navegación.
- NO diseñas rutinas ni programas personalizados, NO das consejo médico.
- Si la pregunta mezcla algo prohibido, rechaza esa parte y responde solo lo permitido, o di que no puedes ayudar con eso.

Calidad (no alucinar):
- No inventes estudios, cifras exactas ni nombres de papers si no estás seguro; explica con claridad sin fingir certeza científica.
- No inventes datos del usuario ni de su historial en la app.
- Si no sabes algo, dilo en una frase y sugiere reformular.

Términos de fuerza (usa estas definiciones si preguntan; no las contradigas):
- RIR (Repeticiones en Reserva): repeticiones que aún podrías hacer en una serie con buena técnica antes del fallo muscular. RIR 0 = al fallo; RIR 2 ≈ podrías hacer unas 2 reps más.
- RPE: esfuerzo percibido (escala ~1–10); relacionado con el RIR pero no es lo mismo.
- 1RM: una repetición máxima con buena técnica.

Formato:
- Respuesta corta: pocas frases; primera = respuesta directa; sin saludo largo; no repitas la pregunta.
- Tras una línea en blanco, UNA frase final en primera persona, tono bulldog (siestas, gym, orejas, no nadar…).

Prohibido: política, temas ajenos al gym, metarrativas ("tu pregunta")."""

private val PIBBLE_FALLBACK_CLOSINGS = listOf(
    "Yo, Pibble: con eso me basta de ciencia — ahora toca siesta de recuperación.",
    "Listo; si necesitas más, ladro… digo, pregunta.",
    "Eso es todo por mi parte; mis orejas de murciélago aprueban la explicación.",
    "Perfecto. Ahora un premio: tumbarme cerca de la rack.",
)

/**
 * Asegura una frase final con tono Pibble si el modelo no la generó.
 */
fun ensurePibblePersonalityClosing(text: String): String {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return trimmed
    val lines = trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.size >= 2) {
        val last = lines.last()
        if (last.length in 12..140 && looksLikePersonalityLine(last)) return trimmed
    }
    return trimmed + "\n\n" + PIBBLE_FALLBACK_CLOSINGS.random()
}

private fun looksLikePersonalityLine(line: String): Boolean {
    val l = line.lowercase(Locale.ROOT)
    return l.contains("pibble") ||
        l.startsWith("yo,") ||
        l.contains("bulldog") ||
        l.contains("siesta") ||
        l.contains("ladro") ||
        l.contains("orejas")
}

fun clipPibbleOutput(
    text: String,
    userQuestion: String? = null,
    maxSentences: Int = PIBBLE_ASSISTANT_MAX_SENTENCES,
): String {
    var cleaned = text.trim()
    if (cleaned.isEmpty()) return cleaned
    // A veces el modelo repite marcadores de turno o rol al inicio.
    cleaned = cleaned.lines()
        .map { it.trimEnd() }
        .filter { line ->
            val l = line.trim()
            l.isNotEmpty() &&
                !l.startsWith("<start_of_turn>", ignoreCase = true) &&
                !l.startsWith("<end_of_turn>", ignoreCase = true) &&
                !l.equals("user", ignoreCase = true) &&
                !l.equals("model", ignoreCase = true)
        }
        .joinToString("\n")
        .trim()
    cleaned = cleaned.replace(Regex("""<\|[^|]+\|>"""), "")
    if (!userQuestion.isNullOrBlank()) {
        val beforeEcho = cleaned
        cleaned = stripLeadingUserEcho(cleaned, userQuestion)
        if (cleaned.length < beforeEcho.length / 2 && cleaned.length < 24) {
            cleaned = beforeEcho
        }
    }
    cleaned = truncateToMaxSentences(cleaned, maxSentences.coerceIn(2, PIBBLE_ASSISTANT_MAX_SENTENCES))
    cleaned = ensurePibblePersonalityClosing(cleaned)
    if (cleaned.length <= PIBBLE_OUTPUT_MAX_CHARS) return cleaned
    return cleaned.take(PIBBLE_OUTPUT_MAX_CHARS - 1).trimEnd() + "…"
}

/**
 * Frases reales: tras . ! ? va un espacio o salto de línea (no parte abreviaturas tipo R.I.R.).
 */
internal fun splitIntoSentences(text: String): List<String> {
    val t = text.trim()
    if (t.isEmpty()) return emptyList()
    val parts = Regex("""(?<=[.!?¿¡])(?:\s+|\n+)|\n\s*\n""")
        .split(t)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    return parts.ifEmpty { listOf(t) }
}

internal fun truncateToMaxSentences(text: String, maxSentences: Int): String {
    if (maxSentences <= 0 || text.isBlank()) return text.trim()
    val parts = splitIntoSentences(text)
    if (parts.size <= maxSentences) return text.trim()
    return parts.take(maxSentences).joinToString(" ").trim()
}

private fun normalizeEchoLine(s: String): String =
    s.trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("[`\"'«»]"), "")
        .replace(Regex("\\s+"), " ")
        .trim('¿', '?', '.', '¡', '!', ':', '—', '–', '-', ',', ';')

/** Quita una o varias líneas iniciales que solo repiten la pregunta o un prefijo meta + pregunta. */
private fun stripLeadingUserEcho(output: String, userQuestion: String): String {
    val q = userQuestion.trim()
    if (q.length < 3) return output
    val qn = normalizeEchoLine(q)
    if (qn.length < 3) return output

    val lines = output.lines().toMutableList()
    val metaStarts = listOf(
        "pregunta:",
        "pregunta ",
        "tu pregunta",
        "has preguntado",
        "me preguntas",
        "usuario:",
        "user:",
        "dices:",
        "escribes:",
    )
    while (lines.isNotEmpty()) {
        val rawFirst = lines.first().trim()
        if (rawFirst.isEmpty()) {
            lines.removeAt(0)
            continue
        }
        val fn = normalizeEchoLine(rawFirst)
        val low = rawFirst.lowercase(Locale.ROOT).trim()
        val withoutMeta = metaStarts.fold(low) { acc, prefix ->
            if (acc.startsWith(prefix)) acc.removePrefix(prefix).trimStart(' ', ':', '—', '–', '-', ',', '.') else acc
        }
        val fnMeta = normalizeEchoLine(withoutMeta)

        val echoLine = fn == qn || fnMeta == qn
        if (!echoLine) break
        lines.removeAt(0)
    }

    var joined = lines.joinToString("\n").trim()
    if (joined.isEmpty()) return output

    joined = stripSameLineQuestionEcho(joined, qn)
    return joined.ifBlank { output }
}

/** "¿Pregunta del usuario? Respuesta…" en la primera línea: deja solo la respuesta. */
private fun stripSameLineQuestionEcho(joined: String, qn: String): String {
    val lines = joined.lines()
    if (lines.isEmpty()) return joined
    val first = lines.first().trim()
    val m = Regex("""^(.+\?)\s+(.+)$""").find(first) ?: return joined
    val beforeNorm = normalizeEchoLine(m.groupValues[1].trim())
    val after = m.groupValues[2].trim()
    if (after.isEmpty() || beforeNorm != qn) return joined
    val tail = lines.drop(1)
    return (sequenceOf(after) + tail.asSequence()).joinToString("\n").trim()
}
