package com.example.gymtrack.assistant

/**
 * Destinos de GymTrack que Pibble puede abrir con un botón genérico.
 */
enum class PibbleDestination {
    HOME_PRINCIPAL,
    HOME_STATS,
    ADD_EXERCISE,
    SETTINGS,
    TIMER_OVERLAY,
}

data class PibbleNavAction(
    val destination: PibbleDestination,
    val buttonLabel: String = "Haz clic aquí",
)

object PibbleAppNavigator {

    private const val MIN_SCORE = 4

    fun match(normalizedQuery: String): PibbleDestination? {
        val q = normalizedQuery.trim()
        if (q.length < 3) return null

        val scores = LinkedHashMap<PibbleDestination, Int>()
        fun add(dest: PibbleDestination, points: Int) {
            scores[dest] = (scores[dest] ?: 0) + points
        }

        val navIntent = NAV_INTENT_RE.containsMatchIn(q)

        if (matchesAddExercise(q, navIntent)) add(PibbleDestination.ADD_EXERCISE, 12)
        if (containsAny(q, STATS_KEYS)) add(PibbleDestination.HOME_STATS, 10)
        if (containsAny(q, SETTINGS_KEYS)) add(PibbleDestination.SETTINGS, 10)
        if (containsAny(q, TIMER_KEYS)) add(PibbleDestination.TIMER_OVERLAY, 10)
        if (containsAny(q, PRINCIPAL_KEYS)) add(PibbleDestination.HOME_PRINCIPAL, 8)

        val best = scores.maxByOrNull { it.value } ?: return null
        if (best.value < MIN_SCORE) return null

        val top = best.value
        val tied = scores.filter { it.value == top }
        if (tied.size > 1) return resolveTie(q, tied.keys)
        return best.key
    }

    private fun matchesAddExercise(q: String, navIntent: Boolean): Boolean {
        if (!q.contains("ejercicio")) return false
        return containsAny(q, ADD_EXERCISE_VERBS) ||
            q.contains("boton") ||
            (navIntent && !containsAny(q, STATS_KEYS) && !containsAny(q, SETTINGS_KEYS))
    }

    private fun resolveTie(q: String, candidates: Set<PibbleDestination>): PibbleDestination? {
        val priority = listOf(
            PibbleDestination.ADD_EXERCISE,
            PibbleDestination.HOME_STATS,
            PibbleDestination.SETTINGS,
            PibbleDestination.TIMER_OVERLAY,
            PibbleDestination.HOME_PRINCIPAL,
        )
        return priority.firstOrNull { it in candidates }
    }

    private fun containsAny(q: String, keys: List<String>): Boolean =
        keys.any { q.contains(it) }

    fun destinationHint(dest: PibbleDestination): String = when (dest) {
        PibbleDestination.HOME_PRINCIPAL ->
            "Pantalla Principal: pestaña con la lista de sesiones de entrenamiento."
        PibbleDestination.HOME_STATS ->
            "Pantalla Estadísticas: segunda pestaña del inicio, con gráficos de progreso."
        PibbleDestination.ADD_EXERCISE ->
            "Agregar ejercicio: botón «Añadir ejercicio» en la parte superior del inicio."
        PibbleDestination.SETTINGS ->
            "Ajustes: botón «Ajustes» en el inicio; ahí está el temporizador, tema oscuro y la ayuda."
        PibbleDestination.TIMER_OVERLAY ->
            "Temporizador flotante: botón «Temporizador» en el inicio (puede pedir permiso de superposición)."
    }

    private val NAV_INTENT_RE = Regex(
        """\b(quiero|quisiera|necesito|deseo|podrias|podrías|me\s+llevas|llevarme|ir|entrar|entro|abrir|abre|muestrame|muéstrame|mostrar|ver|usar|acceder|vamos|vamonos|donde|dónde|como\s+llego|como\s+entro|cómo\s+llego|cómo\s+entro|ubicar|encuentro|esta|está|estan|están|lleva|llevar)\b""",
    )

    private val STATS_KEYS = listOf(
        "estadist", "estadisticas", "grafic", "graficos", "grafico", "progreso", "dashboard", "evolucion", "chart",
    )

    private val ADD_EXERCISE_VERBS = listOf(
        "anadir", "añadir", "agregar", "crear", "nuevo", "nueva",
    )

    private val SETTINGS_KEYS = listOf(
        "ajustes", "configuracion", "opciones", "preferencias",
    )

    private val TIMER_KEYS = listOf(
        "temporizador", "cronometro", "timer", "contador",
    )

    private val PRINCIPAL_KEYS = listOf(
        "principal", "inicio", "home", "sesiones", "sesion", "entrenamientos", "lista de sesiones",
    )
}
