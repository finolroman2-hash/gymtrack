package com.example.gymtrack.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtrack.data.cache.SessionDataCache
import com.example.gymtrack.data.local.dao.GymDao
import com.example.gymtrack.data.local.entities.SessionEntity
import com.example.gymtrack.data.local.entities.SessionSetPreviewRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SessionUi(val id: Long, val title: String, val rawName: String, val date: Long)

class SessionsViewModel(
    private val dao: GymDao,
    private val userName: String
) : ViewModel() {
    val sessions: StateFlow<List<SessionUi>> = dao.observeSessions(userName)
        .map { sessions ->
            sessions.map { session ->
                val dateText = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    .format(Date(session.dateTimestamp))
                SessionUi(
                    id = session.id,
                    title = "${session.customName} ($dateText)",
                    rawName = session.customName,
                    date = session.dateTimestamp
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2000), emptyList())

    fun addSession(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            dao.insertSession(
                SessionEntity(
                    userName = userName,
                    customName = name.trim(),
                    dateTimestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun renameSession(sessionId: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            val existing = sessions.value.firstOrNull { it.id == sessionId } ?: return@launch
            dao.updateSession(
                SessionEntity(
                    id = sessionId,
                    userName = userName,
                    customName = newName.trim(),
                    dateTimestamp = existing.date
                )
            )
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            dao.deleteSession(
                SessionEntity(
                    id = sessionId,
                    userName = userName,
                    customName = "",
                    dateTimestamp = 0L
                )
            )
        }
    }

    fun observePreview(sessionId: Long): StateFlow<List<SessionSetPreviewRow>> {
        return dao.observeSessionPreview(sessionId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2000), emptyList())
    }

    private val _previewId = MutableStateFlow<Long?>(null)
    val previewId: StateFlow<Long?> = _previewId.asStateFlow()

    fun togglePreview(sessionId: Long) {
        _previewId.value = if (_previewId.value == sessionId) null else sessionId
    }

    fun clearPreview() {
        _previewId.value = null
    }

    /**
     * Precarga real de la sesión antes de navegar: rellena el caché en memoria con las
     * series, la entidad de sesión y la lista de ejercicios del usuario, de modo que
     * cuando SessionDetailViewModel monte sus StateFlows ya tenga el valor inicial y
     * Compose pueda pintar contenido en el primer frame, sin la pausa típica.
     */
    fun preloadSession(sessionId: Long, onReady: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val sets = dao.fetchSetsOrderedForSession(sessionId)
                    SessionDataCache.putSets(sessionId, sets)
                }
                runCatching {
                    val session = dao.observeSession(sessionId).firstOrNull()
                    SessionDataCache.putSession(sessionId, session)
                }
                if (SessionDataCache.exercisesFor(userName).isEmpty()) {
                    runCatching {
                        val exs = dao.observeExercises(userName).firstOrNull().orEmpty()
                        SessionDataCache.putExercises(userName, exs)
                    }
                }
            }
            onReady()
        }
    }
}
