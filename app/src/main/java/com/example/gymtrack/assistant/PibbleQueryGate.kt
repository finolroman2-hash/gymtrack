package com.example.gymtrack.assistant

import android.content.Context
import com.example.gymtrack.R
import java.text.Normalizer
import java.util.Locale

sealed class PibbleResolution {
    data class Immediate(val text: String, val navAction: PibbleNavAction? = null) : PibbleResolution()
    data class InvokeLlm(
        val userQuestion: String,
        val llmContext: PibbleLlmContext,
        val navAction: PibbleNavAction? = null,
    ) : PibbleResolution()
}

private const val MSG_OUT_OF_SCOPE =
    "Solo puedo ayudar con dudas de entrenamiento, guiarte en la app o contarte cosas sobre mí como Pibble. " +
        "No diseño rutinas personalizadas ni doy consejo médico."

private const val MSG_PERSONA_OPINION_BLOCKED =
    "Sobre temas ajenos al gym no doy opiniones. Pregúntame por entrenamiento, la app o sobre mí como bulldog francés."

private const val MSG_PIBBLE_IDENTITY =
    "Sí, soy Pibble: bulldog francés y la ayuda de GymTrack. A veces me dicen «pib» en el gym. " +
        "Explico conceptos de fuerza y te guío en la app; no diseño rutinas ni doy consejo médico.\n\n" +
        "Yo, Pibble: mis orejas de murciélago no mienten — aquí estoy para tus dudas."

private const val MSG_RIR_DEFINITION =
    "RIR son las Repeticiones en Reserva: cuántas repeticiones más podrías hacer en una serie " +
        "con buena técnica antes de llegar al fallo muscular. RIR 0 ≈ al fallo; RIR 2 ≈ te quedaban unas 2 reps en el tanque.\n\n" +
        "Yo, Pibble: lo uso para no ir siempre al fallo — hasta un bulldog dosifica el esfuerzo."

private val POLITICS_RELIGION_POLEMIC_RE = Regex(
    pattern = """\b(politica|política|elecciones|presidente|congreso|religion|religión|biblia|iglesia|ateo|aborto|guerra\s+en|conflicto\s+armado)\b""",
)

private val MEDICAL_RE = Regex(
    pattern = """\b(medicina|médico|medico|doctor|hospital|clinica|clínica|diagnostico|diagnóstico|pastilla|medicamento|farmaco|fármaco|lesion|lesión|esguince|rotura|tendinitis|artritis|enfermedad|covid|gripe|dolor\s+de\s+cabeza|duele\s+el\s+corazon|corazón)\b""",
)

private val TRAINING_COACHING_RE = Regex(
    pattern = """\b(mi\s+rutina|programa(?:ción|cion)?\s+de\s+entren|metod(?:o|os)|microcarga|undulating|smolov|wendler|5x5|full\s*body|push\s*pull|split|superserie|biserie|triserie|dropset|myorep|rest[\s-]*pause|piramide|pirámide|cuantas\s+series|cuántas\s+series|cuantas\s+sesiones|cuántas\s+sesiones|cuanto\s+peso|cuánto\s+peso|descanso\s+entre\s+series|progresion\s+lineal|doble\s+progresion|como\s+organizo\s+mi\s+semana|organizar\s+mi\s+rutina|me\s+recomiendas|opinas\s+de\s+mi|estoy\s+bien\s+si|lo\s+hago\s+bien|que\s+ejercicio|qué\s+ejercicio|cuantos\s+dias|cuántos\s+días|para\s+mi\s+caso)\b""",
)

private val OFF_TOPIC_RE = Regex(
    pattern = """\b(futbol|fútbol|bitcoin|nft|clima\s+de|serie\s+de\s+netflix|videojuego|chisme|receta\s+de\s+cocina)\b""",
)

private val SIMPLE_PIBBLE_IDENTITY_RE = Regex(
    pattern = """\b(eres|quien|quién)\b.*\b(pibble|bulldog)\b|\b(pibble|bulldog)\b.*\b(eres|quien|quién)\b""",
)

