package com.example.gymtrack.ui.voice

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Reconocimiento continuo en segundo plano (sin overlay de Google).
 *
 * Offline: usa [SpeechRecognizer.createOnDeviceSpeechRecognizer] cuando el sistema lo expone
 * (API 31+). El recognizer por defecto + EXTRA_PREFER_OFFLINE dejó de funcionar offline en
 * la app Google ≥11; por eso sin motor on-device el micrófono quedaba en bucle sin transcribir.
 */
class ContinuousSpeechRecognizer(
    private val context: Context,
    private val scope: CoroutineScope,
    /** Todas las hipótesis del STT ordenadas por confianza; el parser elige la mejor. */
    private val onResult: (List<String>) -> Unit,
    private val onListening: () -> Unit,
    private val onErrorMessage: (String) -> Unit,
) {
    /**
     * Vocabulario hacia el que sesgar el reconocimiento (nombres de ejercicios, "rir",
     * "placas"…). Solo surte efecto en el motor on-device (API 33+); en el resto se ignora.
     */
    var biasingStrings: List<String> = emptyList()

    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var paused = false
    private var useOnDeviceEngine = false
    private var preferOfflineMode = false
    private var restartGeneration = 0
    private var lastPartialText: String? = null
    private var consecutiveEmptyCycles = 0
    private var savedAudioMode = AudioManager.MODE_NORMAL
    private var savedSpeakerphone = false
    private var savedBluetoothSco = false
    private var audioRouteConfigured = false
    private var lastDeliveredText: String? = null
    private var lastDeliveredAtMs = 0L
    private var listeningUiNotified = false

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (!active || paused) return
            if (!listeningUiNotified) {
                listeningUiNotified = true
                onListening()
            }
        }

        override fun onBeginningOfSpeech() {
            lastPartialText = null
        }

        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            if (!active || paused) return
            Log.d(TAG, "onError code=$error partial=$lastPartialText")
            when (error) {
                SpeechRecognizer.ERROR_CLIENT -> {
                    if (!lastPartialText.isNullOrBlank()) {
                        deliverResult(listOf(lastPartialText!!))
                    }
                    scheduleRestart(delayMs = 600L)
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    active = false
                    onErrorMessage("Permiso de micrófono denegado")
                }
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> {
                    active = false
                    onErrorMessage("El reconocimiento offline no admite español en este dispositivo.")
                }
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                    active = false
                    onErrorMessage(
                        "El idioma español offline no está listo. " +
                            "Abre Ajustes → Google → Gestión de apps → Descargas de voz y espera a que termine.",
                    )
                }
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER -> {
                    if (useOnDeviceEngine) {
                        scheduleRestart(delayMs = 800L)
                    } else {
                        active = false
                        onErrorMessage("Sin conexión: conecta a internet o usa un dispositivo con reconocimiento offline del sistema.")
                    }
                }
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    if (!lastPartialText.isNullOrBlank()) {
                        deliverResult(listOf(lastPartialText!!))
                        scheduleRestart(delayMs = 700L)
                        return
                    }
                    consecutiveEmptyCycles++
                    if (consecutiveEmptyCycles >= 12) {
                        active = false
                        onErrorMessage(
                            if (useOnDeviceEngine) {
                                "No se detecta voz. Habla cerca del micrófono con frases cortas."
                            } else {
                                "No se detecta voz. Revisa el micrófono o la conexión."
                            },
                        )
                        return
                    }
                    scheduleRestart(delayMs = 500L)
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> scheduleRestart(delayMs = 900L)
                else -> scheduleRestart(delayMs = 700L)
            }
        }

        override fun onResults(results: Bundle?) {
            if (!active || paused) return
            val alternatives = extractAlternatives(results)
            if (alternatives.isNotEmpty()) {
                deliverResult(alternatives)
            } else if (!lastPartialText.isNullOrBlank()) {
                deliverResult(listOf(lastPartialText!!))
            }
            scheduleRestart(delayMs = RESULT_RESTART_DELAY_MS)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!active || paused) return
            extractText(partialResults)?.let { partial ->
                if (partial.length >= 2) {
                    lastPartialText = partial
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    fun start(offlinePreferred: Boolean = false) {
        val onDeviceAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

        if (offlinePreferred && !onDeviceAvailable) {
            onErrorMessage(
                "Reconocimiento offline no disponible en este móvil. " +
                    "Necesitas Android 12+ con motor local del sistema, o conexión a internet.",
            )
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context) && !onDeviceAvailable) {
            onErrorMessage("Reconocimiento de voz no disponible")
            return
        }

        useOnDeviceEngine = offlinePreferred && onDeviceAvailable
        preferOfflineMode = offlinePreferred
        consecutiveEmptyCycles = 0
        lastPartialText = null
        paused = false
        listeningUiNotified = false
        active = true
        configureBuiltInMicRoute()
        recreateRecognizer()
        beginListening()
    }

    fun stop() {
        active = false
        paused = false
        restartGeneration++
        consecutiveEmptyCycles = 0
        lastPartialText = null
        listeningUiNotified = false
        recognizer?.stopListening()
        recognizer?.cancel()
        restoreBuiltInMicRoute()
    }

    /** Pausa sin desactivar el micrófono (p. ej. mientras se procesa la frase o hay diálogo de elección). */
    fun pause() {
        if (!active) return
        paused = true
        restartGeneration++
        lastPartialText = null
        listeningUiNotified = false
        runCatching {
            recognizer?.stopListening()
            recognizer?.cancel()
        }
    }

    /** Reanuda escucha si el micrófono sigue activo y no está pausado por el usuario. */
    fun resume() {
        if (!active || !paused) return
        paused = false
        consecutiveEmptyCycles = 0
        listeningUiNotified = false
        beginListening()
    }

    fun destroy() {
        stop()
        recognizer?.destroy()
        recognizer = null
    }

    /** Fuerza micrófono del dispositivo (altavoz); evita BT / auriculares con cable. */
    private fun configureBuiltInMicRoute() {
        if (audioRouteConfigured) return
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        savedAudioMode = am.mode
        savedSpeakerphone = am.isSpeakerphoneOn
        savedBluetoothSco = am.isBluetoothScoOn
        am.stopBluetoothSco()
        am.isBluetoothScoOn = false
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        am.isSpeakerphoneOn = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                am.clearCommunicationDevice()
                val builtIn = am.availableCommunicationDevices.firstOrNull { device ->
                    device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC
                }
                builtIn?.let { am.setCommunicationDevice(it) }
            }
        }
        audioRouteConfigured = true
    }

    private fun restoreBuiltInMicRoute() {
        if (!audioRouteConfigured) return
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { am.clearCommunicationDevice() }
        }
        am.stopBluetoothSco()
        am.isBluetoothScoOn = savedBluetoothSco
        am.isSpeakerphoneOn = savedSpeakerphone
        am.mode = savedAudioMode
        audioRouteConfigured = false
    }

    private fun recreateRecognizer() {
        recognizer?.destroy()
        recognizer = null
        recognizer = if (useOnDeviceEngine && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }.also {
            it.setRecognitionListener(recognitionListener)
        }
    }

    private fun buildRecognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, preferredSpanishTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L)
            if (!useOnDeviceEngine && preferOfflineMode) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && biasingStrings.isNotEmpty()) {
                putStringArrayListExtra(
                    RecognizerIntent.EXTRA_BIASING_STRINGS,
                    ArrayList(biasingStrings.distinct().take(MAX_BIASING_STRINGS)),
                )
            }
        }

    /** Variante de español del dispositivo (es-MX, es-CL, …) si existe; mejora acentos locales. */
    private fun preferredSpanishTag(): String {
        val def = java.util.Locale.getDefault()
        return if (def.language == "es") def.toLanguageTag() else "es-ES"
    }

    private fun beginListening() {
        if (!active || paused) return
        val intent = buildRecognizerIntent()
        runCatching {
            recognizer?.cancel()
            recognizer?.startListening(intent)
        }.onFailure { e ->
            Log.e(TAG, "startListening failed", e)
            active = false
            onErrorMessage("No se pudo iniciar el micrófono")
        }
    }

    private fun deliverResult(alternatives: List<String>) {
        val cleaned = alternatives.map { it.trim() }.filter { it.length >= 2 }
        val primary = cleaned.firstOrNull() ?: return
        val now = SystemClock.elapsedRealtime()
        if (primary.equals(lastDeliveredText, ignoreCase = true) &&
            now - lastDeliveredAtMs < DUPLICATE_RESULT_WINDOW_MS
        ) {
            return
        }
        lastDeliveredText = primary
        lastDeliveredAtMs = now
        lastPartialText = null
        consecutiveEmptyCycles = 0
        onResult(cleaned)
    }

    private fun extractAlternatives(results: Bundle?): List<String> =
        results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.mapNotNull { it?.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    private fun extractText(results: Bundle?): String? =
        extractAlternatives(results).firstOrNull()

    private fun scheduleRestart(delayMs: Long) {
        if (!active || paused) return
        val generation = ++restartGeneration
        scope.launch {
            delay(delayMs)
            if (!active || paused || generation != restartGeneration) return@launch
            beginListening()
        }
    }

    companion object {
        private const val TAG = "ContinuousSpeech"
        private const val RESULT_RESTART_DELAY_MS = 900L
        private const val DUPLICATE_RESULT_WINDOW_MS = 2_500L
        private const val MAX_BIASING_STRINGS = 200
    }
}
