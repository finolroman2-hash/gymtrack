package com.example.gymtrack.assistant

/** Prompts para Gemini API. */
fun buildCloudPrompt(userMessage: String, llmContext: PibbleLlmContext): Pair<String, String> {
    val system = PIBBLE_SYSTEM_PROMPT.trim()
    val maxSent = llmContext.maxSentences
    val modeHint = when (llmContext.mode) {
        PibbleLlmMode.ANSWER ->
            "Responde la duda de entrenamiento de forma clara y breve."
        PibbleLlmMode.PERSONA ->
            "Responde sobre ti (Pibble, bulldog francés, ayuda de GymTrack) con humor suave."
        PibbleLlmMode.NAVIGATION -> buildString {
            append("El usuario quiere ir a una pantalla de GymTrack. ")
            append("Explica en 2-3 frases dónde está, según esta ficha:\n")
            llmContext.destination?.let { append(PibbleAppNavigator.destinationHint(it)) }
            append("\nNo inventes menús. Debajo verá un botón para abrir esa pantalla; no hace falta que lo repitas mucho.")
        }
    }
    val user = buildString {
        append(modeHint).append("\n\nPregunta:\n")
        append(userMessage.trim())
        append(
            "\n\nFormato: respuesta breve (máximo $maxSent frases en total) + línea en blanco + " +
                "una frase final corta en primera persona como Pibble. Sin listas largas.",
        )
    }
    return system to user
}

fun maxOutputTokensForQuestion(question: String): Int = when (question.trim().length) {
    in 0..12 -> 320
    in 13..28 -> 400
    in 29..45 -> 480
    else -> 560
}
