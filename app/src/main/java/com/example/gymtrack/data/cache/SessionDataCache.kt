package com.example.gymtrack.data.cache

import com.example.gymtrack.data.local.entities.ExerciseEntity
import com.example.gymtrack.data.local.entities.SessionEntity
import com.example.gymtrack.data.local.entities.SetEntity
import java.util.concurrent.ConcurrentHashMap

/**
 * Caché en proceso (solo memoria) que guarda la última instantánea conocida de los
 * datos por sesión. Su única finalidad es eliminar la pausa que aparece la primera
 * vez que se abre una sesión: el ViewModel usa el valor cacheado como valor inicial
 * de sus StateFlows, así Compose puede pintar contenido en el primer frame en vez
 * de mostrar la pantalla vacía mientras llega la primera emisión de Room.
 *
 * Se rellena durante el WarmupScreen y también justo antes de navegar a una sesión
 * (via SessionsViewModel.preloadSession). El cache se actualiza también con cada
 * emisión real de Room para reflejar cambios futuros.
 */
object SessionDataCache {
    private val sets = ConcurrentHashMap<Long, List<SetEntity>>()
    private val sessions = ConcurrentHashMap<Long, SessionEntity>()
    private val exercisesByUser = ConcurrentHashMap<String, List<ExerciseEntity>>()

    fun setsFor(sessionId: Long): List<SetEntity> = sets[sessionId].orEmpty()

    fun putSets(sessionId: Long, value: List<SetEntity>) {
        sets[sessionId] = value
    }

    fun sessionFor(sessionId: Long): SessionEntity? = sessions[sessionId]

    fun putSession(sessionId: Long, value: SessionEntity?) {
        if (value == null) sessions.remove(sessionId) else sessions[sessionId] = value
    }

    fun exercisesFor(userName: String): List<ExerciseEntity> = exercisesByUser[userName].orEmpty()

    fun putExercises(userName: String, value: List<ExerciseEntity>) {
        exercisesByUser[userName] = value
    }

    fun clearAll() {
        sets.clear()
        sessions.clear()
        exercisesByUser.clear()
    }
}
