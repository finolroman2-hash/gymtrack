package com.example.gymtrack.ui.training

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtrack.domain.CycleTimelineItem
import com.example.gymtrack.ui.common.HintText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class ActivationStep { CYCLE, WEEK, DAY }

private const val SELECTION_FEEDBACK_MS = 220L

@Composable
fun MacrocycleActivationWizard(
    program: MacrocycleProgram,
    onDismiss: () -> Unit,
    onConfirm: (startCycleId: Long, startWeekInCycle: Int, startDayInWeek: Int) -> Unit,
) {
    if (program.timeline.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Sin ciclos") },
            text = { Text("Añade al menos un ciclo antes de activar el macrociclo.") },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("OK") }
            },
        )
        return
    }

    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(ActivationStep.CYCLE) }
    var selectedCycleIndex by remember { mutableIntStateOf(-1) }
    var selectedWeek by remember { mutableIntStateOf(-1) }
    var selectedDay by remember { mutableIntStateOf(-1) }
    var isAdvancing by remember { mutableStateOf(false) }

    val selectedCycle = program.timeline.getOrNull(selectedCycleIndex)
    val cycleWeeks = selectedCycle?.entity?.weeks ?: 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (step) {
                    ActivationStep.CYCLE -> "Ciclo de inicio"
                    ActivationStep.WEEK -> "Semana de inicio"
                    ActivationStep.DAY -> "Día de inicio"
                },
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (step != ActivationStep.CYCLE && selectedCycle != null) {
                    ActivationSummaryRow(
                        cycleLabel = cycleChipLabel(selectedCycleIndex, selectedCycle),
                        weekLabel = if (selectedWeek > 0) "Sem $selectedWeek" else null,
                    )
                }

                HintText(
                    when (step) {
                        ActivationStep.CYCLE ->
                            "Elige por cuál ciclo empezar ahora."
                        ActivationStep.WEEK ->
                            "Semana en ${selectedCycle?.kind?.label?.lowercase() ?: "ciclo"} " +
                                "#${selectedCycleIndex + 1}."
                        ActivationStep.DAY ->
                            "Día 1 = primer día · Día 7 = séptimo."
                    },
                )

                when (step) {
                    ActivationStep.CYCLE -> {
                        ScrollableTabRow(
                            selectedTabIndex = selectedCycleIndex.coerceAtLeast(0),
                            edgePadding = 0.dp,
                            indicator = { },
                            divider = {},
                        ) {
                            program.timeline.forEachIndexed { index, item ->
                                val label = cycleChipLabel(index, item)
                                val selected = selectedCycleIndex == index
                                Tab(
                                    selected = selected,
                                    enabled = !isAdvancing,
                                    onClick = {
                                        if (isAdvancing) return@Tab
                                        isAdvancing = true
                                        selectedCycleIndex = index
                                        selectedWeek = -1
                                        selectedDay = -1
                                        scope.launch {
                                            delay(SELECTION_FEEDBACK_MS)
                                            step = ActivationStep.WEEK
                                            isAdvancing = false
                                        }
                                    },
                                    text = {
                                        ActivationTabLabel(label, selected)
                                    },
                                )
                            }
                        }
                    }
                    ActivationStep.WEEK -> {
                        ScrollableTabRow(
                            selectedTabIndex = (selectedWeek - 1).coerceAtLeast(0),
                            edgePadding = 0.dp,
                            indicator = { },
                            divider = {},
                        ) {
                            (1..cycleWeeks).forEach { week ->
                                val selected = selectedWeek == week
                                Tab(
                                    selected = selected,
                                    enabled = !isAdvancing,
                                    onClick = {
                                        if (isAdvancing) return@Tab
                                        isAdvancing = true
                                        selectedWeek = week
                                        selectedDay = -1
                                        scope.launch {
                                            delay(SELECTION_FEEDBACK_MS)
                                            step = ActivationStep.DAY
                                            isAdvancing = false
                                        }
                                    },
                                    text = {
                                        ActivationTabLabel("Sem $week", selected)
                                    },
                                )
                            }
                        }
                    }
                    ActivationStep.DAY -> {
                        ScrollableTabRow(
                            selectedTabIndex = (selectedDay - 1).coerceAtLeast(0),
                            edgePadding = 0.dp,
                            indicator = { },
                            divider = {},
                        ) {
                            (1..7).forEach { day ->
                                val selected = selectedDay == day
                                Tab(
                                    selected = selected,
                                    enabled = !isAdvancing,
                                    onClick = {
                                        if (isAdvancing) return@Tab
                                        val cycle = selectedCycle ?: return@Tab
                                        if (selectedWeek <= 0) return@Tab
                                        isAdvancing = true
                                        selectedDay = day
                                        scope.launch {
                                            delay(SELECTION_FEEDBACK_MS)
                                            onConfirm(cycle.entity.id, selectedWeek, day)
                                            isAdvancing = false
                                        }
                                    },
                                    text = {
                                        ActivationTabLabel("Día $day", selected, compact = true)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (step) {
                    ActivationStep.WEEK -> {
                        TextButton(
                            enabled = !isAdvancing,
                            onClick = { step = ActivationStep.CYCLE },
                        ) { Text("Atrás") }
                    }
                    ActivationStep.DAY -> {
                        TextButton(
                            enabled = !isAdvancing,
                            onClick = { step = ActivationStep.WEEK },
                        ) { Text("Atrás") }
                    }
                    else -> Unit
                }
                TextButton(enabled = !isAdvancing, onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

@Composable
private fun ActivationTabLabel(
    label: String,
    selected: Boolean,
    compact: Boolean = false,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "tabScale",
    )
    val background by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        animationSpec = tween(durationMillis = 180),
        label = "tabBg",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 180),
        label = "tabText",
    )

    Surface(
        modifier = Modifier.scale(scale),
        shape = RoundedCornerShape(8.dp),
        color = background,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = 6.dp,
            ),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun ActivationSummaryRow(
    cycleLabel: String,
    weekLabel: String?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Elegido:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                cycleLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (weekLabel != null) {
                Text("·", color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    weekLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

private fun cycleChipLabel(index: Int, item: CycleTimelineItem): String = buildString {
    append("#${index + 1}")
    if (item.entity.label.isNotBlank()) append(" ${item.entity.label}")
    else append(" ${item.kind.label}")
}
