package com.example.gymtrack.domain

import com.example.gymtrack.data.local.entities.ForecastPointEntity
import com.example.gymtrack.data.local.entities.SetProgressWithSession
import com.example.gymtrack.ui.dashboard.ProgressPoint
import com.patrykandpatrick.vico.core.entry.FloatEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

object ForecastCalculator {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())

    data class OverlapWarning(
        val overlapStart: LocalDate,
        val overlapEnd: LocalDate,
    ) {
        fun message(): String =
            "Se sobrescribirá del ${overlapStart.format(dateFmt)} al ${overlapEnd.format(dateFmt)} " +
                "de la otra pronosticación."
    }

    data class ForecastSegment(
        val startDate: LocalDate,
        val endDate: LocalDate,
        val startOneRm: Float,
        val endOneRm: Float,
    )

    data class ForecastComparison(
        val date: LocalDate,
        val actualOneRm: Float?,
        val forecastOneRm: Float,
        val delta: Float?,
    )

    fun LocalDate.toDayStartMillis(): Long =
        atStartOfDay(zone).toInstant().toEpochMilli()

    fun millisToLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun SetProgressWithSession.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(sessionDate).atZone(zone).toLocalDate()

    /** Días del pronóstico existente que caen dentro del rango nuevo. */
    fun detectOverlap(
        existing: List<ForecastPointEntity>,
        newStart: LocalDate,
        newEnd: LocalDate,
    ): OverlapWarning? {
        if (existing.isEmpty()) return null
        val overlapping = existing
            .map { millisToLocalDate(it.dateMillis) }
            .filter { !it.isBefore(newStart) && !it.isAfter(newEnd) }
        if (overlapping.isEmpty()) return null
        return OverlapWarning(overlapping.min(), overlapping.max())
    }

    /** Pronóstico lineal día a día entre startOneRm y endOneRm. */
    fun buildForecastPoints(
        userName: String,
        exerciseId: Long,
        startDate: LocalDate,
        endDate: LocalDate,
        startOneRm: Float,
        endOneRm: Float,
    ): List<ForecastPointEntity> {
        if (endDate.isBefore(startDate)) return emptyList()
        val totalDays = ChronoUnit.DAYS.between(startDate, endDate).toInt()
        if (totalDays < 0) return emptyList()
        return (0..totalDays).map { offset ->
            val date = startDate.plusDays(offset.toLong())
            val t = if (totalDays == 0) 1f else offset.toFloat() / totalDays
            val predicted = startOneRm + (endOneRm - startOneRm) * t
            ForecastPointEntity(
                userName = userName,
                exerciseId = exerciseId,
                dateMillis = date.toDayStartMillis(),
                predictedOneRm = ProgressionCalculator.formatOneDecimal(predicted),
            )
        }
    }

    /** Agrupa puntos consecutivos en tramos de pronosticación editables. */
    fun groupForecastSegments(points: List<ForecastPointEntity>): List<ForecastSegment> {
        if (points.isEmpty()) return emptyList()
        val sorted = points.sortedBy { it.dateMillis }
        val segments = ArrayList<ForecastSegment>()
        var segStartDate = millisToLocalDate(sorted.first().dateMillis)
        var segEndDate = segStartDate
        var segStartRm = sorted.first().predictedOneRm
        var segEndRm = sorted.first().predictedOneRm

        for (i in 1 until sorted.size) {
            val date = millisToLocalDate(sorted[i].dateMillis)
            val prevDate = millisToLocalDate(sorted[i - 1].dateMillis)
            if (ChronoUnit.DAYS.between(prevDate, date) == 1L) {
                segEndDate = date
                segEndRm = sorted[i].predictedOneRm
            } else {
                segments += ForecastSegment(segStartDate, segEndDate, segStartRm, segEndRm)
                segStartDate = date
                segEndDate = date
                segStartRm = sorted[i].predictedOneRm
                segEndRm = sorted[i].predictedOneRm
            }
        }
        segments += ForecastSegment(segStartDate, segEndDate, segStartRm, segEndRm)
        return segments
    }

    /**
     * Líneas de pronóstico independientes para superponer sobre la gráfica de sesiones.
     * Muestra cada tramo completo (inicio → fin), extrapolando en el eje X si va antes o después de las series.
     */
    fun buildForecastOverlayLines(
        points: List<ProgressPoint>,
        forecastPoints: List<ForecastPointEntity>,
    ): List<List<FloatEntry>> {
        if (points.isEmpty() || forecastPoints.isEmpty()) return emptyList()

        return groupForecastSegments(forecastPoints).mapNotNull { segment ->
            val entries = forecastPoints
                .filter { fp ->
                    val date = millisToLocalDate(fp.dateMillis)
                    !date.isBefore(segment.startDate) && !date.isAfter(segment.endDate)
                }
                .map { fp ->
                    val date = millisToLocalDate(fp.dateMillis)
                    val x = dateToChartIndex(points, date)
                    FloatEntry(x, fp.predictedOneRm)
                }
                .groupBy { it.x }
                .map { (_, sameX) -> sameX.last() }
                .sortedBy { it.x }

            if (entries.size >= 2) entries else null
        }
    }

    /**
     * Mapea una fecha al eje X del gráfico por tiempo lineal respecto a las series.
     * La primera serie → 0, la última → lastIndex; fechas anteriores o posteriores se extrapolan.
     */
    private fun dateToChartIndex(points: List<ProgressPoint>, date: LocalDate): Float {
        val workoutFirst = points.first().raw.toLocalDate()
        val workoutLast = points.last().raw.toLocalDate()

        if (points.size == 1) {
            val dayOffset = ChronoUnit.DAYS.between(workoutFirst, date)
            return dayOffset.toFloat().toVicoChartX()
        }

        val indexOnDate = points.indices
            .filter { points[it].raw.toLocalDate() == date }
            .maxOrNull()
        if (indexOnDate != null) return indexOnDate.toFloat().toVicoChartX()

        val workoutDays = ChronoUnit.DAYS.between(workoutFirst, workoutLast).coerceAtLeast(1)
        val dayOffset = ChronoUnit.DAYS.between(workoutFirst, date)
        val x = (dayOffset.toFloat() / workoutDays.toFloat()) * points.lastIndex
        return x.toVicoChartX()
    }

    fun compareWithForecast(
        points: List<ProgressPoint>,
        forecastPoints: List<ForecastPointEntity>,
    ): List<ForecastComparison> {
        if (forecastPoints.isEmpty()) return emptyList()
        val actualByDay = points
            .groupBy { it.raw.toLocalDate() }
            .mapValues { (_, dayPoints) -> dayPoints.maxOf { it.y } }

        return forecastPoints.map { fp ->
            val date = millisToLocalDate(fp.dateMillis)
            val actual = actualByDay[date]
            ForecastComparison(
                date = date,
                actualOneRm = actual,
                forecastOneRm = fp.predictedOneRm,
                delta = actual?.let { it - fp.predictedOneRm },
            )
        }
    }

    fun parseDateInput(text: String): LocalDate? = runCatching {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null
        LocalDate.parse(trimmed, dateFmt)
    }.getOrNull()

    /** Vico 1.14: máximo 2 decimales en X. */
    private fun Float.toVicoChartX(): Float = (this * 100f).roundToInt() / 100f
}
