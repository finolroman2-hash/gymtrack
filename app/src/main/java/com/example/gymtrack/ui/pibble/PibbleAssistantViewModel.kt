package com.example.gymtrack.ui.pibble

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.gymtrack.R
import com.example.gymtrack.assistant.AssistantInferenceEngine
import com.example.gymtrack.assistant.AssistantStreamListener
import com.example.gymtrack.assistant.PIBBLE_USER_INPUT_MAX_CHARS
import com.example.gymtrack.assistant.PibbleNavAction
import com.example.gymtrack.assistant.PIBBLE_CLOUD_GENERATION_TIMEOUT_MS
import com.example.gymtrack.assistant.GeminiInferenceEngine
import com.example.gymtrack.assistant.PibbleAllProvidersExhaustedException
import com.example.gymtrack.assistant.PibbleApiException
import com.example.gymtrack.assistant.PibbleApiKeyMissingException
import com.example.gymtrack.assistant.PibbleLlmContext
import com.example.gymtrack.assistant.PibbleNetwork
import com.example.gymtrack.assistant.PibbleNoInternetException
import com.example.gymtrack.assistant.PibbleResolution
import com.example.gymtrack.assistant.clipPibbleOutput
import com.example.gymtrack.assistant.createAndLoadAssistantEngine
import com.example.gymtrack.assistant.resolvePibbleInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger

class PibbleAssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val processLifecycleObserver = LifecycleEventObserver { _, event ->
        if (event != Lifecycle.Event.ON_STOP) return@LifecycleEventObserver
        val st = _pibbleState.value
        if (st !is PibbleAssistantState.Thinking) return@LifecycleEventObserver
        generationEpoch.incrementAndGet()
        inferenceJob?.cancel()
        inferenceJob = null
        engine?.cancelOngoingGeneration()
        mainHandler.post {
            val now = _pibbleState.value
            if (now is PibbleAssistantState.Thinking) {
                _pibbleState.value = PibbleAssistantState.Error(
                    getApplication<Application>().getString(R.string.pibble_paused_background),
                )
            }
        }
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val engineMutex = Mutex()
    private val inferenceDispatcher = Dispatchers.Default.limitedParallelism(1)

    private val _pibbleState = MutableStateFlow<PibbleAssistantState>(PibbleAssistantState.Idle)
    val pibbleState: StateFlow<PibbleAssistantState> = _pibbleState.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var engine: AssistantInferenceEngine? = null
    private var inferenceJob: Job? = null
    private val generationEpoch = AtomicInteger(0)

    suspend fun prepareModel(): Result<Unit> = engineMutex.withLock {
        prepareModelLocked()
    }

    private suspend fun prepareModelLocked(): Result<Unit> = withContext(inferenceDispatcher) {
        try {
            val app = getApplication<Application>()
            val r = createAndLoadAssistantEngine(app)
            if (r.isSuccess) {
                engine = r.getOrNull()
                _ready.value = true
                Result.success(Unit)
            } else {
                Result.failure(
                    Exception(
                        r.exceptionOrNull()?.let { inferenceFailureMessage(it) }
                            ?: app.getString(R.string.pibble_response_failed_generic),
                    ),
                )
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Result.failure(Exception(inferenceFailureMessage(t)))
        }
    }

    private fun inferenceFailureMessage(t: Throwable): String {
        val app = getApplication<Application>()
        return when (t) {
            is PibbleNoInternetException -> app.getString(R.string.pibble_no_internet)
            is PibbleAllProvidersExhaustedException -> app.getString(R.string.pibble_all_providers_sleep)
            is PibbleApiKeyMissingException -> app.getString(R.string.pibble_api_key_missing)
            is PibbleApiException -> t.message ?: app.getString(R.string.pibble_response_failed_generic)
            else -> t.message?.takeIf { it.isNotBlank() }
                ?: app.getString(R.string.pibble_response_failed_generic)
        }
    }

    private fun apiErrorFromEngine(eng: AssistantInferenceEngine): Throwable? =
        (eng as? GeminiInferenceEngine)?.consumeLastError()

    fun releaseModelSync() {
        generationEpoch.incrementAndGet()
        inferenceJob?.cancel()
        inferenceJob = null
        engine?.cancelOngoingGeneration()
        engine?.close()
        engine = null
        _ready.value = false
        _pibbleState.value = PibbleAssistantState.Idle
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processLifecycleObserver)
        generationEpoch.incrementAndGet()
        inferenceJob?.cancel()
        engine?.cancelOngoingGeneration()
        engine?.close()
        engine = null
        super.onCleared()
    }