private val RIR_TERM_RE = Regex(
    pattern = """\b(que\s+es\s+(el\s+)?rir|que\s+significa\s+(el\s+)?rir|significa\s+rir|rir\s+significa|definicion\s+de\s+rir|definición\s+de\s+rir|repeticiones\s+en\s+reserva)\b""",
)

private val PERSONA_RE = Regex(
    pattern = """\b(pibble|bulldog|bulldogs|bulldog\s+frances|bulldog\s+francés|bulldog\s+franceses|que\s+raza|qué\s+raza|eres\s+un|eres\s+una|quien\s+eres|quién\s+eres|como\s+te\s+llamas|cómo\s+te\s+llamas|presentate|preséntate|personalidad|te\s+gusta|que\s+haces|qué\s+haces|cuentame\s+de\s+ti|cuéntame\s+de\s+ti|dato\s+curioso|datos\s+curiosos|nadas|nadar|ladras|ladrar)\b""",
)

private val EXTERNAL_OPINION_RE = Regex(
    pattern = """\b(opinas|piensas|crees|tu\s+opinion|tu\s+opinión|que\s+piensas|qué\s+piensas|mejor\s+que|peor\s+que|deberia|debería|votar|partido\s+politico|partido\s+político)\b""",
)

fun resolvePibbleInput(raw: String, context: Context): PibbleResolution {
    val n = normalizePibbleText(raw)
    val question = raw.trim().take(PIBBLE_USER_INPUT_MAX_CHARS)

    if (n.length < 2) {
        return PibbleResolution.Immediate(context.getString(R.string.pibble_query_rejected))
    }

    if (POLITICS_RELIGION_POLEMIC_RE.containsMatchIn(n) ||
        OFF_TOPIC_RE.containsMatchIn(n) ||
        MEDICAL_RE.containsMatchIn(n) ||
        TRAINING_COACHING_RE.containsMatchIn(n)
    ) {
        return PibbleResolution.Immediate(MSG_OUT_OF_SCOPE)
    }

    if (SIMPLE_PIBBLE_IDENTITY_RE.containsMatchIn(n)) {
        return PibbleResolution.Immediate(MSG_PIBBLE_IDENTITY)
    }

    if (RIR_TERM_RE.containsMatchIn(n)) {
        return PibbleResolution.Immediate(MSG_RIR_DEFINITION)
    }

    val navDest = PibbleAppNavigator.match(n)
    val navAction = navDest?.let { PibbleNavAction(it) }

    if (navDest != null) {
        return PibbleResolution.InvokeLlm(
            userQuestion = question,
            llmContext = PibbleLlmContext(
                mode = PibbleLlmMode.NAVIGATION,
                destination = navDest,
                maxSentences = maxSentencesForQuestion(question).coerceAtMost(4),
            ),
            navAction = navAction,
        )
    }

    if (PERSONA_RE.containsMatchIn(n)) {
        if (EXTERNAL_OPINION_RE.containsMatchIn(n) &&
            !n.contains("bulldog") &&
            !n.contains("pibble") &&
            !n.contains("raza")
        ) {
            return PibbleResolution.Immediate(MSG_PERSONA_OPINION_BLOCKED)
        }
        return PibbleResolution.InvokeLlm(
            userQuestion = question,
            llmContext = PibbleLlmContext(
                mode = PibbleLlmMode.PERSONA,
                maxSentences = maxSentencesForQuestion(question),
            ),
        )
    }

    return PibbleResolution.InvokeLlm(
        userQuestion = question,
        llmContext = PibbleLlmContext(
            mode = PibbleLlmMode.ANSWER,
            maxSentences = maxSentencesForQuestion(question),
        ),
    )
}

fun maxSentencesForQuestion(question: String): Int {
    val len = question.trim().length
    return when {
        len <= 12 -> 3
        len <= 28 -> 4
        len <= 45 -> 5
        else -> 6
    }
}

internal fun normalizePibbleText(s: String): String {
    val trimmed = s.trim().lowercase(Locale.ROOT)
    val decomposed = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
    return decomposed
        .replace('¿', ' ')
        .replace('¡', ' ')
        .replace(Regex("[^a-z0-9 .,;:\\-]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
