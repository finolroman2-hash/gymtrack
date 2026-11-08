package com.example.gymtrack.ui.training

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.example.gymtrack.backup.BackupManager
import com.example.gymtrack.backup.GymTrackShare
import com.example.gymtrack.backup.ProgramLibrary
import com.example.gymtrack.backup.SavedProgram
import com.example.gymtrack.data.local.entities.TrainingCycleEntity
import com.example.gymtrack.data.local.entities.TrainingCycleGroupEntity
import com.example.gymtrack.domain.ActiveCycleStatus
import com.example.gymtrack.domain.CycleKind
import com.example.gymtrack.domain.CycleTimelineItem
import com.example.gymtrack.ui.common.HintText
import com.example.gymtrack.domain.TrainingCycleCalculator
import com.example.gymtrack.ui.sessiondetail.SessionProgramProgressDialog
import androidx.compose.runtime.collectAsState
import com.example.gymtrack.ui.common.collectAsStateSafe
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.max

@Composable
fun TrainingProgramScreen(
    vm: TrainingProgramViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val programs by vm.programs.collectAsStateSafe()
    val clipboard by vm.groupClipboard.collectAsStateSafe()
    val weeklyProgress by vm.weeklyProgress.collectAsStateSafe()

    var showAddMacro by remember { mutableStateOf(false) }
    var showWeeklyProgress by remember { mutableStateOf(false) }
    var showSaveProgram by remember { mutableStateOf(false) }
    var showLoadProgram by remember { mutableStateOf(false) }
    var saveTargetId by remember { mutableStateOf<Long?>(null) }
    var savedPrograms by remember { mutableStateOf<List<SavedProgram>>(emptyList()) }
    var loadTargetFile by remember { mutableStateOf<String?>(null) }
    var macroNameDraft by rememberSaveable { mutableStateOf("") }
    var addCycleMacroId by remember { mutableStateOf<Long?>(null) }
    var editingCycle by remember { mutableStateOf<TrainingCycleEntity?>(null) }
    var deleteMacroTarget by remember { mutableStateOf<MacrocycleProgram?>(null) }
    var deleteCycleTarget by remember { mutableStateOf<CycleTimelineItem?>(null) }
    var activationTarget by remember { mutableStateOf<MacrocycleProgram?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Programación de entrenamiento",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    saveTargetId = programs.firstOrNull { it.macro.isActive }?.macro?.id
                        ?: programs.firstOrNull()?.macro?.id
                    showSaveProgram = true
                },
                enabled = programs.isNotEmpty(),
            ) {
                Icon(Icons.Default.Save, contentDescription = "Guardar programación")
            }
            IconButton(
                onClick = {
                    savedPrograms = ProgramLibrary.list(context)
                    loadTargetFile = savedPrograms.firstOrNull()?.fileName
                    showLoadProgram = true
                },
            ) {
                Icon(Icons.Default.Folder, contentDescription = "Cargar programación")
            }
            IconButton(onClick = { showWeeklyProgress = true }) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Progreso semanal del macrociclo",
                )
            }
        }

        Button(
            onClick = {
                macroNameDraft = ""
                showAddMacro = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Añadir macrociclo")
        }

        val activeProgram = remember(programs) { programs.firstOrNull { it.macro.isActive } }
        val activeStatus = remember(activeProgram) {
            activeProgram?.let {
                TrainingCycleCalculator.resolveActiveCycleStatus(it.timeline, it.macro)
            }
        }

        if (programs.isEmpty()) {
            ActiveCycleProgressCard(
                status = activeStatus,
                macroName = activeProgram?.macro?.name,
                timeline = activeProgram?.timeline.orEmpty(),
                startDayInWeek = activeProgram?.macro?.startDayInWeek ?: 1,
                activatedAt = activeProgram?.macro?.activatedAt ?: 0L,
            )
            HintText("Sin macrociclos. Pulsa arriba para crear uno.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
            ) {
                // Scrollable: al bajar la lista se deja atrás (no sticky).
                item(key = "cycle-progress") {
                    ActiveCycleProgressCard(
                        status = activeStatus,
                        macroName = activeProgram?.macro?.name,
                        timeline = activeProgram?.timeline.orEmpty(),
                        startDayInWeek = activeProgram?.macro?.startDayInWeek ?: 1,
                        activatedAt = activeProgram?.macro?.activatedAt ?: 0L,
                    )
                }
                items(programs, key = { it.macro.id }) { program ->
                    MacrocycleCard(
                        program = program,
                        vm = vm,
                        onAddCycle = { addCycleMacroId = program.macro.id },
                        onEditCycle = { editingCycle = it.entity },
                        onDeleteMacro = { deleteMacroTarget = program },
                        onDeleteCycle = { deleteCycleTarget = it },
                        onShare = {
                            scope.launch {
                                val json = vm.exportMacrocycleJson(program.macro.id)
                                if (json == null) {
                                    Toast.makeText(context, "No se pudo compartir.", Toast.LENGTH_SHORT).show()
                                } else {
                                    val safe = program.macro.name.replace(Regex("[^A-Za-z0-9_-]+"), "_")
                                    GymTrackShare.shareJson(
                                        context,
                                        json,
                                        "GymTrack_Macro_$safe.json",
                                        "Compartir macrociclo",
                                    )
                                }
                            }
                        },
                        onToggleActive = { active ->
                            if (active) {
                                activationTarget = program
                            } else {
                                vm.deactivateMacrocycle()
                            }
                        },
                    )
                }
            }
        }
    }

    if (showAddMacro) {
        AlertDialog(
            onDismissRequest = { showAddMacro = false },
            title = { Text("Nuevo macrociclo") },
            text = {
                OutlinedTextField(
                    value = macroNameDraft,
                    onValueChange = { macroNameDraft = it },
                    label = { Text("Nombre") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addMacrocycle(macroNameDraft)
                    showAddMacro = false
                }) { Text("Crear") }
            },
            dismissButton = {
                TextButton(onClick = { showAddMacro = false }) { Text("Cancelar") }
            },
        )
    }

    if (showWeeklyProgress) {
        SessionProgramProgressDialog(
            groups = weeklyProgress,
            onDismiss = { showWeeklyProgress = false },
            emptyMessage = "Activa un macrociclo y configura grupos en el ciclo en curso.",
        )
    }

    if (showSaveProgram) {
        AlertDialog(
            onDismissRequest = { showSaveProgram = false },
            title = { Text("Guardar programación") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Elige el macrociclo. Quedará guardado para cargarlo en otras cuentas.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    programs.forEach { program ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { saveTargetId = program.macro.id }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RadioButton(
                                selected = saveTargetId == program.macro.id,
                                onClick = { saveTargetId = program.macro.id },
                            )
                            Text(program.macro.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = saveTargetId != null,
                    onClick = {
                        val id = saveTargetId ?: return@TextButton
                        val name = programs.find { it.macro.id == id }?.macro?.name ?: "Macrociclo"
                        showSaveProgram = false
                        scope.launch {
                            val json = vm.exportMacrocycleJson(id)
                            val ok = json != null && ProgramLibrary.save(context, json, name)
                            Toast.makeText(
                                context,
                                if (ok) "Programación guardada." else "No se pudo guardar.",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveProgram = false }) { Text("Cancelar") }
            },
        )
    }

    if (showLoadProgram) {
        AlertDialog(
            onDismissRequest = { showLoadProgram = false },
            title = { Text("Cargar programación") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (savedPrograms.isEmpty()) {
                        Text(
                            "No hay programaciones guardadas en este dispositivo.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text(
                            "Se añadirá a esta cuenta como macrociclo inactivo.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        savedPrograms.forEach { saved ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { loadTargetFile = saved.fileName }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                RadioButton(
                                    selected = loadTargetFile == saved.fileName,
                                    onClick = { loadTargetFile = saved.fileName },
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(saved.name)
                                    Text(
                                        java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                                            .format(java.util.Date(saved.savedAt)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = loadTargetFile != null,
                    onClick = {
                        val fileName = loadTargetFile ?: return@TextButton
                        showLoadProgram = false
                        scope.launch {
                            val json = ProgramLibrary.read(context, fileName)
                            val result = if (json != null) vm.importSharedMacrocycle(json)
                            else BackupManager.ImportResult.Error("Archivo no encontrado.")
                            val msg = when (result) {
                                is BackupManager.ImportResult.Success -> "Macrociclo añadido a esta cuenta."
                                is BackupManager.ImportResult.Error -> result.reason
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                ) { Text("Cargar") }
            },
            dismissButton = {
                TextButton(onClick = { showLoadProgram = false }) { Text("Cancelar") }
            },
        )
    }

    addCycleMacroId?.let { macroId ->
        AddCycleDialog(
            onDismiss = { addCycleMacroId = null },
            onConfirm = { weeks, avgRir, weeklyVolume, label, groupKeys ->
                vm.addCycle(macroId, weeks, avgRir, weeklyVolume, label, groupKeys)
                addCycleMacroId = null
            },
        )
    }

    activationTarget?.let { program ->
        MacrocycleActivationWizard(
            program = program,
            onDismiss = { activationTarget = null },
            onConfirm = { cycleId, week, day ->
                vm.activateMacrocycle(program.macro.id, cycleId, week, day)
                activationTarget = null
            },
        )
    }

    editingCycle?.let { cycle ->
        CycleEditDialog(
            vm = vm,
            cycle = cycle,
            macrocycleId = cycle.macrocycleId,
            hasClipboard = clipboard != null,
            onDismiss = { editingCycle = null },
        )
    }

    deleteMacroTarget?.let { program ->
        AlertDialog(
            onDismissRequest = { deleteMacroTarget = null },
            title = { Text("Eliminar macrociclo") },
            text = { Text("¿Eliminar «${program.macro.name}» y todos sus ciclos?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteMacrocycle(program.macro)
                    deleteMacroTarget = null
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { deleteMacroTarget = null }) { Text("Cancelar") }
            },
        )
    }

    deleteCycleTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteCycleTarget = null },
            title = { Text("Eliminar ciclo") },
            text = { Text("¿Eliminar este ${item.kind.label.lowercase()}?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteCycle(item.entity)
                    deleteCycleTarget = null
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCycleTarget = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun MacrocycleCard(
    program: MacrocycleProgram,
    vm: TrainingProgramViewModel,
    onAddCycle: () -> Unit,
    onEditCycle: (CycleTimelineItem) -> Unit,
    onDeleteMacro: () -> Unit,
    onDeleteCycle: (CycleTimelineItem) -> Unit,
    onShare: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
) {
    val totalWeeks = TrainingCycleCalculator.macroTotalWeeks(program.timeline)
    val activeStatus = remember(program.macro, program.timeline) {
        TrainingCycleCalculator.resolveActiveCycleStatus(program.timeline, program.macro)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(program.macro.name, fontWeight = FontWeight.SemiBold)
                    if (totalWeeks > 0) {
                        Text(
                            "Duración total: ${TrainingCycleCalculator.formatWeeks(totalWeeks)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(onClick = onShare) {
                        Icon(
                            Icons.Default.IosShare,
                            contentDescription = "Compartir",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    TextButton(onClick = onDeleteMacro) { Text("Borrar") }
                    Text(
                        "Activo",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Switch(
                        checked = program.macro.isActive,
                        onCheckedChange = onToggleActive,
                    )
                }
            }

            HorizontalDivider()

            Text("Línea temporal", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelLarge)

            if (program.timeline.isEmpty()) {
                HintText("Sin ciclos. Añade micro o mesociclos.")
            } else {
                program.timeline.forEachIndexed { index, item ->
                    CycleTimelineRow(
                        index = index + 1,
                        item = item,
                        isCurrent = activeStatus?.item?.entity?.id == item.entity.id,
                        canMoveUp = index > 0,
                        canMoveDown = index < program.timeline.lastIndex,
                        onMoveUp = { vm.moveCycleUp(item.entity) },
                        onMoveDown = { vm.moveCycleDown(item.entity) },
                        onDuplicate = { vm.duplicateCycle(item.entity) },
                        onEdit = { onEditCycle(item) },
                        onDelete = { onDeleteCycle(item) },
                    )
                    if (index < program.timeline.lastIndex) {
                        Text(
                            "↓ continúa",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            OutlinedButton(onClick = onAddCycle, modifier = Modifier.fillMaxWidth()) {
                Text("Añadir ciclo")
            }
        }
    }
}

@Composable
private fun CycleTimelineRow(
    index: Int,
    item: CycleTimelineItem,
    isCurrent: Boolean = false,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                item.kind == CycleKind.MESO -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
            },
        ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    buildString {
                        append("#$index · ${item.kind.label}")
                        if (isCurrent) append(" · en curso")
                    },
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                if (item.entity.label.isNotBlank()) {
                    Text(item.entity.label, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    TrainingCycleCalculator.formatWeeks(item.entity.weeks),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    item.rangeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Subir ciclo")
                    }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Bajar ciclo")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onDuplicate) { Text("Duplicar") }
                    TextButton(onClick = onEdit) { Text("Editar") }
                    TextButton(onClick = onDelete) { Text("Borrar") }
                }
            }
        }
    }
}

@Composable
private fun AddCycleDialog(
    onDismiss: () -> Unit,
    onConfirm: (weeks: Int, avgRir: Float, weeklyVolume: Float, label: String, groupKeys: List<String>) -> Unit,
) {
    var weeks by remember { mutableIntStateOf(1) }
    var avgRirText by rememberSaveable { mutableStateOf("2") }
    var volumeText by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var selectedGroups by remember { mutableStateOf(setOf<String>()) }
    var error by remember { mutableStateOf<String?>(null) }

    val kind = remember(weeks) { TrainingCycleCalculator.classify(weeks) }
    val allGroups = remember { TrainingCycleCalculator.allProgrammableGroupKeys() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir ciclo") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WeeksSliderRow(weeks) { weeks = it }
                Text(
                    "Tipo detectado: ${kind.label}",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedTextField(
                    value = avgRirText,
                    onValueChange = { avgRirText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("RIR base (grupos seleccionados)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = volumeText,
                    onValueChange = { volumeText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Volumen base semanal (grupos seleccionados)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Etiqueta (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                HorizontalDivider()
                Text("Grupos musculares", fontWeight = FontWeight.SemiBold)
                allGroups.forEach { key ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedGroups = if (key in selectedGroups) {
                                    selectedGroups - key
                                } else {
                                    selectedGroups + key
                                }
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = key in selectedGroups,
                            onCheckedChange = { checked ->
                                selectedGroups = if (checked) selectedGroups + key else selectedGroups - key
                            },
                        )
                        Text(TrainingCycleCalculator.formatMuscleGroupKey(key))
                    }
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val rir = avgRirText.replace(',', '.').toFloatOrNull()
                val vol = volumeText.replace(',', '.').toFloatOrNull()
                when {
                    !TrainingCycleCalculator.isValidWeeks(weeks) ->
                        error = "Elige entre 1 y 48 semanas."
                    rir == null -> error = "Indica un RIR base válido."
                    vol == null || vol < 0f -> error = "Indica un volumen base válido."
                    selectedGroups.isEmpty() -> error = "Selecciona al menos un grupo muscular."
                    else -> onConfirm(weeks, rir, vol, label, selectedGroups.toList())
                }
            }) { Text("Añadir") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun CycleEditDialog(
    vm: TrainingProgramViewModel,
    cycle: TrainingCycleEntity,
    macrocycleId: Long,
    hasClipboard: Boolean,
    onDismiss: () -> Unit,
) {
    val groups by vm.cycleGroups(cycle.id).collectAsState(initial = emptyList())

    var weeks by remember(cycle.id) { mutableIntStateOf(cycle.weeks) }
    var label by rememberSaveable(cycle.id) { mutableStateOf(cycle.label) }
    var showAddGroups by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var macroDeactivatedNotice by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar ciclo") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                WeeksSliderRow(weeks) { weeks = it }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Etiqueta") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Por grupo muscular", fontWeight = FontWeight.SemiBold)
                    OutlinedButton(onClick = { showAddGroups = true }) {
                        Text("Añadir grupo")
                    }
                }

                if (groups.isEmpty()) {
                    HintText("Añade grupos con RIR y volumen semanal.")
                } else {
                    groups.forEach { group ->
                        CycleGroupEditRow(
                            group = group,
                            hasClipboard = hasClipboard,
                            onUpdate = { vm.updateCycleGroup(it) },
                            onDelete = { vm.deleteCycleGroup(group, macrocycleId) },
                            onCopy = { rir, vol -> vm.copyGroupValues(rir, vol) },
                            onPaste = { vm.pasteGroupConfig(group, macrocycleId) },
                        )
                    }
                }

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    !TrainingCycleCalculator.isValidWeeks(weeks) ->
                        error = "Elige entre 1 y 48 semanas."
                    else -> {
                        scope.launch {
                            val deactivated = vm.updateCycleReturningDeactivated(
                                cycle.copy(
                                    weeks = weeks,
                                    label = label.trim(),
                                ),
                            )
                            if (deactivated) {
                                macroDeactivatedNotice = true
                            } else {
                                onDismiss()
                            }
                        }
                    }
                }
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )

    if (showAddGroups) {
        AddMuscleGroupsDialog(
            existingKeys = groups.map { it.muscleGroupKey }.toSet(),
            onDismiss = { showAddGroups = false },
            onConfirm = { keys ->
                val template = groups.firstOrNull()
                vm.addCycleGroups(
                    cycle = cycle,
                    groupKeys = keys,
                    defaultRir = template?.avgRir ?: 2f,
                    defaultVolume = template?.weeklyVolume ?: 10f,
                )
                showAddGroups = false
            },
        )
    }

    if (macroDeactivatedNotice) {
        AlertDialog(
            onDismissRequest = {
                macroDeactivatedNotice = false
                onDismiss()
            },
            title = { Text("Macrociclo desactivado") },
            text = {
                Text(
                    "Al guardar cambios en un ciclo, el macrociclo activo se desactivó. " +
                        "Actívalo de nuevo en Programación para elegir ciclo, semana y día de inicio.",
                    style = MaterialTheme.typography.labelSmall,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    macroDeactivatedNotice = false
                    onDismiss()
                }) { Text("Entendido") }
            },
        )
    }
}

@Composable
private fun CycleGroupEditRow(
    group: TrainingCycleGroupEntity,
    hasClipboard: Boolean,
    onUpdate: (TrainingCycleGroupEntity) -> Unit,
    onDelete: () -> Unit,
    onCopy: (Float, Float) -> Unit,
    onPaste: () -> Unit,
) {
    var rirText by rememberSaveable(group.muscleGroupKey) { mutableStateOf("%.1f".format(group.avgRir)) }
    var volText by rememberSaveable(group.muscleGroupKey) { mutableStateOf("%.0f".format(group.weeklyVolume)) }

    LaunchedEffect(group.avgRir, group.weeklyVolume) {
        rirText = "%.1f".format(group.avgRir)
        volText = "%.0f".format(group.weeklyVolume)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                TrainingCycleCalculator.formatMuscleGroupKey(group.muscleGroupKey),
                fontWeight = FontWeight.Medium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = rirText,
                    onValueChange = {
                        rirText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }
                        rirText.replace(',', '.').toFloatOrNull()?.let { v ->
                            onUpdate(group.copy(avgRir = v))
                        }
                    },
                    label = { Text("RIR") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = volText,
                    onValueChange = {
                        volText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }
                        volText.replace(',', '.').toFloatOrNull()?.let { v ->
                            onUpdate(group.copy(weeklyVolume = v))
                        }
                    },
                    label = { Text("Vol/sem") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            ) {
                TextButton(onClick = {
                    val rir = rirText.replace(',', '.').toFloatOrNull() ?: group.avgRir
                    val vol = volText.replace(',', '.').toFloatOrNull() ?: group.weeklyVolume
                    onCopy(rir, vol)
                }) { Text("Copiar") }
                TextButton(onClick = onPaste, enabled = hasClipboard) { Text("Pegar") }
                TextButton(onClick = onDelete) { Text("Quitar") }
            }
        }
    }
}

@Composable
private fun AddMuscleGroupsDialog(
    existingKeys: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    val available = remember(existingKeys) {
        TrainingCycleCalculator.allProgrammableGroupKeys().filter { it !in existingKeys }
    }
    var selected by remember { mutableStateOf(setOf<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir grupos musculares") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (available.isEmpty()) {
                    Text("Todos los grupos ya están añadidos.")
                } else {
                    available.forEach { key ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (key in selected) selected - key else selected + key
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = key in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + key else selected - key
                                },
                            )
                            Text(TrainingCycleCalculator.formatMuscleGroupKey(key))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selected.toList()) },
                enabled = selected.isNotEmpty(),
            ) { Text("Añadir") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun WeeksSliderRow(weeks: Int, onWeeksChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Semanas", style = MaterialTheme.typography.bodyMedium)
            Text("$weeks", fontWeight = FontWeight.Bold)
        }
        Slider(
            value = weeks.toFloat(),
            onValueChange = { onWeeksChange(it.toInt().coerceIn(TrainingCycleCalculator.MIN_WEEKS, TrainingCycleCalculator.MAX_WEEKS)) },
            valueRange = TrainingCycleCalculator.MIN_WEEKS.toFloat()..TrainingCycleCalculator.MAX_WEEKS.toFloat(),
            steps = TrainingCycleCalculator.MAX_WEEKS - TrainingCycleCalculator.MIN_WEEKS - 1,
        )
    }
}

@Composable
private fun ActiveCycleProgressCard(
    status: ActiveCycleStatus?,
    macroName: String?,
    timeline: List<CycleTimelineItem>,
    startDayInWeek: Int = 1,
    activatedAt: Long = 0L,
    modifier: Modifier = Modifier,
) {
    if (status == null || macroName.isNullOrBlank()) return
    val cycleWeeks = status.item.entity.weeks.coerceAtLeast(1)
    val weekInCycle = status.weekInCycle.coerceIn(1, cycleWeeks)
    val cycleName = status.item.entity.label.ifBlank {
        "${status.item.kind.label} #${status.index + 1}"
    }
    val totalMacroWeeks = TrainingCycleCalculator.macroTotalWeeks(timeline).coerceAtLeast(1)
    val primary = MaterialTheme.colorScheme.primary
    val otherCycle = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val onTrack = MaterialTheme.colorScheme.onSurfaceVariant
    val today = remember { LocalDate.now() }
    val weekDays = remember(today) {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        (0 until 7).map { monday.plusDays(it.toLong()) }
    }
    val dayLabels = listOf("L", "M", "X", "J", "V", "S", "D")
    // Índice L–D del calendario donde cae el «día 1» del ciclo (p.ej. viernes si activaste un viernes).
    val day1CalendarIndex = remember(activatedAt, startDayInWeek) {
        programmingDay1CalendarIndex(activatedAt, startDayInWeek)
    }
    val tubePlan = remember(day1CalendarIndex, weekInCycle, cycleWeeks, weekDays) {
        planWeekTubes(
            days = weekDays,
            day1CalendarIndex = day1CalendarIndex,
            weekInCycle = weekInCycle,
            cycleWeeks = cycleWeeks,
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    macroName,
                    style = MaterialTheme.typography.labelMedium,
                    color = onTrack,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    cycleName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = primary,
                )
            }

            Text(
                "Semana $weekInCycle/$cycleWeeks",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    dayLabels.forEach { label ->
                        Text(
                            label,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = onTrack,
                        )
                    }
                }
                CycleCurrentWeekStrip(
                    plan = tubePlan,
                    today = today,
                    activeColor = primary,
                    otherCycleColor = otherCycle,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Macrociclo · semana ${status.macroWeek}/$totalMacroWeeks",
                    style = MaterialTheme.typography.labelSmall,
                    color = onTrack,
                )
                if (timeline.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .clip(RoundedCornerShape(50)),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        timeline.forEachIndexed { index, item ->
                            val weight = max(item.entity.weeks, 1).toFloat()
                            val isCurrent = index == status.index
                            val isPast = item.endWeek < status.macroWeek
                            Box(
                                modifier = Modifier
                                    .weight(weight)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        when {
                                            isCurrent -> primary
                                            isPast -> primary.copy(alpha = 0.45f)
                                            else -> track
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (weight >= 2f || isCurrent) {
                                    Text(
                                        "${item.entity.weeks}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent || isPast) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            onTrack
                                        },
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

/**
 * Índice 0=L … 6=D del día calendario que corresponde al «día 1» del ciclo.
 * Si activaste el viernes eligiendo día 1 → índice de viernes.
 */
private fun programmingDay1CalendarIndex(activatedAt: Long, startDayInWeek: Int): Int {
    val dayInCycle = startDayInWeek.coerceIn(1, 7)
    if (activatedAt <= 0L) return dayInCycle - 1
    val activated = java.time.Instant.ofEpochMilli(activatedAt)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
    val day1Date = activated.minusDays((dayInCycle - 1).toLong())
    return day1Date.dayOfWeek.value - 1 // ISO: Monday=1
}

/**
 * Día 1 del ciclo cae en [day1CalendarIndex] (0=L … 6=D).
 * Tubo actual: desde ese día hasta domingo.
 * Tubo anterior: lunes hasta el día previo (otro ciclo si es semana 1).
 */
private data class WeekTubePlan(
    val leftDays: List<LocalDate>,
    val rightDays: List<LocalDate>,
    val leftIsOtherCycle: Boolean,
    val rightIsOtherCycle: Boolean,
)

private fun planWeekTubes(
    days: List<LocalDate>,
    day1CalendarIndex: Int,
    weekInCycle: Int,
    cycleWeeks: Int,
): WeekTubePlan {
    val startIndex = day1CalendarIndex.coerceIn(0, 6)
    val lastWeek = weekInCycle >= cycleWeeks

    if (startIndex == 0) {
        return if (lastWeek) {
            // Acaba domingo → visual: L–S ciclo actual; D siguiente.
            WeekTubePlan(
                leftDays = days.take(6),
                rightDays = days.drop(6),
                leftIsOtherCycle = false,
                rightIsOtherCycle = true,
            )
        } else {
            WeekTubePlan(
                leftDays = days,
                rightDays = emptyList(),
                leftIsOtherCycle = false,
                rightIsOtherCycle = false,
            )
        }
    }

    val before = days.take(startIndex)
    val after = days.drop(startIndex)

    return if (lastWeek) {
        // Última semana: acaba el día antes del día 1 (ej. jueves si día 1=viernes).
        WeekTubePlan(
            leftDays = before,
            rightDays = after,
            leftIsOtherCycle = false,
            rightIsOtherCycle = true,
        )
    } else {
        // Ej. día 1=viernes: V–D ciclo actual; L–J ciclo/semana anterior.
        WeekTubePlan(
            leftDays = before,
            rightDays = after,
            leftIsOtherCycle = weekInCycle <= 1,
            rightIsOtherCycle = false,
        )
    }
}

@Composable
private fun CycleCurrentWeekStrip(
    plan: WeekTubePlan,
    today: LocalDate,
    activeColor: Color,
    otherCycleColor: Color,
) {
    val segments = buildList {
        if (plan.leftDays.isNotEmpty()) {
            add(plan.leftDays to if (plan.leftIsOtherCycle) otherCycleColor else activeColor)
        }
        if (plan.rightDays.isNotEmpty()) {
            add(plan.rightDays to if (plan.rightIsOtherCycle) otherCycleColor else activeColor)
        }
    }
    if (segments.isEmpty()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        segments.forEach { (segDays, color) ->
            WeekTubeSegment(
                days = segDays,
                today = today,
                tubeColor = color,
                modifier = Modifier.weight(segDays.size.toFloat()),
            )
        }
    }
}

@Composable
private fun WeekTubeSegment(
    days: List<LocalDate>,
    today: LocalDate,
    tubeColor: Color,
    modifier: Modifier = Modifier,
) {
    if (days.isEmpty()) return
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .shadow(6.dp, shape, clip = false)
            .border(3.dp, tubeColor.copy(alpha = 0.95f), shape)
            .border(6.dp, tubeColor.copy(alpha = 0.28f), shape)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        tubeColor.copy(alpha = 0.22f),
                        tubeColor.copy(alpha = 0.10f),
                        tubeColor.copy(alpha = 0.22f),
                    ),
                ),
            )
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        days.forEach { day ->
            val isToday = day == today
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .then(
                        if (isToday) Modifier.background(tubeColor) else Modifier,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
