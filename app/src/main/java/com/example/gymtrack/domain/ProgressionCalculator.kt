package com.example.gymtrack.domain

import com.example.gymtrack.data.local.entities.SetProgressWithSession
import com.example.gymtrack.data.local.entities.UnitType
import kotlin.math.roundToInt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

object ProgressionCalculator {

    // ─── 1RM actualizado con RIR ──────────────────────────────────────────────
    /** 1RM = peso × (1 + (reps + rir) / 30)  */
    fun estimateOneRm(weight: Float, reps: Int, rir: Int = 0): Float {
        if (reps <= 0) return 0f
        return weight * (1f + (reps + rir) / 30f)
    }

    /** Brzycki clásico sin RIR — conservado para retrocompatibilidad. */
    fun estimateOneRmBrzycki(weight: Float, reps: Int): Float {
        if (reps <= 0 || reps >= 37) return 0f
        return weight * (36f / (37f - reps))
    }

    fun formatOneDecimal(value: Float): Float =
        (value * 10f).roundToInt() / 10f

    // ─── Sufijo dinámico ──────────────────────────────────────────────────────
    fun weightLabel(unitType: UnitType): String =
        if (unitType == UnitType.PLATES) "placas" else "kg"

    // ─── RIR Promedio ─────────────────────────────────────────────────────────
    private fun SetProgressWithSession.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(sessionDate).atZone(ZoneId.systemDefault()).toLocalDate()

    fun avgRirWeekly(rows: List<SetProgressWithSession>): Float {
        val cutoff = LocalDate.now().minusDays(6)
        return rows.filter { it.toLocalDate() >= cutoff }.avgRir()
    }

    fun avgRirMonthly(rows: List<SetProgressWithSession>): Float {
        val now = LocalDate.now()
        return rows.filter { val d = it.toLocalDate(); d.year == now.year && d.month == now.month }.avgRir()
    }

    fun avgRirAnnual(rows: List<SetProgressWithSession>): Float {
        val year = LocalDate.now().year
        return rows.filter { it.toLocalDate().year == year }.avgRir()
    }

    private fun List<SetProgressWithSession>.avgRir(): Float =
        if (isEmpty()) 0f else sumOf { it.rir }.toFloat() / size

    // ─── Datos para gráficas ──────────────────────────────────────────────────
    data class ChartPoint(val label: String, val value: Float)
    enum class ChartPeriod { DAILY, WEEKLY, MONTHLY, ANNUAL }

    fun buildChartData(rows: List<SetProgressWithSession>, period: ChartPeriod): List<ChartPoint> {
        val wf = WeekFields.of(Locale.getDefault())
        return when (period) {
            ChartPeriod.DAILY -> (0 until 30).map { offset ->
                val date = LocalDate.now().minusDays((29 - offset).toLong())
                val max = rows.filter { it.toLocalDate() == date }
                    .maxOfOrNull { estimateOneRm(it.weight, it.reps, it.rir) } ?: 0f
                ChartPoint(date.dayOfMonth.toString(), max)
            }
            ChartPeriod.WEEKLY -> (0 until 12).map { offset ->
                val ref = LocalDate.now().minusWeeks((11 - offset).toLong())
                val key = "${ref.year}-W${ref.get(wf.weekOfWeekBasedYear())}"
                val max = rows.filter {
                    val d = it.toLocalDate()
                    "${d.year}-W${d.get(wf.weekOfWeekBasedYear())}" == key
                }.maxOfOrNull { estimateOneRm(it.weight, it.reps, it.rir) } ?: 0f
                ChartPoint("S${ref.get(wf.weekOfWeekBasedYear())}", max)
            }
            ChartPeriod.MONTHLY -> (0 until 12).map { offset ->
                val ref = LocalDate.now().minusMonths((11 - offset).toLong())
                val max = rows.filter {
                    val d = it.toLocalDate(); d.year == ref.year && d.month == ref.month
                }.maxOfOrNull { estimateOneRm(it.weight, it.reps, it.rir) } ?: 0f
                ChartPoint(ref.month.name.take(3), max)
            }
            ChartPeriod.ANNUAL -> {
                val currentYear = LocalDate.now().year
                (0 until 5).map { offset ->
                    val year = currentYear - (4 - offset)
                    val max = rows.filter { it.toLocalDate().year == year }
                        .maxOfOrNull { estimateOneRm(it.weight, it.reps, it.rir) } ?: 0f
                    ChartPoint(year.toString(), max)
                }
            }
        }
    }
}