    fun submitQuery(raw: String) {
        val q = raw.trim().take(PIBBLE_USER_INPUT_MAX_CHARS)
        if (q.isEmpty()) return
        val state = _pibbleState.value
        if (state is PibbleAssistantState.Thinking) return

        val app = getApplication<Application>()
        if (!PibbleNetwork.isOnline(app)) {
            _pibbleState.value = PibbleAssistantState.Error(app.getString(R.string.pibble_no_internet))
            return
        }

        when (val gate = resolvePibbleInput(q, app)) {
            is PibbleResolution.Immediate -> {
                _pibbleState.value = PibbleAssistantState.Success(
                    text = gate.text,
                    navAction = gate.navAction,
                )
            }
            is PibbleResolution.InvokeLlm -> {
                val eng = engine ?: run {
                    _pibbleState.value = PibbleAssistantState.Error(
                        getApplication<Application>().getString(R.string.pibble_model_not_ready),
                    )
                    return
                }
                runLlmGeneration(eng, gate.userQuestion, gate.llmContext, gate.navAction)
            }
        }
    }

    private fun runLlmGeneration(
        eng: AssistantInferenceEngine,
        question: String,
        llmContext: PibbleLlmContext,
        navAction: PibbleNavAction?,
    ) {
        eng.cancelOngoingGeneration()
        inferenceJob?.cancel()
        generationEpoch.incrementAndGet()
        val epochAtStart = generationEpoch.get()
        inferenceJob = viewModelScope.launch(inferenceDispatcher) {
            fun isStale(): Boolean = generationEpoch.get() != epochAtStart

            withContext(Dispatchers.Main.immediate) {
                if (!isStale()) _pibbleState.value = PibbleAssistantState.Thinking
            }

            val sb = StringBuilder()
            val finished = CompletableDeferred<Unit>()
            val maxSent = llmContext.maxSentences

            try {
                eng.generateResponse(
                    question,
                    AssistantStreamListener { partial, done ->
                        if (isStale()) {
                            if (done) finished.complete(Unit)
                            return@AssistantStreamListener
                        }
                        if (partial.isNotEmpty()) sb.append(partial)
                        if (done) finished.complete(Unit)
                    },
                    llmContext,
                )
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                mainHandler.post {
                    if (!isStale()) _pibbleState.value = PibbleAssistantState.Error(inferenceFailureMessage(t))
                }
                finished.complete(Unit)
            }

            try {
                withTimeout(LLM_WAIT_MS) { finished.await() }
                if (!isStale()) {
                    val apiErr = apiErrorFromEngine(eng)
                    if (apiErr != null) {
                        withContext(Dispatchers.Main) {
                            if (!isStale()) {
                                _pibbleState.value = PibbleAssistantState.Error(inferenceFailureMessage(apiErr))
                            }
                        }
                        return@launch
                    }
                    var finalText = clipPibbleOutput(sb.toString(), question, maxSent)
                    if (finalText.isBlank()) {
                        try {
                            var recovered = eng.generateResponseSync(question, minimalPrompt = false, llmContext)
                            val syncErr = apiErrorFromEngine(eng)
                            if (syncErr != null) {
                                withContext(Dispatchers.Main) {
                                    if (!isStale()) {
                                        _pibbleState.value = PibbleAssistantState.Error(inferenceFailureMessage(syncErr))
                                    }
                                }
                                return@launch
                            }
                            finalText = clipPibbleOutput(recovered, question, maxSent)
                        } catch (e: Exception) {
                            Log.w(TAG, "respaldo sync", e)
                            withContext(Dispatchers.Main) {
                                if (!isStale()) {
                                    _pibbleState.value = PibbleAssistantState.Error(inferenceFailureMessage(e))
                                }
                            }
                            return@launch
                        }
                    }
                    val out = finalText
                    withContext(Dispatchers.Main) {
                        if (!isStale()) {
                            _pibbleState.value = if (out.isBlank()) {
                                PibbleAssistantState.Error(
                                    getApplication<Application>().getString(R.string.pibble_empty_response),
                                )
                            } else {
                                PibbleAssistantState.Success(text = out, navAction = navAction)
                            }
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                mainHandler.post {
                    if (!isStale() && _pibbleState.value is PibbleAssistantState.Thinking) {
                        _pibbleState.value = PibbleAssistantState.Error(
                            getApplication<Application>().getString(R.string.pibble_timeout_response),
                        )
                    }
                }
            } catch (e: CancellationException) {
                finished.complete(Unit)
                throw e
            }
        }.also { job ->
            job.invokeOnCompletion { eng.cancelOngoingGeneration() }
        }
    }

    companion object {
        private const val TAG = "PibbleAssistantVM"
        private const val LLM_WAIT_MS = PIBBLE_CLOUD_GENERATION_TIMEOUT_MS
    }
}
