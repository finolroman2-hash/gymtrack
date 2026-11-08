package com.example.gymtrack.assistant

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Motor de Pibble: **Gemini API** en la nube (internet obligatorio).
 */
suspend fun createAndLoadAssistantEngine(context: Context): Result<AssistantInferenceEngine> =
    withContext(Dispatchers.IO) {
        if (!PibbleNetwork.isOnline(context)) {
            return@withContext Result.failure(PibbleNoInternetException())
        }
        val engine = GeminiInferenceEngine(context.applicationContext)
        val lr = engine.load()
        if (lr.isSuccess) {
            Log.i(TAG, "Motor: Gemini + Groq (respaldo)")
            return@withContext Result.success(engine)
        }
        engine.close()
        Result.failure(lr.exceptionOrNull() ?: IllegalStateException("No se pudo preparar Gemini"))
    }

private const val TAG = "AssistantEngineFactory"
