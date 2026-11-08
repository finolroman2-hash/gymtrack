package com.example.gymtrack.assistant

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * Inferencia en la nube: **Gemini** con respaldo **Groq** (rotación automática).
 */
class GeminiInferenceEngine(private val appContext: Context) : AssistantInferenceEngine {

    override val backendId: String = "cloud_llm_rotator"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var generateJob: Job? = null
    private val lastError = AtomicReference<Throwable?>(null)

    @Volatile
    private var ready: Boolean = false

    fun consumeLastError(): Throwable? = lastError.getAndSet(null)

    override fun modelOnDisk(): Boolean = PibbleLlmRotator.hasAnyProviderConfigured()

    override fun isLoaded(): Boolean = ready

    override suspend fun load(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!PibbleNetwork.isOnline(appContext)) {
            return@withContext Result.failure(PibbleNoInternetException())
        }
        if (!PibbleLlmRotator.hasAnyProviderConfigured()) {
            return@withContext Result.failure(PibbleApiKeyMissingException())
        }
        ready = true
        Result.success(Unit)
    }

    override fun close() {
        cancelOngoingGeneration()
        ready = false
        scope.cancel()
    }

    override fun cancelOngoingGeneration() {
        generateJob?.cancel()
        generateJob = null
    }

    override fun resetSession() {
        cancelOngoingGeneration()
    }

    override fun generateResponse(
        userText: String,
        listener: AssistantStreamListener,
        llmContext: PibbleLlmContext?,
    ) {
        resetSession()
        lastError.set(null)
        generateJob = scope.launch {
            try {
                val text = requestText(userText, llmContext)
                listener.onChunk(text, true)
            } catch (e: CancellationException) {
                listener.onChunk("", true)
                throw e
            } catch (e: Throwable) {
                lastError.set(e)
                Log.e(TAG, "generateResponse", e)
                listener.onChunk("", true)
            }
        }
    }

    override suspend fun generateResponseSync(
        userText: String,
        minimalPrompt: Boolean,
        llmContext: PibbleLlmContext?,
    ): String = withContext(Dispatchers.IO) {
        lastError.set(null)
        try {
            requestText(userText, llmContext)
        } catch (e: Throwable) {
            lastError.set(e)
            ""
        }
    }

    private suspend fun requestText(userText: String, llmContext: PibbleLlmContext?): String {
        if (!PibbleNetwork.isOnline(appContext)) {
            throw PibbleNoInternetException()
        }
        val ctx = llmContext ?: PibbleLlmContext(mode = PibbleLlmMode.ANSWER)
        val (system, user) = buildCloudPrompt(userText, ctx)
        return PibbleLlmRotator.generateContent(
            systemPrompt = system,
            userPrompt = user,
            maxOutputTokens = maxOutputTokensForQuestion(userText),
        )
    }

    companion object {
        private const val TAG = "CloudLlmEngine"
    }
}
