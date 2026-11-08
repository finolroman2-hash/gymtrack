package com.example.gymtrack.ui.nutrition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.example.gymtrack.domain.MacroTotals
import kotlin.math.min

private val ColorGray = Color(0xFF9E9E9E)
private val ColorLichenGreen = Color(0xFF9BA392)
private val ColorVividGreen = Color(0xFF00C853)
private val ColorVividSky = Color(0xFF00B0FF)
private val ColorYellow = Color(0xFFFDD835)
private val ColorRed = Color(0xFFE53935)

internal fun nutritionBarFillColor(progress: Float): Color = when {
    progress > 1.3f -> ColorRed
    progress > 1.1f -> ColorYellow
    progress >= 0.85f -> ColorVividSky
    progress >= 0.5f -> ColorVividGreen
    progress >= 0.3f -> ColorLichenGreen
    else -> ColorGray
}

@Composable
fun CalorieCompletionWheel(
    eaten: Float,
    target: Float,
    modifier: Modifier = Modifier,
) {
    val progress = if (target > 0f) eaten / target else 0f
    val remaining = (target - eaten).coerceAtLeast(0f)
    val over = progress >= 1.1f
    val fillColor = if (over) ColorYellow else ColorVividGreen
    val trackColor = ColorRed.copy(alpha = 0.85f)
    val sweep = (min(progress, 1f) * 360f).coerceIn(0f, 360f)

    Box(modifier = modifier.size(180.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(180.dp)) {
            val stroke = 18.dp.toPx()
            val arcSize = Size(size.minDimension - stroke, size.minDimension - stroke)
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (sweep > 0f) {
                drawArc(
                    color = fillColor,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${eaten.toInt()} / ${target.toInt()} kcal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                when {
                    over -> "Superas el 110 %"
                    remaining > 0f -> "Faltan ${remaining.toInt()} kcal"
                    else -> "Objetivo completado"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun NutritionMacroBars(
    totals: MacroTotals,
    targets: MacroTotals?,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val trackNeutral = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.20f else 0.14f)
    val onBarText = if (dark) Color.White else Color(0xFF1A1A1A)
    val rows = listOf(
        Triple("Calorías", totals.kcal, targets?.kcal ?: 0f),
        Triple("Proteína (g)", totals.protein, targets?.protein ?: 0f),
        Triple("Carbos (g)", totals.carbs, targets?.carbs ?: 0f),
        Triple("Grasa (g)", totals.fat, targets?.fat ?: 0f),
        Triple("Fibra (g)", totals.fiber, targets?.fiber ?: 0f),
    )
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { (label, current, target) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                NutritionFillBar(
                    current = current,
                    target = target,
                    trackColor = trackNeutral,
                    onBarText = onBarText,
                )
            }
        }
    }
}

@Composable
private fun NutritionFillBar(
    current: Float,
    target: Float,
    trackColor: Color,
    onBarText: Color,
) {
    val progress = if (target > 0f) current / target else 0f
    val fillColor = nutritionBarFillColor(progress)
    val fillWidth = if (progress > 1f) 1f else progress.coerceIn(0f, 1f)
    val sky = progress >= 0.85f && progress <= 1.1f
    val shineShift by rememberInfiniteTransition(label = "macroShine").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shine",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(trackColor)
            .drawBehind {
                if (sky) {
                    val x = size.width * shineShift
                    drawRect(
                        color = Color(0xFFE1F5FF).copy(alpha = 0.35f),
                        topLeft = Offset(x - size.width * 0.25f, 0f),
                        size = Size(size.width * 0.25f, size.height),
                    )
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fillWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(999.dp))
                .background(fillColor),
        )
        Text(
            text = if (target > 0f) "${current.toInt()} / ${target.toInt()}" else "${current.toInt()}",
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = onBarText,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
