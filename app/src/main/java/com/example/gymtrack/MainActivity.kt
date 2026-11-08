package com.example.gymtrack

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.core.content.ContextCompat
import com.example.gymtrack.data.local.GymDatabase
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.alpha
import com.example.gymtrack.R
import com.example.gymtrack.backup.AccountSavePreferences
import com.example.gymtrack.backup.BackupManager
import com.example.gymtrack.backup.GymTrackShare
import com.example.gymtrack.backup.ProfileRecovery
import com.example.gymtrack.backup.ShareOffer
import com.example.gymtrack.data.cache.SessionDataCache
import com.example.gymtrack.domain.ExerciseMuscleTags
import com.example.gymtrack.domain.ProgressionCalculator
import com.example.gymtrack.data.local.dao.GymDao
import com.example.gymtrack.data.local.dao.OffCatalogDao
import com.example.gymtrack.data.local.entities.ExerciseEntity
import com.example.gymtrack.data.local.entities.ProfileEntity
import com.example.gymtrack.data.local.entities.SessionSetPreviewRow
import com.example.gymtrack.data.local.entities.SetEntity
import com.example.gymtrack.data.local.entities.UnitType
import com.example.gymtrack.service.TimerOverlayService
import com.example.gymtrack.ui.common.BrandLogoSwitcher
import com.example.gymtrack.ui.common.HintText
import com.example.gymtrack.nutrition.OffCatalogPrefs
import com.example.gymtrack.nutrition.OffUsedFoods
import com.example.gymtrack.ui.common.WarmupScreen
import com.example.gymtrack.ui.common.collectAsStateSafe
import com.example.gymtrack.ui.dashboard.OneRmCalculatorCard
import com.example.gymtrack.ui.training.TrainingProgramScreen
import com.example.gymtrack.ui.training.TrainingProgramViewModel
import com.example.gymtrack.ui.dashboard.DashboardViewModel
import com.example.gymtrack.ui.dashboard.ProgressPoint
import com.example.gymtrack.ui.dashboard.SessionProgressChart
import com.example.gymtrack.ui.dashboard.computeSessionBands
import com.example.gymtrack.ui.session.SessionsViewModel
import com.example.gymtrack.ui.sessiondetail.SessionDetailViewModel
import com.example.gymtrack.ui.sessiondetail.SessionProgramProgressDialog
import com.example.gymtrack.ui.sessiondetail.SessionProgramVolumeBars
import com.example.gymtrack.ui.muscle.MuscleGroupIcon
import com.example.gymtrack.ui.muscle.MuscleTagUi
import com.example.gymtrack.assistant.PibbleDestination
import com.example.gymtrack.assistant.PibbleNetwork
import com.example.gymtrack.assistant.VoiceSetCommandParser
import com.example.gymtrack.assistant.VoiceSetField
import com.example.gymtrack.domain.voice.ExerciseMatchCandidate
import com.example.gymtrack.assistant.deleteLegacyOnDeviceModels
import com.example.gymtrack.ui.pibble.PibbleAssistantScreen
import com.example.gymtrack.ui.voice.ContinuousSpeechRecognizer
import android.widget.Toast
import com.example.gymtrack.ui.nutrition.AddFoodDialog
import com.example.gymtrack.ui.nutrition.NutritionCalendarDialog
import com.example.gymtrack.ui.nutrition.NutritionGoalsDialog
import com.example.gymtrack.ui.nutrition.NutritionSection
import com.example.gymtrack.ui.nutrition.NutritionViewModel
import com.example.gymtrack.ui.share.IncomingShareBanner
import com.example.gymtrack.ui.theme.AppBrand
import com.example.gymtrack.ui.theme.GymTrackTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PREFS_NAME = "gymtrack_prefs"
private const val KEY_USER  = "current_user_name"
private const val KEY_NUTRITION_MODE = "nutrition_mode"
private const val KEY_VOICE_OFFLINE_HINT_LAST = "voice_offline_hint_last_ms"
private const val VOICE_OFFLINE_HINT_INTERVAL_MS = 10 * 60 * 1000L

private val saveAccountButtonColors
    @Composable get() = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
    )

private fun dashboardExerciseKey(userName: String) = "dashboard_last_exercise_$userName"

class MainActivity : ComponentActivity() {
    private val shareOffer = MutableStateFlow<ShareOffer?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deleteLegacyOnDeviceModels(this)
        val dao = GymDatabase.getInstance(this).gymDao()
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (savedInstanceState == null) handleIncomingIntent(intent)

