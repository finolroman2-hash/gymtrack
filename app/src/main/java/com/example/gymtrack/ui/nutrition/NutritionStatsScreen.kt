package com.example.gymtrack.ui.nutrition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.gymtrack.data.local.entities.BodyMetricEntity
import com.example.gymtrack.domain.NutritionCalculator
import com.example.gymtrack.domain.ProgressionCalculator.ChartPeriod
import com.example.gymtrack.ui.common.HintText
import com.example.gymtrack.ui.common.collectAsStateSafe
import java.time.ZoneId
import kotlin.math.max

@Composable
fun NutritionStatsScreen(
    vm: NutritionViewModel,
    modifier: Modifier = Modifier,
) {
    val period by vm.statsPeriod.collectAsStateSafe()
    val totals by vm.periodTotals.collectAsStateSafe()
    val goals by vm.goals.collectAsStateSafe()
    val metrics by vm.bodyMetrics.collectAsStateSafe()
    val date by vm.selectedDate.collectAsStateSafe()
    val dayCount = when (period) {
        ChartPeriod.DAILY -> 1
        ChartPeriod.WEEKLY -> 7
        ChartPeriod.MONTHLY -> date.lengthOfMonth()
        ChartPeriod.ANNUAL -> if (date.isLeapYear) 366 else 365
    }
    val targets = goals?.asTargets()?.let { t ->
        t.copy(
            kcal = t.kcal * dayCount,
            protein = t.protein * dayCount,
            carbs = t.carbs * dayCount,
            fat = t.fat * dayCount,
            fiber = t.fiber * dayCount,
        )
    }
    var weightDraft by remember { mutableStateOf("") }
    var fatDraft by remember { mutableStateOf("") }
    val heightCm = goals?.heightCm
    val labels = listOf("Día", "Semana", "Mes", "Año")

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TabRow(selectedTabIndex = period.ordinal) {
            ChartPeriod.entries.forEachIndexed { i, p ->
                Tab(
                    selected = period == p,
                    onClick = { vm.setStatsPeriod(p) },
                    text = { Text(labels[i]) },
                )
            }
        }
        CalorieCompletionWheel(
            eaten = totals.kcal,
            target = targets?.kcal ?: 0f,
        )
        goals?.let {
            Text(
                "TDEE ${it.tdee} kcal · objetivo diario ${it.calories} kcal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        NutritionMacroBars(totals = totals, targets = targets)
        Spacer(Modifier.height(8.dp))
        Text("Peso y % graso", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        val zone = remember { ZoneId.systemDefault() }
        val dayMillis = remember(date) { date.atStartOfDay(zone).toInstant().toEpochMilli() }
        val metricToday = metrics.find { it.dayStartMillis == dayMillis }
        LaunchedEffect(dayMillis, metricToday) {
            fun fmt(value: Float) = String.format(java.util.Locale.US, "%.1f", value)
                .trimEnd('0').trimEnd('.')
            weightDraft = metricToday?.weightKg?.let(::fmt) ?: ""
            fatDraft = metricToday?.bodyFatPercent?.let(::fmt) ?: ""
        }
        OutlinedTextField(
            value = weightDraft,
            onValueChange = { weightDraft = it.filter { ch -> ch.isDigit() || ch == '.' } },
            label = { Text("Peso (kg)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = fatDraft,
            onValueChange = { fatDraft = it.filter { ch -> ch.isDigit() || ch == '.' } },
            label = { Text("% graso") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(
            onClick = {
                val w = weightDraft.toFloatOrNull() ?: return@Button
                val f = fatDraft.toFloatOrNull() ?: return@Button
                vm.saveBodyMetric(w, f)
                weightDraft = ""
                fatDraft = ""
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = weightDraft.toFloatOrNull() != null && fatDraft.toFloatOrNull() != null,
        ) { Text("Registrar en esta fecha") }

        val latest = metrics.lastOrNull()
        if (latest != null && heightCm != null && heightCm > 0f) {
            val ffmi = NutritionCalculator.ffmi(latest.weightKg, heightCm, latest.bodyFatPercent)
            val nFfmi = NutritionCalculator.normalizedFfmi(latest.weightKg, heightCm, latest.bodyFatPercent)
            Text(
                "FFMI ${"%.1f".format(ffmi)}  ·  FFMI norm. ${"%.1f".format(nFfmi)}",
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Masa magra ${(latest.weightKg * (1f - latest.bodyFatPercent / 100f)).toInt()} kg",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (metrics.size >= 2) {
            BodyMetricChart(
                metrics = metrics,
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )
        } else {
            HintText("Registra al menos dos fechas para ver la gráfica de peso y % graso.")
        }
    }
}

@Composable
private fun BodyMetricChart(
    metrics: List<BodyMetricEntity>,
    modifier: Modifier = Modifier,
) {
    val weightColor = MaterialTheme.colorScheme.primary
    val fatColor = MaterialTheme.colorScheme.tertiary
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ChartLegendDot(weightColor, "Peso (kg)")
            ChartLegendDot(fatColor, "% graso")
        }
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            if (metrics.size < 2) return@Canvas
            val pad = 12.dp.toPx()
            val w = size.width - pad * 2
            val h = size.height - pad * 2
            fun yOf(value: Float, min: Float, span: Float): Float =
                pad + h * (1f - (value - min) / span)

            val minW = metrics.minOf { it.weightKg }
            val maxW = metrics.maxOf { it.weightKg }
            val spanW = max(maxW - minW, 1f)
            val minF = metrics.minOf { it.bodyFatPercent }
            val maxF = metrics.maxOf { it.bodyFatPercent }
            val spanF = max(maxF - minF, 1f)
            val lastIndex = metrics.lastIndex.toFloat()

            val weightPath = Path()
            val fatPath = Path()
            metrics.forEachIndexed { i, m ->
                val x = pad + w * (i / lastIndex)
                val yW = yOf(m.weightKg, minW, spanW)
                val yF = yOf(m.bodyFatPercent, minF, spanF)
                if (i == 0) {
                    weightPath.moveTo(x, yW)
                    fatPath.moveTo(x, yF)
                } else {
                    weightPath.lineTo(x, yW)
                    fatPath.lineTo(x, yF)
                }
                drawCircle(weightColor, 5.dp.toPx(), Offset(x, yW))
                drawCircle(fatColor, 4.dp.toPx(), Offset(x, yF))
            }
            drawPath(weightPath, weightColor, style = Stroke(width = 3.dp.toPx()))
            drawPath(fatPath, fatColor, style = Stroke(width = 2.5.dp.toPx()))
        }
    }
}

@Composable
private fun ChartLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
