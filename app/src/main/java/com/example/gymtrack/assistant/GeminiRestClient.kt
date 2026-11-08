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

internal object GeminiRestClient {

    private const val TAG = "GeminiRestClient"
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
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        if (apiKey.isEmpty()) {
            throw PibbleApiKeyMissingException("gemini_key_missing")
        }

        val body = JSONObject().apply {
            put(
                "system_instruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemPrompt)),
                ),
            )
            put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", userPrompt)),
                        ),
                ),
            )
            put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.25)
                    .put("topP", 0.9)
                    .put("maxOutputTokens", maxOutputTokens.coerceIn(128, 600)),
            )
        }

        val url =
            "https://generativelanguage.googleapis.com/v1beta/models/" +
                "${PIBBLE_GEMINI_MODEL}:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val apiMessage = parseApiErrorMessage(raw)
                Log.e(TAG, "HTTP ${response.code}: $apiMessage")
                throw PibbleApiException(
                    message = apiMessage ?: "Gemini HTTP ${response.code}",
                    provider = PibbleLlmProvider.GEMINI,
                    httpCode = response.code,
                )
            }
            val text = parseTextFromResponse(raw)
            if (text.isBlank()) {
                throw PibbleApiException(
                    message = "Gemini no devolvió texto.",
                    provider = PibbleLlmProvider.GEMINI,
                )
            }
            text
        }
    }

    private fun parseApiErrorMessage(raw: String): String? = runCatching {
        JSONObject(raw).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun parseTextFromResponse(raw: String): String {
        val root = JSONObject(raw)
        val candidates = root.optJSONArray("candidates") ?: return ""
        if (candidates.length() == 0) return ""
        val parts = candidates.getJSONObject(0)
            .optJSONObject("content")
            ?.optJSONArray("parts") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val t = parts.optJSONObject(i)?.optString("text").orEmpty()
            if (t.isNotBlank()) sb.append(t)
        }
        return sb.toString().trim()
    }
}
