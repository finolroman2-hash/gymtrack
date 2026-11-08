package com.example.gymtrack.assistant

import android.util.Log
import com.example.gymtrack.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal object OpenRouterRestClient {

    private const val TAG = "OpenRouterRestClient"
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun generateContent(
        systemPrompt: String,
        userPrompt: String,
        maxOutputTokens: Int = 500,
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.OPENROUTER_API_KEY.trim()
        if (apiKey.isEmpty()) {
            throw PibbleApiKeyMissingException("openrouter_key_missing")
        }

        val body = JSONObject().apply {
            put("model", PIBBLE_OPENROUTER_MODEL)
            put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", userPrompt)),
            )
            put("temperature", 0.25)
            put("top_p", 0.9)
            put("max_tokens", maxOutputTokens.coerceIn(128, 600))
        }

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("HTTP-Referer", "https://gymtrack.app")
            .addHeader("X-Title", "GymTrack")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val apiMessage = parseApiErrorMessage(raw)
                Log.e(TAG, "HTTP ${response.code}: $apiMessage")
                throw PibbleApiException(
                    message = apiMessage ?: "OpenRouter HTTP ${response.code}",
                    provider = PibbleLlmProvider.OPENROUTER,
                    httpCode = response.code,
                )
            }
            val text = parseTextFromResponse(raw)
            if (text.isBlank()) {
                throw PibbleApiException(
                    message = "OpenRouter no devolvió texto.",
                    provider = PibbleLlmProvider.OPENROUTER,
                )
            }
            text
        }
    }

    private fun parseApiErrorMessage(raw: String): String? = runCatching {
        JSONObject(raw).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun parseTextFromResponse(raw: String): String = runCatching {
        val choices = JSONObject(raw).optJSONArray("choices") ?: return ""
        if (choices.length() == 0) return ""
        choices.getJSONObject(0)
            .optJSONObject("message")
            ?.optString("content")
            .orEmpty()
            .trim()
    }.getOrElse { "" }
}
