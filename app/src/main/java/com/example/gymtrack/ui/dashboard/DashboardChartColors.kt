package com.example.gymtrack.ui.dashboard

import androidx.compose.ui.graphics.Color
import com.patrykandpatrick.vico.core.entry.FloatEntry

/** Paleta cíclica (1–10 sesiones) para bandas del gráfico de estadísticas. */
val SessionChartPalette = listOf(
    Color(0xFFE53935),
    Color(0xFF1E88E5),
    Color(0xFF43A047),
    Color(0xFFFB8C00),
    Color(0xFF8E24AA),
    Color(0xFF00ACC1),
    Color(0xFFD81B60),
    Color(0xFF6D4C41),
    Color(0xFF3949AB),
    Color(0xFF7CB342),
)

data class ChartSessionBand(
    val startIndex: Int,
    val endIndex: Int,
    val sessionId: Long,
    val sessionName: String,
    val colorIndex: Int,
)

fun computeSessionBands(points: List<ProgressPoint>): List<ChartSessionBand> {
    if (points.isEmpty()) return emptyList()
    val sessionOrder = points.map { it.raw.sessionId }.distinct()
    val bands = ArrayList<ChartSessionBand>()
    var start = 0
    for (i in 1..points.size) {
        if (i == points.size || points[i].raw.sessionId != points[start].raw.sessionId) {
            val sid = points[start].raw.sessionId
            bands += ChartSessionBand(
                startIndex = start,
                endIndex = i - 1,
                sessionId = sid,
                sessionName = points[start].raw.sessionName,
                colorIndex = sessionOrder.indexOf(sid).coerceAtLeast(0) % SessionChartPalette.size,
            )
            start = i
        }
    }
    return bands
}

data class ChartLineSegment(
    val entries: List<FloatEntry>,
    val color: Color,
)

data class SessionChartSeries(
    val segments: List<ChartLineSegment>,
)

/**
 * Por cada sesión: tramo con color propio.
 * Entre sesiones: conector partido en dos — mitad color sesión anterior, mitad color sesión siguiente.
 */
fun buildSessionChartSeries(
    points: List<ProgressPoint>,
    bands: List<ChartSessionBand>,
): SessionChartSeries {
    if (points.isEmpty()) return SessionChartSeries(emptyList())

    val effectiveBands = bands.ifEmpty {
        listOf(
            ChartSessionBand(
                startIndex = 0,
                endIndex = points.lastIndex,
                sessionId = points.first().raw.sessionId,
                sessionName = points.first().raw.sessionName,
                colorIndex = 0,
            ),
        )
    }

    val segments = ArrayList<ChartLineSegment>(effectiveBands.size * 3)

    effectiveBands.forEachIndexed { index, band ->
        val sessionColor = SessionChartPalette[band.colorIndex % SessionChartPalette.size]
        val sessionEntries = (band.startIndex..band.endIndex).map { i ->
            vicoEntry(i.toFloat(), points[i].y)
        }
        segments += ChartLineSegment(entries = sessionEntries, color = sessionColor)

        if (index < effectiveBands.lastIndex) {
            val next = effectiveBands[index + 1]
            val nextColor = SessionChartPalette[next.colorIndex % SessionChartPalette.size]
            val fromIdx = band.endIndex
            val toIdx = next.startIndex
            val yFrom = points[fromIdx].y
            val yTo = points[toIdx].y
            val midX = ((fromIdx + toIdx) / 2f).toVicoX()
            val midY = ((yFrom + yTo) / 2f)

            segments += ChartLineSegment(
                entries = listOf(
                    vicoEntry(fromIdx.toFloat(), yFrom),
                    vicoEntry(midX, midY),
                ),
                color = sessionColor,
            )
            segments += ChartLineSegment(
                entries = listOf(
                    vicoEntry(midX, midY),
                    vicoEntry(toIdx.toFloat(), yTo),
                ),
                color = nextColor,
            )
        }
    }

    return SessionChartSeries(segments)
}
