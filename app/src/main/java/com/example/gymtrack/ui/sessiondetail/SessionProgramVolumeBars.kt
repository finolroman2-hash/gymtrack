package com.example.gymtrack.ui.sessiondetail

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.gymtrack.domain.ExerciseMuscleTags
import com.example.gymtrack.domain.SessionGroupProgress
import com.example.gymtrack.domain.TrainingCycleCalculator
import com.example.gymtrack.ui.common.HintText
import com.example.gymtrack.ui.muscle.MuscleGroupIcon
import com.example.gymtrack.ui.muscle.MuscleTagUi

private val ColorGray = Color(0xFF9E9E9E)
/** Verde liquen muy cercano al gris inicial. */
private val ColorLichenGreen = Color(0xFF9BA392)
/** Verde vivo / brillante. */
private val ColorVividGreen = Color(0xFF00C853)
/** Celeste vivo. */
private val ColorVividSky = Color(0xFF00B0FF)
private val ColorSkyHighlight = Color(0xFFE1F5FF)
private val ColorYellow = Color(0xFFFDD835)
private val ColorRed = Color(0xFFE53935)

/** &lt;30 gris · 30–50 liquen · 50–85 vivo · 85–110 celeste · 110–130 amarillo · &gt;130 rojo. */
private fun progressBarFillColor(progress: Float): Color = when {
    progress > 1.3f -> ColorRed
    progress > 1.1f -> ColorYellow
    progress >= 0.85f -> ColorVividSky
    progress >= 0.5f -> ColorVividGreen
    progress >= 0.3f -> ColorLichenGreen
    else -> ColorGray
}

private fun isSkyAchievement(progress: Float): Boolean =
    progress >= 0.85f && progress <= 1.1f

@Composable
fun SessionProgramProgressDialog(
    groups: List<SessionGroupProgress>,
    onDismiss: () -> Unit,
    showPastWeekHint: Boolean = false,
    emptyMessage: String = "Activa macrociclo en Programación y registra series.",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Progreso semanal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showPastWeekHint) {
                    HintText(
                        "Objetivos según la activación del macrociclo; cambian con la fecha.",
                    )
                }
                if (groups.isEmpty()) {
                    Text(
                        emptyMessage,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    SessionProgramVolumeBars(
                        groups = groups,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}

@Composable
fun SessionProgramVolumeBars(
    groups: List<SessionGroupProgress>,
    modifier: Modifier = Modifier,
) {
    if (groups.isEmpty()) return
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val labelBgAlpha = if (dark) 0.22f else 0.14f
    val trackNeutral = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.20f else 0.14f)
    val onBarText = if (dark) Color.White else Color(0xFF1A1A1A)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        groups.forEach { group ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = labelBgAlpha))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MuscleGroupIcon(tag = group.groupKey, size = 18.dp)
                    Text(
                        text = if (group.groupKey == ExerciseMuscleTags.GROUP_UNTAGGED) {
                            TrainingCycleCalculator.formatMuscleGroupKey(group.groupKey)
                        } else {
                            MuscleTagUi.displayName(group.groupKey)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = if (dark) 0.88f else 0.78f,
                        ),
                    )
                }
                LabeledProgramBar(
                    current = group.weeklySetCount,
                    target = group.targetWeeklyVolume,
                    kindLabel = "Volumen",
                    trackColor = trackNeutral,
                    onBarText = onBarText,
                )
                LabeledProgramBar(
                    current = group.actualAvgRir,
                    target = group.targetAvgRir,
                    kindLabel = "RIR",
                    trackColor = trackNeutral,
                    onBarText = onBarText,
                    isRir = true,
                )
            }
        }
    }
}

@Composable
private fun LabeledProgramBar(
    current: Float,
    target: Float,
    kindLabel: String,
    trackColor: Color,
    onBarText: Color,
    isRir: Boolean = false,
) {
    val progress = if (target > 0f) current / target else 0f
    val fillColor = progressBarFillColor(progress)
    val fillWidth = if (progress > 1f) 1f else progress.coerceIn(0f, 1f)
    val skyGlow = isSkyAchievement(progress)
    val barHeight = 14.dp
    val shape = RoundedCornerShape(999.dp)
    val centerLabel = formatBarCenter(current, target, isRir)
    val shineShift by rememberInfiniteTransition(label = "skyShine").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skyShineShift",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(shape)
            .background(trackColor),
    ) {
        if (fillWidth > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(fillWidth)
                    .fillMaxHeight()
                    .clip(shape)
                    .then(
                        if (skyGlow) {
                            Modifier.drawBehind {
                                val band = size.width * 0.55f
                                val x = shineShift * (size.width + band) - band
                                drawRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            ColorVividSky,
                                            ColorVividSky,
                                            ColorSkyHighlight,
                                            ColorVividSky,
                                            ColorVividSky,
                                        ),
                                        start = Offset(x, 0f),
                                        end = Offset(x + band, 0f),
                                    ),
                                )
                            }
                        } else {
                            Modifier.background(fillColor)
                        },
                    ),
            )
        }
        Text(
            text = centerLabel,
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = onBarText,
            textAlign = TextAlign.Center,
        )
        Text(
            text = kindLabel,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = onBarText.copy(alpha = 0.92f),
        )
    }
}

private fun formatBarCenter(current: Float, target: Float, isRir: Boolean): String {
    val cur = formatBarValue(current, isRir)
    val tot = formatBarValue(target, isRir)
    return "$cur/$tot"
}

private fun formatBarValue(value: Float, isRir: Boolean): String =
    if (isRir) {
        if (value == value.toInt().toFloat()) value.toInt().toString()
        else "%.1f".format(value)
    } else {
        value.toInt().toString()
    }
