package com.example.gymtrack.assistant

/** Contexto del turno para Gemini. */
data class PibbleLlmContext(
    val mode: PibbleLlmMode,
    val destination: PibbleDestination? = null,
    val maxSentences: Int = PIBBLE_ASSISTANT_MAX_SENTENCES,
)

enum class PibbleLlmMode {
    ANSWER,
    PERSONA,
    /** Cómo llegar a una pantalla o función de la app. */
    NAVIGATION,
}