        setContent {
            var darkMode by rememberSaveable { mutableStateOf(prefs.getBoolean("dark_mode", true)) }
            var userName by rememberSaveable { mutableStateOf(prefs.getString(KEY_USER, "") ?: "") }
            // Sólo precalentamos una vez por proceso: si la Activity recrea (rotación, etc.)
            // se conserva, así no parpadea el splash en cambios de configuración.
            var warmupDone by rememberSaveable { mutableStateOf(false) }
            var nutritionMode by rememberSaveable { mutableStateOf(prefs.getBoolean(KEY_NUTRITION_MODE, false)) }
            val incoming by shareOffer.asStateFlow().collectAsState()
            val shareScope = rememberCoroutineScope()

            GymTrackTheme(
                forceDarkTheme = darkMode,
                brand = if (nutritionMode && userName.isNotBlank()) AppBrand.Nutrition else AppBrand.Gym,
            ) {
                Box(Modifier.fillMaxSize()) {
                    when {
                        !warmupDone -> WarmupScreen(
                            dao = dao,
                            userName = userName,
                            onReady = { warmupDone = true },
                        )
                        userName.isBlank() -> LoginScreen(
                            dao = dao,
                            onLogin = { name ->
                                userName = name
                                prefs.edit().putString(KEY_USER, name).apply()
                            }
                        )
                        else -> AppContent(
                            dao = dao,
                            userName = userName,
                            darkMode = darkMode,
                            nutritionMode = nutritionMode,
                            onNutritionModeChange = { enabled ->
                                nutritionMode = enabled
                                prefs.edit().putBoolean(KEY_NUTRITION_MODE, enabled).apply()
                            },
                            onDarkModeChange = { darkMode = it; prefs.edit().putBoolean("dark_mode", it).apply() },
                            onLogout = {
                                userName = ""
                                prefs.edit().remove(KEY_USER).apply()
                            },
                            onOverlayClick = { requestOverlayAndStartService() }
                        )
                    }

                    incoming?.let { offer ->
                        IncomingShareBanner(
                            offer = offer,
                            canAddMacrocycle = userName.isNotBlank(),
                            onAccept = {
                                shareScope.launch {
                                    val result = when (offer) {
                                        is ShareOffer.Macrocycle -> {
                                            if (userName.isBlank()) return@launch
                                            BackupManager.importMacrocycleForUser(dao, userName, offer.json)
                                        }
                                        is ShareOffer.Account -> BackupManager.importSharedAccount(dao, offer.json)
                                    }
                                    shareOffer.value = null
                                    val msg = when (result) {
                                        is BackupManager.ImportResult.Success -> when (offer) {
                                            is ShareOffer.Macrocycle -> "Macrociclo añadido."
                                            is ShareOffer.Account ->
                                                "Cuenta «${result.importedUserName ?: offer.userName}» añadida."
                                        }
                                        is BackupManager.ImportResult.Error -> result.reason
                                    }
                                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDismiss = { shareOffer.value = null },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(12.dp)
                                .zIndex(8f),
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    @Suppress("DEPRECATION")
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val hasPayload = intent.data != null ||
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) != null ||
            intent.clipData != null ||
            !intent.getStringExtra(Intent.EXTRA_TEXT).isNullOrBlank()
        if (!hasPayload) return
        lifecycleScope.launch {
            val offer = GymTrackShare.parseIntent(this@MainActivity, intent)
            if (offer != null) shareOffer.value = offer
        }
    }

    private fun requestOverlayAndStartService() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        startForegroundService(Intent(this, TimerOverlayService::class.java))
    }

    companion object {
    }
}

// ─── LOGIN / PERFIL ──────────────────────────────────────────────────────────

@Composable
private fun LoginScreen(dao: GymDao, onLogin: (String) -> Unit) {
    val scope    = rememberCoroutineScope()
    val profiles by dao.observeProfiles().collectAsState(initial = emptyList())

    val context = LocalContext.current
    var newName      by rememberSaveable { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameDraft  by rememberSaveable { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var duplicateSource by remember { mutableStateOf<String?>(null) }
    var duplicateNameDraft by rememberSaveable { mutableStateOf("") }
    var duplicateError by remember { mutableStateOf<String?>(null) }
    var recoveryNotice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val recovered = ProfileRecovery.syncMissingProfiles(dao)
        if (recovered.isNotEmpty()) {
            recoveryNotice =
                "Recuperada(s) ${recovered.size}: ${recovered.joinToString(", ")}"
        }
    }

    // ── Backup import state ───────────────────────────────────────────────────
    var importDialogMsg  by remember { mutableStateOf<String?>(null) }
    var showImportConfirm by remember { mutableStateOf<android.net.Uri?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { showImportConfirm = it } }

    // ── Import confirm dialog ─────────────────────────────────────────────────
    showImportConfirm?.let { uri ->
        AlertDialog(
            onDismissRequest = { showImportConfirm = null },
            title   = { Text("¿Restaurar cuenta?") },
            text    = { Text("Esto sobrescribirá los datos existentes con el backup seleccionado. ¿Continuar?") },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm = null
                    scope.launch {
                        val result = BackupManager.importFromUri(context, uri, dao)
                        importDialogMsg = when (result) {
                            is BackupManager.ImportResult.Success -> "Cuenta restaurada correctamente."
                            is BackupManager.ImportResult.Error   -> "❌ ${result.reason}"
                        }
                    }
                }) { Text("Restaurar") }
            },
            dismissButton = { TextButton(onClick = { showImportConfirm = null }) { Text("Cancelar") } }
        )
    }

    importDialogMsg?.let { msg ->
        AlertDialog(
            onDismissRequest = { importDialogMsg = null },
            title   = { Text("Importar") },
            text    = { Text(msg) },
            confirmButton = { TextButton(onClick = { importDialogMsg = null }) { Text("OK") } }
        )
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 60.dp, bottom = 32.dp)
        ) {
            // ── Título ────────────────────────────────────────────────────
            item {
                Text(
                    "GYM TRACK",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Tu entrenamiento, tu progreso",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
            }

            recoveryNotice?.let { notice ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Text(
                            notice,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            // ── Registro de nuevo perfil ──────────────────────────────────
            item {
                Text(
                    "Nuevo perfil",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val n = newName.trim()
                            if (n.isNotBlank()) {
                                scope.launch {
                                    dao.insertProfile(ProfileEntity(name = n))
                                    newName = ""
                                    onLogin(n)
                                }
                            }
                        },
                        enabled = newName.isNotBlank()
                    ) { Text("Registrar") }
                }
            }

            // ── Perfiles guardados ─────────────────────────────────────────
            if (profiles.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Perfiles guardados",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                }
                items(profiles) { profile ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Nombre — toca para entrar
                            TextButton(
                                onClick = { onLogin(profile.name) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text(
                                    profile.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            // Renombrar
                            IconButton(
                                onClick = { renameTarget = profile.name; renameDraft = profile.name },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Renombrar",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    duplicateSource = profile.name
                                    duplicateNameDraft = BackupManager.suggestDuplicateName(
                                        profiles.map { it.name },
                                        profile.name,
                                    )
                                    duplicateError = null
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Duplicar cuenta",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            IconButton(
                                onClick = { deleteTarget = profile.name },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Borrar",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // ── Botón Cargar Cuenta ───────────────────────────────────────────
            item {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Cargar Cuenta") }
            }

            // ── Footer: autor y versión ───────────────────────────────────────
            item {
                Spacer(Modifier.height(24.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Hecho por Roman Finol",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "v1.2",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }

    // ── Diálogo renombrar ─────────────────────────────────────────────────────
    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Renombrar perfil") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    label = { Text("Nuevo nombre") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameDraft.isNotBlank() && renameDraft.trim() != renameTarget,
                    onClick = {
                        val old = renameTarget!!
                        val new = renameDraft.trim()
                        scope.launch {
                            dao.renameAccountUser(old, new)
                            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            AccountSavePreferences.getSavedUri(prefs, old)?.let { uri ->
                                AccountSavePreferences.clearSavedLocation(prefs, old)
                                AccountSavePreferences.rememberSaveLocation(context, prefs, new, uri)
                            }
                        }
                        renameTarget = null
                    }
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancelar") } }
        )
    }

    // ── Diálogo confirmar borrado ─────────────────────────────────────────────
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Borrar perfil") },
            text = { Text("¿Borrar el perfil \"$deleteTarget\"? Se eliminarán sesiones, ejercicios, programación y datos asociados. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    val name = deleteTarget!!
                    scope.launch {
                        dao.deleteAccountData(name)
                        AccountSavePreferences.clearSavedLocation(
                            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                            name,
                        )
                    }
                    deleteTarget = null
                }) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") } }
        )
    }

    duplicateSource?.let { sourceName ->
        AlertDialog(
            onDismissRequest = { duplicateSource = null },
            title = { Text("Duplicar cuenta") },
            text = {
                OutlinedTextField(
                    value = duplicateNameDraft,
                    onValueChange = {
                        duplicateNameDraft = it
                        duplicateError = null
                    },
                    label = { Text("Nombre de la copia") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = duplicateError != null,
                    supportingText = duplicateError?.let { msg -> { Text(msg) } },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = duplicateNameDraft.isNotBlank(),
                    onClick = {
                        val source = sourceName
                        val target = duplicateNameDraft.trim()
                        duplicateSource = null
                        duplicateError = null
                        scope.launch {
                            when (
                                val result = BackupManager.duplicateAccount(
                                    dao,
                                    sourceUserName = source,
                                    targetUserName = target,
                                )
                            ) {
                                is BackupManager.DuplicateResult.Error ->
                                    recoveryNotice = result.reason
                                else -> Unit
                            }
                        }
                    },
                ) { Text("Guardar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { duplicateSource = null }) { Text("Cancelar") }
            },
        )
    }
}

/** Evita varios [NavController.popBackStack] seguidos cuando el destino actual ya cambió (p. ej. pantalla negra al pulsar «volver» muy rápido desde Ajustes). */
private fun NavController.popBackStackIfShowing(route: String) {
    if (currentBackStackEntry?.destination?.route != route) return
    popBackStack()
}

/** Sale de la sesión y restaura la pestaña de inicio correcta. */
private fun NavController.exitSession(returnTab: Int) {
    navigate("home/$returnTab") {
        popUpTo("session/{id}?highlightSetId={highlightSetId}&returnTab={returnTab}") {
            inclusive = true
        }
        launchSingleTop = true
    }
}

private fun formatMuscleTagsLine(csv: String): String {
    val tags = ExerciseMuscleTags.fromCsv(csv)
    if (tags.isEmpty()) return ""
    return tags.joinToString(", ") { MuscleTagUi.displayName(it) }
}

private fun formatMuscleGroupSectionTitle(groupKey: String): String =
    if (groupKey == ExerciseMuscleTags.GROUP_UNTAGGED) {
        "Sin etiqueta"
    } else {
        groupKey.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else c.toString() }
    }

/** Ordinal abreviado en femenino para "la serie": 1era, 2da, … 9na, 10ª, 11ª, … 99ª. */
private fun spanishFeminineOrdinal(n: Int): String = when (n) {
    1 -> "1era"
    2 -> "2da"
    3 -> "3era"
    4 -> "4ta"
    5 -> "5ta"
    6 -> "6ta"
    7 -> "7ma"
    8 -> "8va"
    9 -> "9na"
    else -> "${n}ª"
}

/**
 * Calcula la posición (1-based) de [setId] entre las series de la misma sesión que
 * comparten el grupo muscular primario de [exerciseId]. Devuelve null si no se puede
 * resolver (datos aún no cacheados o ejercicio sin etiqueta).
 */
private fun muscleGroupPositionForSet(
    setId: Long,
    sessionSets: List<com.example.gymtrack.data.local.entities.SetEntity>,
    exercises: List<com.example.gymtrack.data.local.entities.ExerciseEntity>,
    exerciseId: Long,
): Pair<Int, String>? {
    if (sessionSets.isEmpty() || exercises.isEmpty()) return null
    val exById = exercises.associateBy { it.id }
    val targetEx = exById[exerciseId] ?: return null
    val targetGroup = ExerciseMuscleTags.primaryGroupKey(targetEx.muscleTags)
    if (targetGroup == ExerciseMuscleTags.GROUP_UNTAGGED) return null
    val sameGroup = sessionSets
        .asSequence()
        .filter { s ->
            val ex = exById[s.exerciseId] ?: return@filter false
            ExerciseMuscleTags.primaryGroupKey(ex.muscleTags) == targetGroup
        }
        .sortedWith(compareBy({ it.sortOrder }, { it.createdAt }, { it.id }))
        .toList()
    val idx = sameGroup.indexOfFirst { it.id == setId }
    if (idx < 0) return null
    return (idx + 1) to MuscleTagUi.displayName(targetGroup)
}

/** Lista ya filtrada por búsqueda; se segmenta solo visualmente por grupo muscular principal. */
private fun groupedExercisesByPrimaryTag(filtered: List<ExerciseEntity>): List<Pair<String, List<ExerciseEntity>>> {
    val out = ArrayList<Pair<String, List<ExerciseEntity>>>()
    val buckets = LinkedHashMap<String, MutableList<ExerciseEntity>>()
    ExerciseMuscleTags.ALL.forEach { buckets[it] = mutableListOf() }
    buckets[ExerciseMuscleTags.GROUP_UNTAGGED] = mutableListOf()
    filtered.forEach { ex ->
        buckets[ExerciseMuscleTags.primaryGroupKey(ex.muscleTags)]!!.add(ex)
    }
    ExerciseMuscleTags.ALL.forEach { k ->
        buckets[k]?.takeIf { it.isNotEmpty() }?.let { out.add(k to it.sortedBy { e -> e.name }) }
    }
    buckets[ExerciseMuscleTags.GROUP_UNTAGGED]?.takeIf { it.isNotEmpty() }?.let {
        out.add(ExerciseMuscleTags.GROUP_UNTAGGED to it.sortedBy { e -> e.name })
    }
    return out
}

// ─── APP CONTENT / NAV ───────────────────────────────────────────────────────

@Composable
private fun AppContent(
    dao: GymDao,
    userName: String,
    darkMode: Boolean,
    nutritionMode: Boolean,
    onNutritionModeChange: (Boolean) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onLogout: () -> Unit,
    onOverlayClick: () -> Unit
) {
    val context = LocalContext.current
    val nav = rememberNavController()
    val sessionsVm = remember(userName) { SessionsViewModel(dao, userName) }
    val trainingProgramVm = remember(userName) { TrainingProgramViewModel(dao, userName) }
    val dashboardVm = remember(userName) {
        val savedExerciseId = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(dashboardExerciseKey(userName), -1L)
            .takeIf { it >= 0 }
        DashboardViewModel(dao, userName, savedExerciseId)
    }
    val nutritionVm = remember(userName) {
        NutritionViewModel(
            dao,
            GymDatabase.getInstance(context).offCatalogDao(),
            context.applicationContext,
            userName,
        )
    }

    NavHost(
        navController = nav,
        startDestination = "home/1",
        enterTransition = { fadeIn(tween(120)) },
        exitTransition = { fadeOut(tween(90)) },
        popEnterTransition = { fadeIn(tween(120)) },
        popExitTransition = { fadeOut(tween(90)) },
    ) {
        composable(
            route = "home/{tab}",
            arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 1 })
        ) { entry ->
            val initialTab = entry.arguments?.getInt("tab") ?: 0
            HomeScreen(
                sessionsVm        = sessionsVm,
                dashboardVm       = dashboardVm,
                trainingProgramVm = trainingProgramVm,
                dao               = dao,
                userName          = userName,
                initialTab        = initialTab,
                nutritionMode     = nutritionMode,
                onNutritionModeChange = onNutritionModeChange,
                nutritionVm       = nutritionVm,
                onOpenSession     = { nav.navigate("session/$it?returnTab=1") },
                onOpenSessionFromStats = { sessionId, setId ->
                    nav.navigate("session/$sessionId?highlightSetId=$setId&returnTab=2")
                },
                onOpenSettings    = { nav.navigate("settings") },
                onOpenAddExercise = { nav.navigate("exercise/new") },
                onOverlayClick    = onOverlayClick
            )
        }
        composable("exercise/new") {
            AddExerciseScreen(dao, userName, onBack = { nav.popBackStackIfShowing("exercise/new") })
        }
        composable("settings") {
            SettingsScreen(
                darkMode,
                onDarkModeChange,
                onLogout,
                onBack = { nav.popBackStackIfShowing("settings") },
                dao = dao,
                userName = userName,
                onOpenPibbleHelp = { nav.navigate("pibbleHelp") },
                nutritionMode = nutritionMode,
                catalogDao = GymDatabase.getInstance(context).offCatalogDao(),
            )
        }
        composable("pibbleHelp") {
            PibbleAssistantScreen(
                onBack = { nav.popBackStackIfShowing("pibbleHelp") },
                onNavigate = { dest ->
                    nav.popBackStackIfShowing("pibbleHelp")
                    when (dest) {
                        PibbleDestination.HOME_PRINCIPAL ->
                            nav.navigate("home/1") {
                                popUpTo("home/1") { inclusive = true }
                                launchSingleTop = true
                            }
                        PibbleDestination.HOME_STATS ->
                            nav.navigate("home/2") {
                                popUpTo("home/1") { inclusive = true }
                                launchSingleTop = true
                            }
                        PibbleDestination.ADD_EXERCISE -> nav.navigate("exercise/new")
                        PibbleDestination.SETTINGS -> nav.navigate("settings")
                        PibbleDestination.TIMER_OVERLAY -> onOverlayClick()
                    }
                },
            )
        }
        composable(
            route = "session/{id}?highlightSetId={highlightSetId}&returnTab={returnTab}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("highlightSetId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument("returnTab") {
                    type = NavType.IntType
                    defaultValue = 1
                },
            )
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            val highlightSetId = entry.arguments?.getLong("highlightSetId")
                ?.takeIf { it > 0L }
            val returnTab = entry.arguments?.getInt("returnTab")?.coerceIn(0, 2) ?: 1
            val vm = remember(id, userName) { SessionDetailViewModel(dao, id, userName) }
            SessionDetailScreen(
                vm          = vm,
                initialHighlightSetId = highlightSetId,
                onBack      = { nav.exitSession(returnTab) },
                onOpenStats = {
                    dashboardVm.setSession(id)
                    nav.navigate("home/2") {
                        popUpTo("session/{id}?highlightSetId={highlightSetId}&returnTab={returnTab}") {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}

// ─── HOME ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    sessionsVm: SessionsViewModel,
    dashboardVm: DashboardViewModel,
    trainingProgramVm: TrainingProgramViewModel,
    dao: GymDao,
    userName: String,
    initialTab: Int = 0,
    nutritionMode: Boolean,
    onNutritionModeChange: (Boolean) -> Unit,
    nutritionVm: NutritionViewModel,
    onOpenSession: (Long) -> Unit,
    onOpenSessionFromStats: (Long, Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAddExercise: () -> Unit,
    onOverlayClick: () -> Unit
) {
    val gymTabs = listOf("Programación", "Principal", "Estadísticas")
    val pager = rememberPagerState(
        initialPage = initialTab.coerceIn(0, gymTabs.lastIndex),
        pageCount = { gymTabs.size }
    )
    val scope = rememberCoroutineScope()
    val exerciseCount by remember(userName) {
        dao.observeExercises(userName).map { it.size }
    }.collectAsState(initial = 0)
    var showNutritionCalendar by remember { mutableStateOf(false) }
    var showNutritionGoals by remember { mutableStateOf(false) }
    var showCreateFood by remember { mutableStateOf(false) }
    val selectedNutritionDate by nutritionVm.selectedDate.collectAsStateSafe()
    val nutritionGoals by nutritionVm.goals.collectAsStateSafe()

    // Jump to the requested tab (e.g. Estadísticas when coming from a session)
    LaunchedEffect(initialTab, nutritionMode) {
        if (!nutritionMode) {
            pager.scrollToPage(initialTab.coerceIn(0, gymTabs.lastIndex))
        }
    }

    // Cierra la vista rápida al cambiar de pestaña a algo que no sea "Principal".
    LaunchedEffect(pager.currentPage, nutritionMode) {
        if (nutritionMode || pager.currentPage != 1) sessionsVm.clearPreview()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                BrandLogoSwitcher(
                    nutritionMode = nutritionMode,
                    onNutritionModeChange = onNutritionModeChange,
                )
                Text(
                    "Hola, $userName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Action buttons ─────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (nutritionMode) Modifier.horizontalScroll(rememberScrollState())
                        else Modifier
                    )
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { sessionsVm.clearPreview(); onOpenSettings() }) {
                    Text("Ajustes", maxLines = 1, softWrap = false)
                }
                if (nutritionMode) {
                    Button(
                        onClick = { nutritionVm.shiftDay(-1) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Día anterior")
                    }
                    Button(
                        onClick = { nutritionVm.shiftDay(1) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Día siguiente")
                    }
                    Button(
                        onClick = { showNutritionCalendar = true },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = "Calendario",
                        )
                    }
                    Button(
                        onClick = { showNutritionGoals = true },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            Icons.Default.TrackChanges,
                            contentDescription = "Objetivos",
                        )
                    }
                    Button(
                        onClick = { showCreateFood = true },
                    ) { Text("Añadir alimento", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
                } else {
                    Button(onClick = { sessionsVm.clearPreview(); onOverlayClick() }) {
                        Text("Temporizador", maxLines = 1, softWrap = false)
                    }
                    val pulseAlpha: Float = if (exerciseCount == 0) {
                        val inf = rememberInfiniteTransition(label = "addExPulse")
                        inf.animateFloat(
                            initialValue = 1f,
                            targetValue  = 0.25f,
                            animationSpec = infiniteRepeatable(
                                animation  = tween(800, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "addExAlpha"
                        ).value
                    } else 1f
                    Button(
                        onClick  = { sessionsVm.clearPreview(); onOpenAddExercise() },
                        modifier = Modifier.alpha(pulseAlpha)
                    ) { Text("Añadir ejercicio", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (nutritionMode) {
                NutritionSection(vm = nutritionVm, modifier = Modifier.weight(1f))
            } else {
                TabRow(selectedTabIndex = pager.currentPage) {
                    gymTabs.forEachIndexed { i, t ->
                        Tab(
                            selected = pager.currentPage == i,
                            onClick  = {
                                scope.launch {
                                    pager.animateScrollToPage(
                                        i,
                                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                                    )
                                }
                            },
                            text     = { Text(t) }
                        )
                    }
                }

                HorizontalPager(
                    state = pager,
                    modifier = Modifier.weight(1f),
                    beyondViewportPageCount = 0
                ) { page ->
                    when (page) {
                        0 -> TrainingProgramScreen(vm = trainingProgramVm)
                        1 -> SessionsScreen(
                            vm = sessionsVm,
                            onOpenSession = onOpenSession,
                        )
                        else -> DashboardScreen(dao, dashboardVm, userName, onOpenSessionFromStats)
                    }
                }
            }
        }
    }

    if (showNutritionCalendar) {
        NutritionCalendarDialog(
            selectedDate = selectedNutritionDate,
            onSelectDate = nutritionVm::selectDate,
            onDismiss = { showNutritionCalendar = false },
        )
    }
    if (showNutritionGoals) {
        NutritionGoalsDialog(
            current = nutritionGoals,
            onSave = { sex, age, height, weight, activity, goal, bodyFat, protein, carbs, fat, fiber ->
                nutritionVm.saveGoals(sex, age, height, weight, activity, goal, bodyFat, protein, carbs, fat, fiber)
            },
            onDismiss = { showNutritionGoals = false },
        )
    }
    if (showCreateFood) {
        AddFoodDialog(
            vm = nutritionVm,
            fromScratch = true,
            onDismiss = { showCreateFood = false },
        )
    }
}

// ─── SESSIONS LIST ────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionsScreen(
    vm: SessionsViewModel,
    onOpenSession: (Long) -> Unit
) {
    val sessions by vm.sessions.collectAsStateSafe()
    val previewId by vm.previewId.collectAsStateSafe()
    var query       by rememberSaveable { mutableStateOf("") }
    var showCreate  by remember { mutableStateOf(false) }
    var sessionName by rememberSaveable { mutableStateOf("") }
    var previewRows by remember { mutableStateOf<List<SessionSetPreviewRow>>(emptyList()) }
    var openingSessionId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(previewId) {
        val id = previewId
        if (id == null) {
            previewRows = emptyList()
        } else {
            vm.observePreview(id).collectLatest { previewRows = it }
        }
    }
    // Rename + delete dialog state
    var renameTarget by remember { mutableStateOf<Long?>(null) }
    var renameDraft  by rememberSaveable { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Long?>(null) }

    val filtered = remember(sessions, query) {
        if (query.isBlank()) sessions else sessions.filter { it.title.contains(query, true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar sesión") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                vm.clearPreview()
                showCreate = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Nueva sesión", maxLines = 1, softWrap = false)
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(items = filtered, key = { it.id }) { s ->
                val previewScrollState = rememberScrollState()
                val isOpening = openingSessionId == s.id
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    enabled = openingSessionId == null,
                                    onClick = {
                                        vm.clearPreview()
                                        openingSessionId = s.id
                                        vm.preloadSession(s.id) {
                                            openingSessionId = null
                                            onOpenSession(s.id)
                                        }
                                    },
                                    onLongClick = { vm.togglePreview(s.id) },
                                )
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                s.title,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = { renameTarget = s.id; renameDraft = s.rawName },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Renombrar", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { deleteTarget = s.id },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Eliminar sesión",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        if (isOpening) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp),
                            )
                        }

                        AnimatedVisibility(
                            visible = previewId == s.id,
                            enter = fadeIn(tween(120)) + expandVertically(tween(140)),
                            exit = fadeOut(tween(100)) + shrinkVertically(tween(120)),
                        ) {
                            Column(
                                modifier = Modifier.padding(
                                    start = 12.dp,
                                    end = 12.dp,
                                    bottom = 12.dp,
                                ),
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
                                    IconButton(
                                        onClick = { vm.clearPreview() },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Cerrar vista rápida",
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                                if (previewRows.isEmpty()) {
                                    Text(
                                        "Sin series",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 360.dp)
                                            .verticalScroll(previewScrollState),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        previewRows.forEachIndexed { i, row ->
                                            Text(
                                                "${i + 1}. ${row.exerciseName} — ${row.reps} reps • ${row.weight} • RIR ${row.rir}",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // New session dialog
    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Nueva sesión") },
            text  = {
                OutlinedTextField(
                    value = sessionName,
                    onValueChange = { sessionName = it },
                    label = { Text("Nombre") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = sessionName.isNotBlank(),
                    onClick = { vm.addSession(sessionName); sessionName = ""; showCreate = false }
            ) { Text("Guardar", maxLines = 1, softWrap = false) }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancelar") } }
        )
    }

    // Rename session dialog
    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Renombrar sesión") },
            text  = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    label = { Text("Nuevo nombre") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameDraft.isNotBlank(),
                    onClick = {
                        vm.renameSession(renameTarget!!, renameDraft)
                        renameTarget = null
                    }
                ) { Text("Guardar", maxLines = 1, softWrap = false) }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancelar") } }
        )
    }

    // Confirm delete session dialog
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("¿Eliminar sesión?") },
            text  = { Text("Esta acción eliminará la sesión y todas sus series. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSession(deleteTarget!!)
                    deleteTarget = null
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error, maxLines = 1, softWrap = false) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") } }
        )
    }
}

// ─── SESSION DETAIL ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionDetailScreen(
    vm: SessionDetailViewModel,
    initialHighlightSetId: Long? = null,
    onBack: () -> Unit,
    onOpenStats: () -> Unit = {},
) {
    val context = LocalContext.current
    val session   by vm.session.collectAsStateSafe()
    val exercises by vm.exercises.collectAsStateSafe()
    val sets      by vm.sets.collectAsStateSafe()
    val programProgress by vm.programGroupProgress.collectAsStateSafe()
    val programProgressIsPastWeek by vm.programProgressIsPastWeek.collectAsStateSafe()
    var showProgramProgress by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val gapPx = remember(density) { with(density) { 8.dp.toPx() } }
    val defaultRowPx = remember(density) { with(density) { 100.dp.toPx() } }
    val setsScrollState = rememberScrollState()

    /** Orden mostrado; se congela durante el intercambio para no saltar al actualizar Room.
     *  Se inicializa con el valor actual de [vm.sets] para que la pantalla pinte contenido
     *  en el primer frame cuando el caché de [SessionDataCache] ya estaba caliente. */
    var displaySets by remember { mutableStateOf(vm.sets.value) }

    /** Par (id arriba, id abajo) mientras corre la animación de intercambio; null si idle. */
    var swappingAdjacentIds by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    val swapProgress = remember { Animatable(0f) }
    /** Evita que Room (orden antiguo) pise el orden local justo tras un intercambio. */
    var blockSetsSyncFromDb by remember { mutableStateOf(false) }

    LaunchedEffect(sets, swappingAdjacentIds, blockSetsSyncFromDb) {
        if (swappingAdjacentIds != null) return@LaunchedEffect
        if (blockSetsSyncFromDb) {
            if (sets.map { it.id } == displaySets.map { it.id }) {
                blockSetsSyncFromDb = false
            }
            return@LaunchedEffect
        }
        if (sets.map { it.id } != displaySets.map { it.id }) {
            displaySets = sets
        } else if (sets != displaySets) {
            displaySets = sets
        }
    }

    /** Alturas medidas de cada tarjeta (px), para animar el intercambio exacto entre dos filas. */
    val setRowHeightsPx = remember { mutableStateMapOf<Long, Float>() }

    /** IDs de las filas que participaron en el último intercambio (pulso ~0,8 s). */
    var reorderHighlightIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var topMarkHighlightId by remember { mutableStateOf<Long?>(initialHighlightSetId) }
    var consumedInitialHighlightId by remember { mutableStateOf<Long?>(null) }
    fun flashReorderHighlight(movedSetId: Long, neighborSetId: Long?) {
        reorderHighlightIds = buildSet {
            add(movedSetId)
            neighborSetId?.let { add(it) }
        }
        scope.launch {
            delay(850)
            reorderHighlightIds = emptySet()
        }
    }

    LaunchedEffect(displaySets, initialHighlightSetId) {
        val targetId = initialHighlightSetId ?: return@LaunchedEffect
        if (consumedInitialHighlightId == targetId) return@LaunchedEffect
        val targetIndex = displaySets.indexOfFirst { it.id == targetId }
        if (targetIndex < 0) return@LaunchedEffect
        consumedInitialHighlightId = targetId
        delay(120)
        val targetScroll = displaySets
            .take(targetIndex)
            .sumOf { ((setRowHeightsPx[it.id] ?: defaultRowPx) + gapPx).toInt() }
            .coerceAtLeast(0)
        setsScrollState.animateScrollTo(targetScroll)
        topMarkHighlightId = targetId
        delay(2400)
        if (topMarkHighlightId == targetId) topMarkHighlightId = null
    }

    suspend fun runAdjacentSwap(upperIndex: Int, persist: suspend () -> Unit) {
        if (upperIndex < 0 || upperIndex >= displaySets.lastIndex) return
        val upper = displaySets[upperIndex]
        val lower = displaySets[upperIndex + 1]
        swappingAdjacentIds = upper.id to lower.id
        swapProgress.snapTo(0f)
        swapProgress.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        val swapped = displaySets.toMutableList().also {
            it[upperIndex] = lower
            it[upperIndex + 1] = upper
        }
        blockSetsSyncFromDb = true
        displaySets = swapped
        swappingAdjacentIds = null
        swapProgress.snapTo(0f)
        persist()
        flashReorderHighlight(lower.id, upper.id)
    }

    var showAdd       by remember { mutableStateOf(false) }
    var showPicker    by remember { mutableStateOf(false) }
    var showRename    by remember { mutableStateOf(false) }
    var renameDraft   by rememberSaveable { mutableStateOf("") }
    var search        by rememberSaveable { mutableStateOf("") }
    var selectedExercise by rememberSaveable { mutableStateOf<Long?>(null) }
    var reps   by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var rir    by rememberSaveable { mutableStateOf("") }
    var note   by rememberSaveable { mutableStateOf("") }
    var voiceListening by remember { mutableStateOf(false) }
    var voiceStatus by remember { mutableStateOf<String?>(null) }
    var voiceProcessing by remember { mutableStateOf(false) }
    var voiceUseCloudParser by remember { mutableStateOf(true) }
    var pendingVoiceUtterance by remember { mutableStateOf<List<String>?>(null) }
    var voiceExerciseAmbiguity by remember { mutableStateOf<List<ExerciseMatchCandidate>?>(null) }
    var editing by remember { mutableStateOf<SetEntity?>(null) }
    var deleteSetTarget by remember { mutableStateOf<SetEntity?>(null) }
    val exerciseMap = remember(exercises) { exercises.associateBy { it.id } }

    val sessionTitle = session?.customName ?: "Sesión"
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val demoPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    var wasOnlineAtLastVoiceCheck by remember { mutableStateOf<Boolean?>(null) }

    suspend fun applyVoiceUtterance(alternatives: List<String>) {
        val text = alternatives.firstOrNull() ?: return
        if (voiceProcessing) {
            pendingVoiceUtterance = alternatives
            return
        }
        voiceProcessing = true
        voiceStatus = "Procesando: \"$text\"…"
        val result = VoiceSetCommandParser.parseAll(
            alternatives = alternatives,
            exercises = exercises,
            selectedExerciseId = selectedExercise,
            reps = reps,
            weight = weight,
            rir = rir,
            note = note,
            useCloudParser = voiceUseCloudParser,
        )
        voiceProcessing = false
        if (!result.hasAny) {
            voiceExerciseAmbiguity = null
            voiceStatus = if (voiceUseCloudParser) {
                "No entendí. Intenta de nuevo"
            } else {
                "No entendí (modo offline). Prueba: «10 reps, RIR 2, 40 kg»"
            }
        } else {
            val applied = mutableListOf<String>()
            for (cmd in result.commands) {
                when (cmd.field) {
                    VoiceSetField.EXERCISE -> {
                        if (cmd.exerciseId != null) {
                            selectedExercise = cmd.exerciseId
                            applied += "Ejercicio: ${cmd.exerciseName}"
                        }
                    }
                    VoiceSetField.REPS -> {
                        reps = cmd.value
                        applied += "Reps: ${cmd.value}"
                    }
                    VoiceSetField.WEIGHT -> {
                        weight = cmd.value
                        applied += "Peso: ${cmd.value}"
                    }
                    VoiceSetField.RIR -> {
                        rir = cmd.value
                        applied += "RIR: ${cmd.value}"
                    }
                    VoiceSetField.NOTE -> {
                        note = cmd.value
                        applied += "Nota"
                    }
                    VoiceSetField.NONE -> Unit
                }
            }
            if (result.ambiguousExercises.isNotEmpty()) {
                voiceExerciseAmbiguity = result.ambiguousExercises
                applied += "Elige ejercicio (${result.ambiguousExercises.size})"
            } else {
                voiceExerciseAmbiguity = null
            }
            voiceStatus = if (applied.isEmpty()) {
                "No entendí. Intenta de nuevo"
            } else {
                applied.joinToString(" • ")
            }
        }
        val pending = pendingVoiceUtterance
        if (pending != null) {
            pendingVoiceUtterance = null
            applyVoiceUtterance(pending)
        }
    }

    val continuousSpeech = remember(context, scope) {
        ContinuousSpeechRecognizer(
            context = context,
            scope = scope,
            onResult = { alternatives ->
                scope.launch { applyVoiceUtterance(alternatives) }
            },
            onListening = {
                voiceStatus = if (voiceUseCloudParser) "Escuchando…" else "Escuchando (offline)…"
            },
            onErrorMessage = { msg ->
                voiceStatus = msg
                voiceListening = false
            },
        )
    }

    DisposableEffect(continuousSpeech) {
        onDispose { continuousSpeech.destroy() }
    }

    // Sesga el reconocimiento hacia el vocabulario del gym y los ejercicios del usuario.
    LaunchedEffect(continuousSpeech, exercises) {
        continuousSpeech.biasingStrings = buildList {
            addAll(exercises.map { it.name })
            addAll(
                listOf(
                    "RIR", "reps", "repeticiones", "serie", "series",
                    "kilos", "kg", "placas", "placa", "discos", "libras", "peso",
                ),
            )
        }
    }

    LaunchedEffect(voiceListening, voiceProcessing, voiceExerciseAmbiguity) {
        if (!voiceListening) return@LaunchedEffect
        val shouldPause = voiceProcessing || voiceExerciseAmbiguity != null
        if (shouldPause) {
            continuousSpeech.pause()
        } else {
            continuousSpeech.resume()
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            voiceUseCloudParser = PibbleNetwork.isOnline(context)
            voiceListening = true
            continuousSpeech.start(offlinePreferred = !voiceUseCloudParser)
        } else {
            voiceListening = false
            voiceStatus = "Permiso de micrófono denegado"
        }
    }

    fun maybeShowOfflineVoiceHint() {
        val online = PibbleNetwork.isOnline(context)
        if (online) {
            wasOnlineAtLastVoiceCheck = true
            return
        }
        val justWentOffline = wasOnlineAtLastVoiceCheck == true
        wasOnlineAtLastVoiceCheck = false

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastShown = prefs.getLong(KEY_VOICE_OFFLINE_HINT_LAST, 0L)
        val intervalElapsed = now - lastShown >= VOICE_OFFLINE_HINT_INTERVAL_MS

        if (justWentOffline || intervalElapsed) {
            Toast.makeText(
                context,
                "Estás usando el micrófono en modo offline. Conéctate a internet para un reconocimiento más preciso.",
                Toast.LENGTH_SHORT,
            ).show()
            prefs.edit().putLong(KEY_VOICE_OFFLINE_HINT_LAST, now).apply()
        }
    }

    fun toggleVoiceInput() {
        if (voiceListening) {
            voiceListening = false
            continuousSpeech.stop()
            voiceStatus = "Micrófono desactivado"
            return
        }
        maybeShowOfflineVoiceHint()
        voiceUseCloudParser = PibbleNetwork.isOnline(context)
        voiceStatus = if (voiceUseCloudParser) "Escuchando…" else "Escuchando (offline)…"
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> {
                voiceListening = true
                continuousSpeech.start(offlinePreferred = !voiceUseCloudParser)
            }
            else -> audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Pulso compartido para la marca destacada. Solo se crea una vez y la fila
    // resaltada es la única que lee .value, evitando recomponer toda la pantalla
    // cada frame mientras dura el highlight.
    val topSetTransition = rememberInfiniteTransition(label = "topSetPulse")
    val topMarkPulseState = topSetTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "topSetPulseValue",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        sessionTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { showProgramProgress = true }) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Progreso semanal del macrociclo"
                        )
                    }
                    IconButton(onClick = {
                        voiceListening = false
                        continuousSpeech.stop()
                        voiceStatus = null
                        note = ""
                        onOpenStats()
                    }) {
                        Icon(
                            Icons.Filled.Assignment,
                            contentDescription = "Ver estadísticas de sesión"
                        )
                    }
                    IconButton(onClick = { renameDraft = sessionTitle; showRename = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Renombrar sesión")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Session date sub-text
            session?.let {
                val dateStr = remember(it.dateTimestamp) {
                    dateFormatter.format(Date(it.dateTimestamp))
                }
                Text(
                    dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = {
                    editing = null
                    selectedExercise = null
                    reps = ""
                    weight = ""
                    rir = ""
                    note = ""
                    voiceListening = false
                    voiceStatus = null
                    showAdd = true
                },
                enabled = displaySets.size < SessionDetailViewModel.MAX_SETS_PER_SESSION,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Añadir serie", maxLines = 1, softWrap = false) }

            if (displaySets.size >= SessionDetailViewModel.MAX_SETS_PER_SESSION) {
                Text(
                    "Límite de 99 series alcanzado para esta sesión.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(setsScrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                displaySets.forEachIndexed { idx, set ->
                    key(set.id) {
                    val pulse = remember { Animatable(0f) }
                    val showReorderFlash = set.id in reorderHighlightIds
                    LaunchedEffect(showReorderFlash) {
                        if (showReorderFlash) {
                            pulse.snapTo(0f)
                            pulse.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
                            pulse.animateTo(0f, tween(400, easing = FastOutSlowInEasing))
                        } else {
                            pulse.snapTo(0f)
                        }
                    }
                    val showTopMarkFlash = set.id == topMarkHighlightId
                    val topMarkPulse = if (showTopMarkFlash) topMarkPulseState.value else 0f
                    val borderPulse = maxOf(pulse.value, topMarkPulse)
                    val cardShape = RoundedCornerShape(12.dp)
                    val swapPair = swappingAdjacentIds
                    val upperId = swapPair?.first
                    val lowerId = swapPair?.second
                    val hu = if (upperId != null) setRowHeightsPx[upperId] ?: defaultRowPx else defaultRowPx
                    val hl = if (lowerId != null) setRowHeightsPx[lowerId] ?: defaultRowPx else defaultRowPx
                    val p = swapProgress.value
                    val swapTy = when (set.id) {
                        upperId -> (hl + gapPx) * p
                        lowerId -> -(hu + gapPx) * p
                        else -> 0f
                    }
                    val swappingThis = swapPair != null && (set.id == upperId || set.id == lowerId)
                    val zDuringSwap = when {
                        !swappingThis -> 0f
                        lowerId != null && set.id == lowerId -> 3f
                        else -> 2f
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { setRowHeightsPx[set.id] = it.height.toFloat() }
                            .zIndex(zDuringSwap)
                            .graphicsLayer {
                                translationY = swapTy
                            }
                            .border(
                                width = (borderPulse * 3f).dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f + 0.55f * borderPulse),
                                shape = cardShape,
                            ),
                        shape = cardShape,
                    ) {
                        val ex = exerciseMap[set.exerciseId]
                        val primaryMuscleTag = ex?.muscleTags?.let { csv ->
                            ExerciseMuscleTags.primaryGroupKey(csv).takeIf {
                                it != ExerciseMuscleTags.GROUP_UNTAGGED
                            }
                        }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                (idx + 1).toString(),
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
                            Text(
                                ex?.name ?: "Ejercicio",
                                modifier = Modifier.padding(end = if (primaryMuscleTag != null) 36.dp else 0.dp),
                                fontWeight = FontWeight.SemiBold,
                            )
                            val tagLine = ex?.let { formatMuscleTagsLine(it.muscleTags) }.orEmpty()
                            if (tagLine.isNotBlank()) {
                                Text(tagLine, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            val wLabel = ProgressionCalculator.weightLabel(ex?.unitType ?: com.example.gymtrack.data.local.entities.UnitType.KG)
                            Text("${set.reps} reps • ${set.weight} $wLabel • RIR ${set.rir}")
                            if (set.note.isNotBlank()) {
                                Text(set.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(
                                    onClick = {
                                        if (swappingAdjacentIds != null) return@IconButton
                                        scope.launch {
                                            runAdjacentSwap(idx - 1) { vm.moveSetUpAwait(set) }
                                        }
                                    },
                                    enabled = idx > 0 && swappingAdjacentIds == null,
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Subir serie")
                                }
                                IconButton(
                                    onClick = {
                                        if (swappingAdjacentIds != null) return@IconButton
                                        scope.launch {
                                            runAdjacentSwap(idx) { vm.moveSetDownAwait(set) }
                                        }
                                    },
                                    enabled = idx < displaySets.lastIndex && swappingAdjacentIds == null,
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Bajar serie")
                                }
                                OutlinedButton(onClick = {
                                    editing = set
                                    selectedExercise = set.exerciseId
                                    reps   = set.reps.toString()
                                    weight = set.weight.toString()
                                    rir    = set.rir.toString()
                                    note   = set.note
                                    voiceListening = false
                                    voiceStatus = null
                                    showAdd = true
                                }) { Text("Editar", maxLines = 1, softWrap = false) }
                                OutlinedButton(onClick = { deleteSetTarget = set }) {
                                    Text("Eliminar", maxLines = 1, softWrap = false)
                                }
                            }
                            }
                            if (primaryMuscleTag != null) {
                                MuscleGroupIcon(
                                    tag = primaryMuscleTag,
                                    size = 30.dp,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 8.dp, end = 8.dp),
                                )
                            }
                        }
                    }
                    }
                }
            }
        }
    }

    // Confirm delete set dialog
    if (deleteSetTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteSetTarget = null },
            title = { Text("¿Eliminar serie?") },
            text  = { Text("¿Seguro que quieres eliminar esta serie? No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSet(deleteSetTarget!!)
                    deleteSetTarget = null
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error, maxLines = 1, softWrap = false) }
            },
            dismissButton = { TextButton(onClick = { deleteSetTarget = null }) { Text("Cancelar") } }
        )
    }

    // Rename dialog
    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Renombrar sesión") },
            text  = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    label = { Text("Nombre") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameDraft.isNotBlank(),
                    onClick = { vm.renameSession(renameDraft); showRename = false }
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancelar") } }
        )
    }

    if (showProgramProgress) {
        SessionProgramProgressDialog(
            groups = programProgress,
            onDismiss = { showProgramProgress = false },
            showPastWeekHint = programProgressIsPastWeek,
        )
    }

    // Add / Edit set dialog
    var showNoExerciseWarning by remember { mutableStateOf(false) }
    var showInvalidSetWarning by remember { mutableStateOf(false) }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = {
                voiceListening = false
                continuousSpeech.stop()
                voiceStatus = null
                note = ""
                showAdd = false
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (editing == null) "Añadir serie" else "Editar serie")
                    IconButton(onClick = { toggleVoiceInput() }) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Entrada por voz",
                            tint = when {
                                voiceProcessing -> MaterialTheme.colorScheme.tertiary
                                voiceListening -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                }
            },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    voiceStatus?.let { status ->
                        Text(
                            status,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    val selectedExerciseEntity = exercises.firstOrNull { it.id == selectedExercise }
                    Button(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedExerciseEntity?.name ?: "Seleccionar ejercicio")
                    }
                    OutlinedTextField(
                        value = reps,
                        onValueChange = { reps = it.filter(Char::isDigit) },
                        label = { Text("Reps") },
                        placeholder = { Text("8", color = demoPlaceholderColor) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    val selEx = exercises.firstOrNull { it.id == selectedExercise }
                    val wUnit = ProgressionCalculator.weightLabel(selEx?.unitType ?: com.example.gymtrack.data.local.entities.UnitType.KG)
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it.replace(',', '.') },
                        label = { Text("Peso ($wUnit)") },
                        placeholder = { Text("20", color = demoPlaceholderColor) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    OutlinedTextField(
                        value = rir,
                        onValueChange = { rir = it.filter(Char::isDigit) },
                        label = { Text("RIR") },
                        placeholder = { Text("2", color = demoPlaceholderColor) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Nota") },
                        placeholder = { Text("Opcional", color = demoPlaceholderColor) },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val exId = selectedExercise ?: 0L
                    if (exId == 0L) {
                        showNoExerciseWarning = true
                        return@TextButton
                    }
                    val repsVal = reps.toIntOrNull()
                    val weightVal = weight.toFloatOrNull()
                    val rirVal = rir.toIntOrNull()
                    if (repsVal == null || repsVal <= 0 || weightVal == null || weightVal <= 0f || rirVal == null) {
                        showInvalidSetWarning = true
                        return@TextButton
                    }
                    if (editing == null) {
                        vm.addSet(exId, repsVal, weightVal, rirVal, note)
                        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        if (prefs.getBoolean(TimerOverlayService.KEY_TIMER_AUTOSTART_AFTER_SET, false) &&
                            Settings.canDrawOverlays(context)
                        ) {
                            ContextCompat.startForegroundService(
                                context,
                                Intent(context, TimerOverlayService::class.java).apply {
                                    putExtra(TimerOverlayService.EXTRA_AUTOSTART, true)
                                },
                            )
                        }
                    } else {
                        val base = editing!!
                        vm.updateSet(
                            base.copy(
                                exerciseId = exId,
                                reps = repsVal,
                                weight = weightVal,
                                rir = rirVal,
                                note = note,
                            ),
                        )
                    }
                    voiceListening = false
                    continuousSpeech.stop()
                    voiceStatus = null
                    note = ""
                    showAdd = false
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    voiceListening = false
                    continuousSpeech.stop()
                    voiceStatus = null
                    note = ""
                    showAdd = false
                }) { Text("Cancelar") }
            }
        )
    }

    if (showInvalidSetWarning) {
        AlertDialog(
            onDismissRequest = { showInvalidSetWarning = false },
            title = { Text("Datos incompletos") },
            text  = { Text("Completa reps, peso y RIR antes de guardar la serie.") },
            confirmButton = {
                TextButton(onClick = { showInvalidSetWarning = false }) { Text("Entendido") }
            }
        )
    }

    if (showNoExerciseWarning) {
        AlertDialog(
            onDismissRequest = { showNoExerciseWarning = false },
            title = { Text("Sin ejercicio") },
            text  = { Text("Añade el ejercicio antes de guardar la serie.") },
            confirmButton = {
                TextButton(onClick = { showNoExerciseWarning = false }) { Text("Entendido") }
            }
        )
    }

    if (showPicker) {
        val filtered = remember(exercises, search) {
            if (search.isBlank()) exercises else exercises.filter { it.name.contains(search, true) }
        }
        val grouped = remember(filtered) { groupedExercisesByPrimaryTag(filtered) }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Seleccionar ejercicio") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Buscar") }, modifier = Modifier.fillMaxWidth())
                    LazyColumn(modifier = Modifier.height(280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        grouped.forEach { (groupKey, list) ->
                            item(key = "hdr-$groupKey") {
                                Column(Modifier.fillMaxWidth()) {
                                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                    Text(
                                        formatMuscleGroupSectionTitle(groupKey),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            items(list, key = { it.id }) { ex ->
                                Button(onClick = { selectedExercise = ex.id; showPicker = false }, modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text("${ex.name} (${if (ex.unitType == UnitType.KG) "Kg" else "Placas"})")
                                        val tl = formatMuscleTagsLine(ex.muscleTags)
                                        if (tl.isNotBlank()) {
                                            Text(
                                                tl,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPicker = false }) { Text("Cerrar") } }
        )
    }

    voiceExerciseAmbiguity?.let { candidates ->
        AlertDialog(
            onDismissRequest = { voiceExerciseAmbiguity = null },
            title = { Text("¿Qué ejercicio?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Encontré ${candidates.size} opciones parecidas. Elige una:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LazyColumn(
                        modifier = Modifier.height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(candidates, key = { it.id }) { candidate ->
                            Button(
                                onClick = {
                                    selectedExercise = candidate.id
                                    voiceExerciseAmbiguity = null
                                    voiceStatus = "Ejercicio: ${candidate.name}"
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(candidate.name)
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { voiceExerciseAmbiguity = null }) { Text("Cancelar") }
            },
            confirmButton = {},
        )
    }
}

// ─── ADD EXERCISE ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExerciseScreen(dao: GymDao, userName: String, onBack: () -> Unit) {
    val vm        = remember(userName) { SessionDetailViewModel(dao, -1L, userName) }
    val exercises by vm.exercises.collectAsStateSafe()
    var name by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(UnitType.KG) }
    var edit by remember { mutableStateOf<ExerciseEntity?>(null) }
    var deleteExTarget by remember { mutableStateOf<ExerciseEntity?>(null) }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var showMuscleTagPicker by remember { mutableStateOf(false) }
    val exerciseScroll = rememberScrollState()
    val nameFocus = remember { FocusRequester() }

    LaunchedEffect(edit?.id) {
        selectedTags = ExerciseMuscleTags.fromCsv(edit?.muscleTags)
        if (edit != null) {
            exerciseScroll.animateScrollTo(0)
            runCatching { nameFocus.requestFocus() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Añadir ejercicio") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(exerciseScroll),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre ejercicio") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(nameFocus),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { unit = UnitType.KG },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (unit == UnitType.KG) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor   = if (unit == UnitType.KG) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) { Text("Kg") }
                Button(
                    onClick = { unit = UnitType.PLATES },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (unit == UnitType.PLATES) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor   = if (unit == UnitType.PLATES) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) { Text("Placas") }
            }
            Text("Etiquetas (grupo muscular)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            OutlinedButton(
                onClick = { showMuscleTagPicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val primaryTag = ExerciseMuscleTags.ALL.firstOrNull { it in selectedTags }
                        if (primaryTag != null) {
                            MuscleGroupIcon(
                                tag = primaryTag,
                                size = 28.dp,
                            )
                        }
                        Text(
                            MuscleTagUi.selectedTagsSummary(selectedTags),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    val csv = ExerciseMuscleTags.toCsv(selectedTags)
                    if (edit == null) {
                        vm.addExercise(name.trim(), unit, selectedTags)
                    } else {
                        vm.updateExercise(edit!!.copy(name = name.trim(), unitType = unit, muscleTags = csv))
                    }
                    edit = null
                    name = ""
                    selectedTags = emptySet()
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (edit == null) "Guardar" else "Actualizar", maxLines = 1, softWrap = false) }

            HorizontalDivider()

            exercises.forEach { ex ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${ex.name} (${if (ex.unitType == UnitType.KG) "Kg" else "Placas"})")
                            val tl = formatMuscleTagsLine(ex.muscleTags)
                            if (tl.isNotBlank()) {
                                Text(tl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                edit = ex
                                name = ex.name
                                unit = ex.unitType
                                selectedTags = ExerciseMuscleTags.fromCsv(ex.muscleTags)
                            }) { Text("Editar", maxLines = 1, softWrap = false) }
                            OutlinedButton(onClick = { deleteExTarget = ex }) {
                                Text("Eliminar", maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMuscleTagPicker) {
        AlertDialog(
            onDismissRequest = { showMuscleTagPicker = false },
            title = { Text("Grupos musculares") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(ExerciseMuscleTags.ALL, key = { it }) { tag ->
                        val selected = tag in selectedTags
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                )
                                .clickable {
                                    selectedTags =
                                        if (selected) selectedTags - tag else selectedTags + tag
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            MuscleGroupIcon(
                                tag = tag,
                                size = 36.dp,
                            )
                            Text(
                                MuscleTagUi.displayName(tag),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMuscleTagPicker = false }) { Text("Listo") }
            },
        )
    }

    // Confirm delete exercise dialog
    if (deleteExTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteExTarget = null },
            title = { Text("¿Eliminar ejercicio?") },
            text  = { Text("¿Seguro que quieres eliminar \"${deleteExTarget!!.name}\"? Se perderán todas sus series registradas.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteExercise(deleteExTarget!!)
                    deleteExTarget = null
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error, maxLines = 1, softWrap = false) }
            },
            dismissButton = { TextButton(onClick = { deleteExTarget = null }) { Text("Cancelar") } }
        )
    }
}

// ─── SETTINGS ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    dao: GymDao,
    userName: String,
    onOpenPibbleHelp: () -> Unit,
    nutritionMode: Boolean = false,
    catalogDao: OffCatalogDao,
) {
    val context = LocalContext.current
    val prefs   = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val scope   = rememberCoroutineScope()

    // ── Guardar cuenta ────────────────────────────────────────────────────────
    var saveMsg by remember { mutableStateOf<String?>(null) }
    var showSaveMenu by remember { mutableStateOf(false) }
    var cacheUsedFoods by remember { mutableStateOf(OffCatalogPrefs.isEnabled(context)) }
    var confirmClearCache by remember { mutableStateOf(false) }
    val cachedFoodCount by catalogDao.observeCount().collectAsState(initial = 0)
    var hasQuickSave by remember(userName) {
        mutableStateOf(AccountSavePreferences.hasSavedOnce(prefs, userName))
    }

    fun performSave(uri: Uri, rememberLocation: Boolean) {
        scope.launch {
            val ok = BackupManager.exportToUri(context, uri, dao, userName)
            if (ok) {
                if (rememberLocation) {
                    AccountSavePreferences.rememberSaveLocation(context, prefs, userName, uri)
                    hasQuickSave = true
                }
                saveMsg = "Cuenta guardada correctamente."
            } else {
                saveMsg = "Error al guardar la cuenta."
            }
        }
    }

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let { performSave(it, rememberLocation = true) }
    }

    if (showSaveMenu) {
        AlertDialog(
            onDismissRequest = { showSaveMenu = false },
            title = { Text("Guardar cuenta") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasQuickSave) {
                        Button(
                            onClick = {
                                showSaveMenu = false
                                AccountSavePreferences.getSavedUri(prefs, userName)?.let { uri ->
                                    performSave(uri, rememberLocation = false)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = saveAccountButtonColors,
                        ) { Text("Guardar") }
                    }
                    Button(
                        onClick = {
                            showSaveMenu = false
                            val ts = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                            saveAsLauncher.launch("GymTrack_${userName}_$ts.json")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = saveAccountButtonColors,
                    ) { Text("Guardar como...") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSaveMenu = false }) { Text("Cancelar") }
            },
        )
    }

    if (confirmClearCache) {
        AlertDialog(
            onDismissRequest = { confirmClearCache = false },
            title = { Text("Borrar alimentos locales") },
            text = {
                Text(
                    "Se eliminarán los $cachedFoodCount alimentos guardados en este móvil. " +
                        "La búsqueda seguirá funcionando online. El diario de comidas no se borra.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearCache = false
                        scope.launch { OffUsedFoods.clear(context, catalogDao) }
                    },
                ) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearCache = false }) { Text("Cancelar") }
            },
        )
    }

    var minutes by rememberSaveable {
        mutableStateOf((prefs.getLong(TimerOverlayService.KEY_TIMER_MS, 300_000L) / 60_000L).toString())
    }
    var seconds by rememberSaveable {
        mutableStateOf(((prefs.getLong(TimerOverlayService.KEY_TIMER_MS, 300_000L) / 1000L) % 60).toString())
    }
    var vibrate by rememberSaveable {
        mutableStateOf(prefs.getBoolean(TimerOverlayService.KEY_TIMER_VIBRATE, true))
    }
    var soundUri by rememberSaveable {
        mutableStateOf(prefs.getString(TimerOverlayService.KEY_TIMER_SOUND_URI, "") ?: "")
    }
    var soundName by rememberSaveable {
        mutableStateOf(prefs.getString(TimerOverlayService.KEY_TIMER_SOUND_NAME, "Por defecto") ?: "Por defecto")
    }
    var timerAutostartAfterSet by rememberSaveable {
        mutableStateOf(prefs.getBoolean(TimerOverlayService.KEY_TIMER_AUTOSTART_AFTER_SET, false))
    }
    var savedMinutes  by rememberSaveable { mutableStateOf(minutes) }
    var savedSeconds  by rememberSaveable { mutableStateOf(seconds) }
    var savedVibrate  by rememberSaveable { mutableStateOf(vibrate) }
    var savedSoundUri by rememberSaveable { mutableStateOf(soundUri) }
    var savedTimerAutostart by rememberSaveable { mutableStateOf(timerAutostartAfterSet) }

    val hasChanges = minutes != savedMinutes || seconds != savedSeconds ||
            vibrate != savedVibrate || soundUri != savedSoundUri ||
            timerAutostartAfterSet != savedTimerAutostart

    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                soundUri  = uri.toString()
                soundName = RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: "Sonido personalizado"
            } else {
                soundUri  = ""
                soundName = "Por defecto"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Text("Tema de la app", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                HintText("Modo oscuro recomendado.")
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onDarkModeChange(false) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!darkMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor   = if (!darkMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) { Text("Claro") }
                    Button(
                        onClick = { onDarkModeChange(true) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (darkMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor   = if (darkMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) { Text("Oscuro") }
                }
            }

            if (nutritionMode) {
                item { HorizontalDivider() }
                item {
                    Text("Alimentos usados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    HintText(
                        "La búsqueda usa Open Food Facts online. Si lo activas, se guardan en el móvil " +
                            "los alimentos que añades para encontrarlos antes y sin internet.",
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Guardar alimentos usados", fontWeight = FontWeight.SemiBold)
                            HintText(
                                if (cacheUsedFoods) "Activo. $cachedFoodCount guardados."
                                else "Desactivado. No se guarda nada nuevo.",
                            )
                        }
                        Switch(
                            checked = cacheUsedFoods,
                            onCheckedChange = { enabled ->
                                cacheUsedFoods = enabled
                                OffCatalogPrefs.setEnabled(context, enabled)
                            },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { confirmClearCache = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = cachedFoodCount > 0,
                    ) { Text("Borrar datos locales") }
                }
            }

            item { HorizontalDivider() }

            item {
                Text(stringResource(R.string.pibble_settings_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                HintText(stringResource(R.string.pibble_settings_blurb))
                Spacer(Modifier.height(8.dp))
                val settingsContext = LocalContext.current
                Button(
                    onClick = {
                        if (PibbleNetwork.isOnline(settingsContext)) {
                            onOpenPibbleHelp()
                        } else {
                            Toast.makeText(
                                settingsContext,
                                settingsContext.getString(R.string.pibble_no_internet),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.pibble_open)) }
            }

            if (!nutritionMode) {
                item { HorizontalDivider() }

                item {
                    Text("Tiempo del temporizador", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(value = minutes, onValueChange = { minutes = it.filter(Char::isDigit).take(2) }, label = { Text("Min") }, modifier = Modifier.weight(1f))
                        Text(":", style = MaterialTheme.typography.headlineMedium)
                        OutlinedTextField(value = seconds, onValueChange = { seconds = it.filter(Char::isDigit).take(2) }, label = { Text("Seg") }, modifier = Modifier.weight(1f))
                    }
                }

                item { HorizontalDivider() }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Temporizador al añadir serie", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            HintText("Al guardar serie, inicia temporizador (permiso superposición).")
                        }
                        Switch(checked = timerAutostartAfterSet, onCheckedChange = { timerAutostartAfterSet = it })
                    }
                }

                item { HorizontalDivider() }

                item {
                    Text("Sonido al finalizar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("Seleccionado: $soundName", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = {
                            ringtoneLauncher.launch(
                                Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Elegir timbre")
                                    if (soundUri.isNotBlank()) putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(soundUri))
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Elegir desde mis audios") }
                }

                item { HorizontalDivider() }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Vibrar al finalizar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            HintText("El dispositivo vibrará al terminar")
                        }
                        Switch(checked = vibrate, onCheckedChange = { vibrate = it })
                    }
                }
            }

            item { HorizontalDivider() }

            item {
                if (!nutritionMode) {
                    Button(
                        enabled  = hasChanges,
                        onClick  = {
                            val m = minutes.toIntOrNull() ?: 0
                            val s = (seconds.toIntOrNull() ?: 0).coerceIn(0, 59)
                            prefs.edit()
                                .putLong(TimerOverlayService.KEY_TIMER_MS, ((m * 60L + s) * 1000L).coerceIn(5_000L, 3_600_000L))
                                .putString(TimerOverlayService.KEY_TIMER_SOUND_URI, soundUri)
                                .putString(TimerOverlayService.KEY_TIMER_SOUND_NAME, soundName)
                                .putBoolean(TimerOverlayService.KEY_TIMER_VIBRATE, vibrate)
                                .putBoolean(TimerOverlayService.KEY_TIMER_AUTOSTART_AFTER_SET, timerAutostartAfterSet)
                                .apply()
                            savedMinutes = minutes; savedSeconds = seconds
                            savedVibrate = vibrate; savedSoundUri = soundUri
                            savedTimerAutostart = timerAutostartAfterSet
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Guardar ajustes") }
                    Spacer(Modifier.height(4.dp))
                }

                // ── Guardar / duplicar cuenta ─────────────────────────────────
                Button(
                    onClick = { showSaveMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Guardar cuenta") }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        scope.launch {
                            val json = BackupManager.accountShareJson(dao, userName)
                            GymTrackShare.shareJson(
                                context,
                                json,
                                "GymTrack_Cuenta_${userName}.json",
                                "Compartir cuenta",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Compartir cuenta") }
                saveMsg?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Cerrar sesión") }
            }
        }
    }
}

// ─── DASHBOARD ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DashboardScreen(
    dao: GymDao,
    vm: DashboardViewModel,
    userName: String,
    onOpenSessionFromStats: (Long, Long) -> Unit,
) {
    val exercises      by dao.observeExercises(userName).collectAsState(initial = emptyList())
    val sessions       by dao.observeSessions(userName).collectAsState(initial = emptyList())
    val points         by vm.points.collectAsStateSafe()
    val top            by vm.topPerformanceSets.collectAsStateSafe()
    val sessionBands   by vm.sessionBands.collectAsStateSafe()
    val chartPeriod    by vm.chartPeriod.collectAsStateSafe()
    val selectedSessId by vm.selectedSessionId.collectAsStateSafe()
    val totalSetsScope by vm.totalSetsInSelectedScope.collectAsStateSafe()
    val avgRirScope    by vm.avgRirInSelectedScope.collectAsStateSafe()
    val bestRmScope    by vm.bestOneRmInSelectedScope.collectAsStateSafe()
    val weightUnitScope by vm.weightUnitForSelectedScope.collectAsStateSafe()
    val fullViewMode by vm.fullViewMode.collectAsStateSafe()
    val forecastOverlayLines by vm.forecastOverlayLines.collectAsStateSafe()
    val advancedProgram by vm.advancedProgramProgress.collectAsStateSafe()

    val context = LocalContext.current

    val selectedExIdVm by vm.selectedExerciseId.collectAsStateSafe()
    val selectedGroupKey by vm.selectedMuscleGroupKey.collectAsStateSafe()
    val selectedSessionIds by vm.selectedSessionIds.collectAsStateSafe()

    var sessionExerciseIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    LaunchedEffect(selectedSessId, selectedSessionIds) {
        when {
            selectedSessionIds.isNotEmpty() -> {
                dao.observeExerciseIdsInSessions(selectedSessionIds).collect { sessionExerciseIds = it.toSet() }
            }
            selectedSessId != null -> {
                dao.observeExerciseIdsInSession(selectedSessId!!).collect { sessionExerciseIds = it.toSet() }
            }
            else -> {
                sessionExerciseIds = emptySet()
            }
        }
    }

    LaunchedEffect(selectedSessionIds) {
        if (selectedSessionIds.isEmpty() && selectedSessId == null) {
            sessionExerciseIds = emptySet()
        }
    }

    val pickerExercises = remember(exercises, sessionExerciseIds, selectedSessId, selectedSessionIds) {
        if (selectedSessId == null && selectedSessionIds.isEmpty()) exercises
        else exercises.filter { it.id in sessionExerciseIds }.sortedBy { it.name }
    }

    var showExPicker   by remember { mutableStateOf(false) }
    var showSessPicker by remember { mutableStateOf(false) }
    var q              by rememberSaveable { mutableStateOf("") }
    var qs             by rememberSaveable { mutableStateOf("") }
    var sessionMultiSelect by remember { mutableStateOf(false) }
    var sessionSelectionDraft by remember { mutableStateOf<Set<Long>>(emptySet()) }

    LaunchedEffect(showSessPicker) {
        if (showSessPicker) {
            sessionMultiSelect = selectedSessionIds.isNotEmpty()
            sessionSelectionDraft = selectedSessionIds.toSet()
        }
    }

    LaunchedEffect(selectedExIdVm) {
        selectedExIdVm?.let { id ->
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(dashboardExerciseKey(userName), id)
                .apply()
        }
    }

    LaunchedEffect(pickerExercises, selectedExIdVm, selectedGroupKey, selectedSessId, selectedSessionIds) {
        if (selectedGroupKey != null) {
            val stillValid = pickerExercises.any { ExerciseMuscleTags.primaryGroupKey(it.muscleTags) == selectedGroupKey }
            if (!stillValid) vm.setMuscleGroup(null)
            return@LaunchedEffect
        }
        if (pickerExercises.isEmpty()) return@LaunchedEffect

        val lastSavedExerciseId = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(dashboardExerciseKey(userName), -1L)
            .takeIf { it >= 0 }

        if (selectedExIdVm != null && pickerExercises.none { it.id == selectedExIdVm }) {
            val target = pickerExercises.firstOrNull { it.id == lastSavedExerciseId }
                ?: pickerExercises.first()
            vm.setExercise(target.id)
            return@LaunchedEffect
        }

        if (selectedExIdVm == null) {
            val target = pickerExercises.firstOrNull { it.id == lastSavedExerciseId }
                ?: pickerExercises.first()
            vm.setExercise(target.id)
        }
    }

    val dateFmt   = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val isComparison = selectedSessionIds.isNotEmpty()
    val isGeneral = selectedSessId == null && !isComparison
    val periodLabels = listOf("Diario", "Semanal", "Mensual", "Anual")
    val scopeLabel = when {
        isComparison -> "Comparación"
        isGeneral -> periodLabels[chartPeriod.ordinal]
        else -> "Sesión"
    }
    val wLabelRm = ProgressionCalculator.weightLabel(weightUnitScope)

    val showForecastOverlay = fullViewMode && forecastOverlayLines.isNotEmpty()

    var selectedChartPoint by remember { mutableStateOf<ProgressPoint?>(null) }
    LaunchedEffect(selectedExIdVm, selectedGroupKey, selectedSessId, selectedSessionIds, chartPeriod) {
        selectedChartPoint = null
    }
    LaunchedEffect(points) {
        val id = selectedChartPoint?.raw?.setId ?: return@LaunchedEffect
        if (points.none { it.raw.setId == id }) selectedChartPoint = null
    }

    val platesPoints = remember(points, selectedGroupKey) {
        if (selectedGroupKey == null) emptyList()
        else points.filter { it.raw.unitType == UnitType.PLATES }
    }
    val kgPoints = remember(points, selectedGroupKey) {
        if (selectedGroupKey == null) emptyList()
        else points.filter { it.raw.unitType == UnitType.KG }
    }
    val platesBands = remember(platesPoints) { computeSessionBands(platesPoints) }
    val kgBands = remember(kgPoints) { computeSessionBands(kgPoints) }
    val splitByUnit = selectedGroupKey != null && (platesPoints.isNotEmpty() || kgPoints.isNotEmpty())

    val topMarks = remember(top, selectedChartPoint) {
        selectedChartPoint?.let { listOf(it) } ?: top
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // ── Row: selector ejercicio + selector sesión ─────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick  = { showExPicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        when {
                            selectedGroupKey != null ->
                                "Grupo: ${formatMuscleGroupSectionTitle(selectedGroupKey!!)}"
                            else ->
                                pickerExercises.firstOrNull { it.id == selectedExIdVm }?.name ?: "Ejercicio"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(onClick = { showSessPicker = true }) {
                    Text(
                        when {
                            isComparison -> "Comparar (${selectedSessionIds.size})"
                            isGeneral -> "General"
                            else -> sessions.firstOrNull { it.id == selectedSessId }?.customName?.take(10) ?: "Sesión"
                        },
                        maxLines = 1,
                    )
                }
            }
        }

        item {
            val calcUnit = exercises.firstOrNull { it.id == selectedExIdVm }?.unitType ?: weightUnitScope
            OneRmCalculatorCard(selectedUnitType = calcUnit)
        }

        // ── Período + vista completa + resumen ───────────────────────────────
        item {
            if (isGeneral) {
                Text("Período", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                TabRow(selectedTabIndex = chartPeriod.ordinal) {
                    ProgressionCalculator.ChartPeriod.entries.forEachIndexed { i, p ->
                        Tab(
                            selected = chartPeriod == p,
                            onClick  = { vm.setChartPeriod(p) },
                            text     = { Text(periodLabels[i], style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Estadística de macrociclo",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    HintText(
                        "Proyección en gráfica y volumen/RIR vs programación activa.",
                    )
                }
                Switch(
                    checked = fullViewMode,
                    onCheckedChange = { vm.setFullViewMode(it) },
                )
            }
            if (avgRirScope != null || bestRmScope != null) {
                Spacer(Modifier.height(8.dp))
                Text("Resumen ($scopeLabel)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    avgRirScope?.let { v ->
                        Text(
                            "RIR promedio: ${"%.1f".format(v)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    bestRmScope?.let { rm ->
                        Text(
                            "1RM estimado: ${"%.1f".format(rm)} $wLabelRm",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        item { HorizontalDivider() }

        // ── Gráfica Vico ──────────────────────────────────────────────────────
        item {
            if (points.isEmpty()) {
                Text(
                    "Sin datos para este período.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (splitByUnit) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (platesPoints.isNotEmpty()) {
                        Text(
                            "Placas",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        SessionProgressChart(
                            points = platesPoints,
                            bands = platesBands,
                            selectedSetId = selectedChartPoint?.raw?.setId,
                            onPointSelected = { selectedChartPoint = it },
                        )
                    }
                    if (kgPoints.isNotEmpty()) {
                        Text(
                            "Kg",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        SessionProgressChart(
                            points = kgPoints,
                            bands = kgBands,
                            selectedSetId = selectedChartPoint?.raw?.setId,
                            onPointSelected = { selectedChartPoint = it },
                        )
                    }
                }
            } else {
                SessionProgressChart(
                    points = points,
                    bands = sessionBands,
                    forecastOverlayLines = if (showForecastOverlay) forecastOverlayLines else emptyList(),
                    showForecastOverlay = showForecastOverlay,
                    selectedSetId = selectedChartPoint?.raw?.setId,
                    onPointSelected = { selectedChartPoint = it },
                )
            }
        }

        if (fullViewMode) {
            item {
                Text(
                    "Estadística de macrociclo",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                advancedProgram.periodNote?.let { note ->
                    Spacer(Modifier.height(4.dp))
                    HintText(note)
                }
                if (advancedProgram.progress.isEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    HintText(
                        "Activa macrociclo y elige ej. o grupo del ciclo.",
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                    SessionProgramVolumeBars(
                        groups = advancedProgram.progress,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item {
            Text(
                "Series totales ($scopeLabel): $totalSetsScope",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }

        // ── Top marcas (clickables → navegan a sesión) ────────────────────────
        if (topMarks.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (selectedChartPoint != null) "Serie seleccionada" else "Top marcas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
            }
            itemsIndexed(
                items = topMarks,
                key = { _, p -> "top_${p.raw.setId}" },
            ) { i, p ->
                val wLabel = ProgressionCalculator.weightLabel(p.raw.unitType)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSessionFromStats(p.raw.sessionId, p.raw.setId) },
                    colors = CardDefaults.cardColors(
                        containerColor = run {
                            val darkBg = MaterialTheme.colorScheme.background.luminance() < 0.5f
                            when (i) {
                                0 -> if (darkBg) Color(0xFF6E5400) else Color(0xFFFFD54F) // oro
                                1 -> if (darkBg) Color(0xFF595959) else Color(0xFFC0C0C0) // plata
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (selectedChartPoint != null) "Serie" else "#${i + 1}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "1RM ${"%.1f".format(p.y)} ${ProgressionCalculator.weightLabel(p.raw.unitType)}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        Text(p.raw.exerciseName.ifBlank {
                            exercises.firstOrNull { it.id == p.raw.exerciseId }?.name ?: "Ejercicio"
                        }, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${p.raw.reps} reps • ${p.raw.weight} $wLabel • RIR ${p.raw.rir}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (p.raw.note.isNotBlank()) {
                            Text(
                                p.raw.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (p.raw.sessionName.isNotBlank()) {
                            Text(p.raw.sessionName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val groupPos = remember(p.raw.setId, p.raw.sessionId, p.raw.exerciseId, exercises) {
                            val sessionSets = SessionDataCache.setsFor(p.raw.sessionId)
                            muscleGroupPositionForSet(
                                setId = p.raw.setId,
                                sessionSets = sessionSets,
                                exercises = exercises,
                                exerciseId = p.raw.exerciseId,
                            )
                        }
                        val groupSuffix = groupPos
                            ?.let { (pos, name) -> " • ${spanishFeminineOrdinal(pos)} de ${name}" }
                            .orEmpty()
                        Text(
                            "${dateFmt.format(Date(p.raw.sessionDate))} • Serie #${p.raw.setOrder}${groupSuffix}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("Toca para abrir esta sesión", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    // ── Picker de ejercicio ───────────────────────────────────────────────────
    if (showExPicker) {
        val filtered = remember(pickerExercises, q) {
            if (q.isBlank()) pickerExercises else pickerExercises.filter { it.name.contains(q, true) }
        }
        val grouped = remember(filtered) { groupedExercisesByPrimaryTag(filtered) }
        AlertDialog(
            onDismissRequest = { showExPicker = false },
            title = { Text("Elige un ejercicio") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = q, onValueChange = { q = it }, label = { Text("Buscar") }, modifier = Modifier.fillMaxWidth())
                    LazyColumn(modifier = Modifier.height(280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        grouped.forEach { (groupKey, list) ->
                            item(key = "ex-h-$groupKey") {
                                Column(
                                    Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                    Text(
                                        formatMuscleGroupSectionTitle(groupKey),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    FilledTonalButton(
                                        onClick = {
                                            vm.setMuscleGroup(groupKey)
                                            showExPicker = false
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            "Comparar ${formatMuscleGroupSectionTitle(groupKey)} (${list.size})",
                                            maxLines = 2,
                                        )
                                    }
                                }
                            }
                            items(list, key = { it.id }) { ex ->
                                Button(
                                    onClick = {
                                        vm.setMuscleGroup(null)
                                        vm.setExercise(ex.id)
                                        showExPicker = false
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(ex.name)
                                        val tl = formatMuscleTagsLine(ex.muscleTags)
                                        if (tl.isNotBlank()) {
                                            Text(
                                                tl,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showExPicker = false }) { Text("Cerrar") } }
        )
    }

    // ── Picker de sesión ──────────────────────────────────────────────────────
    if (showSessPicker) {
        val filteredSess = remember(sessions, qs) {
            if (qs.isBlank()) sessions else sessions.filter { it.customName.contains(qs, true) }
        }
        AlertDialog(
            onDismissRequest = { showSessPicker = false },
            title = { Text(if (sessionMultiSelect) "Comparar sesiones" else "Modo de vista") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick  = {
                            vm.setSession(null)
                            sessionMultiSelect = false
                            sessionSelectionDraft = emptySet()
                            showSessPicker = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("General (todo el historial)") }
                    if (!sessionMultiSelect) {
                        Text(
                            "Mantén presionada una sesión para seleccionar varias.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "${sessionSelectionDraft.size} seleccionada(s). Toca para agregar o quitar.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    HorizontalDivider()
                    OutlinedTextField(value = qs, onValueChange = { qs = it }, label = { Text("Buscar sesión") }, modifier = Modifier.fillMaxWidth())
                    LazyColumn(modifier = Modifier.height(220.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(items = filteredSess, key = { it.id }) { s ->
                            val selected = s.id in sessionSelectionDraft
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (sessionMultiSelect) {
                                                val next = if (selected) {
                                                    sessionSelectionDraft - s.id
                                                } else {
                                                    sessionSelectionDraft + s.id
                                                }
                                                sessionSelectionDraft = next
                                                if (next.isEmpty()) {
                                                    vm.setSession(null)
                                                    sessionMultiSelect = false
                                                }
                                            } else {
                                                vm.setSession(s.id)
                                                showSessPicker = false
                                            }
                                        },
                                        onLongClick = {
                                            sessionMultiSelect = true
                                            sessionSelectionDraft = sessionSelectionDraft + s.id
                                        },
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                ),
                                border = BorderStroke(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.75f)
                                    },
                                ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.Start,
                                    ) {
                                        Text(s.customName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            dateFmt.format(Date(s.dateTimestamp)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (selected) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (sessionMultiSelect) {
                        TextButton(
                            enabled = sessionSelectionDraft.size >= 2,
                            onClick = {
                                vm.setSessionComparison(
                                    sessions
                                        .filter { it.id in sessionSelectionDraft }
                                        .sortedBy { it.dateTimestamp }
                                        .map { it.id },
                                )
                                showSessPicker = false
                            },
                        ) { Text("Ver comparación") }
                    }
                    TextButton(onClick = { showSessPicker = false }) { Text("Cerrar") }
                }
            }
        )
    }
}
