package com.example.gymtrack.ui.common

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtrack.backup.ProfileRecovery
import com.example.gymtrack.data.cache.SessionDataCache
import com.example.gymtrack.data.local.dao.GymDao
import com.example.gymtrack.ui.muscle.MuscleGroupIcon
import com.example.gymtrack.ui.muscle.MuscleTagUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pantalla de arranque que hace una precarga agresiva antes de mostrar el contenido
 * principal. Es lo que evita que las primeras interacciones se sientan lentas:
 *
 *  - Abre la base de datos y ejecuta TODAS las consultas frecuentes para que las
 *    páginas de SQLite queden en el caché del SO. En particular se piden todas las
 *    series de cada sesión y los datasets de estadísticas.
 *  - Decodifica todos los drawables de grupos musculares para que cuando aparezcan
 *    no haya jank por decode.
 *  - Renderiza una versión oculta (alpha 0) de las composables más pesadas (cards,
 *    botones, iconos, scrolls, AnimatedVisibility, etc.) para que el JIT de ART
 *    compile sus rutas ahora en vez de durante la primera interacción del usuario.
 *
 * Se acepta esperar hasta 5 s en aras de que el primer uso ya sea fluido.
 */
@Composable
fun WarmupScreen(
    dao: GymDao,
    userName: String,
    onReady: () -> Unit,
) {
    val context = LocalContext.current
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var statusText by remember { mutableStateOf("Preparando…") }

    LaunchedEffect(Unit) {
        val minDisplay = launch { delay(150) }
        withTimeoutOrNull(5000L) {
            coroutineScope {
                // 1) Drawables de músculos en paralelo.
                val drawableJob = launch(Dispatchers.IO) {
                    val opts = BitmapFactory.Options()
                    MuscleTagUi.allDrawableResIds(darkTheme).forEach { id ->
                        runCatching { BitmapFactory.decodeResource(context.resources, id, opts) }
                    }
                }

                // 2) Toda la BD en paralelo. Además poblamos SessionDataCache para que
                //    los ViewModels que se creen después tengan ya el valor inicial.
                val dbJob = launch(Dispatchers.IO) {
                    runCatching { dao.observeProfiles().firstOrNull() }
                    if (userName.isBlank()) {
                        runCatching { ProfileRecovery.syncMissingProfiles(dao) }
                        return@launch
                    }

                    val exercises = runCatching { dao.observeExercises(userName).firstOrNull() }
                        .getOrNull().orEmpty()
                    SessionDataCache.putExercises(userName, exercises)

                    val sessions = runCatching { dao.observeSessions(userName).firstOrNull() }
                        .getOrNull().orEmpty()
                    withContext(Dispatchers.Main) {
                        statusText = "Cargando ${sessions.size} sesiones…"
                    }

                    // Series y datos por sesión en paralelo (limitado por el pool de IO).
                    coroutineScope {
                        sessions.forEach { session ->
                            launch {
                                SessionDataCache.putSession(session.id, session)
                                runCatching {
                                    val sets = dao.fetchSetsOrderedForSession(session.id)
                                    SessionDataCache.putSets(session.id, sets)
                                }
                                runCatching { dao.observeSessionPreview(session.id).firstOrNull() }
                                runCatching { dao.countSetsInSession(session.id) }
                                runCatching { dao.nextSortOrderForSession(session.id) }
                            }
                        }
                    }

                    // Estadísticas globales (las usa el dashboard).
                    runCatching { dao.observeSetStatsForUser(userName).firstOrNull() }
                    // Per-exercise progress: warm la query para algunos ejercicios.
                    exercises.take(20).forEach { ex ->
                        runCatching { dao.observeProgressAll(userName, ex.id).firstOrNull() }
                        runCatching {
                            dao.observeProgressAllWithSession(userName, ex.id).firstOrNull()
                        }
                    }
                }

                drawableJob.join()
                dbJob.join()
            }
        }
        minDisplay.join()
        withContext(Dispatchers.Main) {
            statusText = "Listo"
            onReady()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Render oculto de las composables pesadas para que el JIT las compile
            // ahora en vez de la primera vez que el usuario interactúe con ellas.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0f),
            ) {
                ComposeJitWarmup(darkTheme)
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "GYM TRACK",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Tu entrenamiento, tu progreso",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(28.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Renderiza una sola vez (oculto) cada composable pesada que aparece en el resto de
 * la app. Solo con componerla y medirla, el JIT de ART optimiza las rutas y la
 * próxima vez que el usuario la vea se renderiza prácticamente instantánea.
 */
@Composable
private fun ComposeJitWarmup(darkTheme: Boolean) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Card al estilo de SessionsScreen (con LinearProgressIndicator y preview).
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Sesión de calentamiento", fontWeight = FontWeight.SemiBold, modifier = Modifier)
                    IconButton(onClick = {}, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = {}, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                )
                AnimatedVisibility(visible = true) {
                    Column(
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Vista rápida",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            IconButton(onClick = {}, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                            }
                        }
                        repeat(3) {
                            Text(
                                "${it + 1}. Calentamiento — 10 reps • 20 kg • RIR 3",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        // Card al estilo de SessionDetailScreen (con Box + Text + Column + botones + icono músculo).
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "1",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 10.dp, start = 12.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .padding(top = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Ejercicio", fontWeight = FontWeight.SemiBold)
                    Text("Pecho", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("10 reps • 20 kg • RIR 3")
                    Text("Nota de la serie", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                        }
                        OutlinedButton(onClick = {}) { Text("Editar") }
                        OutlinedButton(onClick = {}) { Text("Eliminar") }
                    }
                }
                MuscleGroupIcon(
                    tag = "pecho",
                    size = 30.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp),
                )
            }
        }

        // Una muestra de cada tipo de icono de grupo muscular para que el painter
        // y el composable de imagen se compilen para cada layout (single/paired).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf("biceps", "pecho", "espalda", "cuadriceps", "femorales", "core", "gluteos").forEach { tag ->
                MuscleGroupIcon(tag = tag, size = 24.dp)
            }
        }
    }
}
