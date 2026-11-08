package com.example.gymtrack.assistant

/** Recibe deltas de texto; al terminar [done] == true (último delta puede ser vacío). */
fun interface AssistantStreamListener {
    fun onChunk(partialDelta: String, done: Boolean)
}

/** Motor de inferencia de Pibble (API en la nube). */
interface AssistantInferenceEngine {
    /** Etiqueta breve para logs. */
    val backendId: String

    fun modelOnDisk(): Boolean
    fun isLoaded(): Boolean
    suspend fun load(): Result<Unit>
    fun close()
    fun cancelOngoingGeneration()
    fun resetSession()
    fun generateResponse(
        userText: String,
        listener: AssistantStreamListener,
        llmContext: PibbleLlmContext? = null,
    )
    suspend fun generateResponseSync(
        userText: String,
        minimalPrompt: Boolean,
        llmContext: PibbleLlmContext? = null,
    ): String
}
