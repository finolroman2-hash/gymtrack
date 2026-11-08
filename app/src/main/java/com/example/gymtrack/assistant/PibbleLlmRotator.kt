package com.example.gymtrack.assistant

import android.util.Log
import com.example.gymtrack.BuildConfig

enum class PibbleLlmProvider {
    GEMINI,
    GROQ,
    OPENROUTER,
}

/**
 * Agotados todos los proveedores en la nube: mensaje con personalidad Pibble.
 */
class PibbleAllProvidersExhaustedException :
    Exception("all_providers_exhausted")

class PibbleApiException(
    message: String,
    val provider: PibbleLlmProvider? = null,
    val httpCode: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

class PibbleApiKeyMissingException(
    private val reason: String = "api_key_missing",
) : Exception(reason)

internal object PibbleLlmRotator {

    private const val TAG = "PibbleLlmRotator"

    @Volatile
    private var preferred: PibbleLlmProvider = PibbleLlmProvider.GEMINI

    fun hasAnyProviderConfigured(): Boolean =
        BuildConfig.GEMINI_API_KEY.isNotBlank() ||
            BuildConfig.GROQ_API_KEY.isNotBlank() ||
            BuildConfig.OPENROUTER_API_KEY.isNotBlank()

    suspend fun generateContent(
        systemPrompt: String,
        userPrompt: String,
        maxOutputTokens: Int,
    ): String {
        val providers = orderedProviders()
        if (providers.isEmpty()) throw PibbleApiKeyMissingException()

        var lastError: Throwable? = null
        for (provider in providers) {
            if (!isConfigured(provider)) continue
            try {
                val text = callProvider(provider, systemPrompt, userPrompt, maxOutputTokens)
                preferred = provider
                Log.i(TAG, "Respuesta OK con $provider")
                return text
            } catch (e: Throwable) {
                lastError = e
                if (!shouldFailover(e)) throw e
                Log.w(TAG, "$provider falló, probando otro…", e)
            }
        }
        Log.e(TAG, "Sin proveedores disponibles", lastError)
        throw PibbleAllProvidersExhaustedException()
    }

    private fun orderedProviders(): List<PibbleLlmProvider> {
        val all = PibbleLlmProvider.entries
        return listOf(preferred) + all.filter { it != preferred }
    }

    private fun isConfigured(provider: PibbleLlmProvider): Boolean = when (provider) {
        PibbleLlmProvider.GEMINI -> BuildConfig.GEMINI_API_KEY.isNotBlank()
        PibbleLlmProvider.GROQ -> BuildConfig.GROQ_API_KEY.isNotBlank()
        PibbleLlmProvider.OPENROUTER -> BuildConfig.OPENROUTER_API_KEY.isNotBlank()
    }

    private suspend fun callProvider(
        provider: PibbleLlmProvider,
        systemPrompt: String,
        userPrompt: String,
        maxOutputTokens: Int,
    ): String = when (provider) {
        PibbleLlmProvider.GEMINI -> GeminiRestClient.generateContent(systemPrompt, userPrompt, maxOutputTokens)
        PibbleLlmProvider.GROQ -> GroqRestClient.generateContent(systemPrompt, userPrompt, maxOutputTokens)
        PibbleLlmProvider.OPENROUTER -> OpenRouterRestClient.generateContent(systemPrompt, userPrompt, maxOutputTokens)
    }

    private fun shouldFailover(error: Throwable): Boolean {
        if (error is PibbleAllProvidersExhaustedException) return false
        if (error is PibbleNoInternetException) return false
        if (error is PibbleApiKeyMissingException) return true
        if (error is PibbleApiException) {
            val code = error.httpCode
            if (code == 429 || code == 503 || code == 502 || code == 500) return true
            val low = error.message?.lowercase().orEmpty()
            return low.contains("quota") ||
                low.contains("rate") ||
                low.contains("limit") ||
                low.contains("capacity") ||
                low.contains("unavailable") ||
                low.contains("overloaded")
        }
        return true
    }
}
