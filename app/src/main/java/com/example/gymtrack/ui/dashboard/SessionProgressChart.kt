package com.example.gymtrack.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import android.graphics.RectF
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.scroll.rememberChartScrollSpec
import com.patrykandpatrick.vico.compose.chart.scroll.rememberChartScrollState
import com.patrykandpatrick.vico.compose.component.lineComponent
import com.patrykandpatrick.vico.compose.component.shape.shader.fromBrush
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.chart.decoration.Decoration
import com.patrykandpatrick.vico.core.chart.dimensions.HorizontalDimensions
import com.patrykandpatrick.vico.core.chart.draw.ChartDrawContext
import com.patrykandpatrick.vico.core.chart.line.LineChart
import com.patrykandpatrick.vico.core.component.shape.shader.DynamicShaders
import com.patrykandpatrick.vico.core.context.MeasureContext
import com.patrykandpatrick.vico.core.entry.ChartEntry
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.scroll.AutoScrollCondition
import com.patrykandpatrick.vico.core.scroll.InitialScroll
import kotlin.math.abs
import kotlin.math.roundToInt

private val ForecastOverlayColor = Color(0xFFFF9800)
private const val ForecastOverlayAlpha = 0.65f
private val PeakGuidelineColor = Color.White.copy(alpha = 0.55f)
private val PeakSelectedColor = Color.White
private val AxisGrayGuideline = Color(0xFF9E9E9E).copy(alpha = 0.35f)

@Composable
fun SessionProgressChart(
    points: List<ProgressPoint>,
    bands: List<ChartSessionBand>,
    forecastOverlayLines: List<List<FloatEntry>> = emptyList(),
    showForecastOverlay: Boolean = false,
    selectedSetId: Long? = null,
    onPointSelected: ((ProgressPoint?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val baseSeries = remember(points, bands) {
        buildSessionChartSeries(points, bands)
    }
    if (baseSeries.segments.isEmpty()) return

    val overlayLines = remember(forecastOverlayLines) {
        forecastOverlayLines.filter { it.size >= 2 }
    }

    val selectedIndex = remember(points, selectedSetId) {
        if (selectedSetId == null) -1
        else points.indexOfFirst { it.raw.setId == selectedSetId }
    }
    // Es una serie del gráfico, no una capa sobrepuesta: se desplaza y alinea
    // exactamente con la guía de la serie seleccionada.
    val selectedStem = remember(points, selectedIndex) {
        if (selectedIndex !in points.indices) emptyList()
        else listOf(
            vicoEntry(selectedIndex.toFloat(), 0f),
            vicoEntry(selectedIndex.toFloat(), points[selectedIndex].y),
        )
    }

    val sessionLineSpecs = remember(baseSeries.segments) {
        baseSeries.segments.map { segment -> sessionColoredLineSpec(segment.color) }
    }

    val forecastLineSpec = remember { forecastOverlayLineSpec() }
    val selectedStemLineSpec = remember {
        LineChart.LineSpec(
            lineThicknessDp = 3f,
            lineColor = PeakSelectedColor.toArgb(),
        )
    }

    val lineSpecs = remember(sessionLineSpecs, overlayLines, selectedStem) {
        buildList {
            addAll(sessionLineSpecs)
            addAll(List(overlayLines.size) { forecastLineSpec })
            if (selectedStem.isNotEmpty()) add(selectedStemLineSpec)
        }
    }

    val model = remember(baseSeries.segments, overlayLines, selectedStem) {
        val series = ArrayList<List<ChartEntry>>(
            baseSeries.segments.size + overlayLines.size + 1,
        )
        baseSeries.segments.forEach { segment ->
            series.add(ArrayList<ChartEntry>(segment.entries.withVicoXPrecision()))
        }
        overlayLines.forEach { line ->
            series.add(ArrayList<ChartEntry>(line.withVicoXPrecision()))
        }
        if (selectedStem.isNotEmpty()) {
            series.add(ArrayList<ChartEntry>(selectedStem))
        }
        ChartEntryModelProducer(series).getModel()
    } ?: return

    val seriesAxisFormatter = remember {
        AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
            val index = value.roundToInt()
            if (index.toFloat() == value) index.toString() else ""
        }
    }
    val seriesAxisItemPlacer = remember(points.size) {
        IntegerSeriesHorizontalItemPlacer(points.size)
    }

    val peakGuideline = lineComponent(
        color = PeakGuidelineColor,
        thickness = 1.dp,
    )
    val grayGuideline = lineComponent(
        color = AxisGrayGuideline,
        thickness = 1.dp,
    )

    val scrollResetKey = remember(points, overlayLines) {
        Triple(
            points.size,
            points.firstOrNull()?.raw?.setId,
            points.lastOrNull()?.raw?.setId,
        )
    }

    key(scrollResetKey) {
        val chartScrollSpec = rememberChartScrollSpec(
            initialScroll = InitialScroll.End,
            isScrollEnabled = true,
            autoScrollCondition = AutoScrollCondition.OnModelSizeIncreased,
        )
        val chartScrollState = rememberChartScrollState()
        val hitLayout = remember(points.size) { ChartHitLayout() }
        val hitDecoration = remember(points.size, hitLayout) {
            BarHitDecoration(points.size, hitLayout)
        }
        val vicoChart = lineChart(
            lines = lineSpecs,
            decorations = listOf(hitDecoration),
        )

        Box(
            modifier = modifier.fillMaxWidth(),
        ) {
            Chart(
                chart = vicoChart,
                model = model,
                chartScrollSpec = chartScrollSpec,
                chartScrollState = chartScrollState,
                getXStep = { 0.5f },
                startAxis = rememberStartAxis(guideline = grayGuideline),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = seriesAxisFormatter,
                    itemPlacer = seriesAxisItemPlacer,
                    guideline = peakGuideline,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .pointerInput(points, selectedSetId, hitLayout) {
                        if (onPointSelected == null || points.isEmpty()) return@pointerInput
                        detectTapGestures { offset ->
                            val idx = hitLayout.nearestIndex(offset.x)
                            if (idx !in points.indices) return@detectTapGestures
                            val tapped = points[idx]
                            if (selectedSetId == tapped.raw.setId) {
                                onPointSelected(null)
                            } else {
                                onPointSelected(tapped)
                            }
                        }
                    },
            )

            if (showForecastOverlay && overlayLines.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 4.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(ForecastOverlayColor.copy(alpha = ForecastOverlayAlpha), CircleShape),
                    )
                    Text(
                        "Pronóstico",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Línea de pronóstico: sin relleno ni conexión con sesiones; solo trazo al 65 %. */
private fun forecastOverlayLineSpec(): LineChart.LineSpec =
    LineChart.LineSpec(
        lineThicknessDp = 2.5f,
        lineColor = ForecastOverlayColor.copy(alpha = ForecastOverlayAlpha).toArgb(),
    )

/** Guarda el mapeo X→píxel con la misma fórmula que LineChart de Vico. */
private class BarHitDecoration(
    private val pointCount: Int,
    private val layout: ChartHitLayout,
) : Decoration {
    override fun onDrawAboveChart(context: ChartDrawContext, bounds: RectF) {
        if (pointCount <= 0) {
            layout.clear()
            return
        }
        // ChartDrawContext.horizontalDimensions ya viene escalado por zoom.
        val hd = context.horizontalDimensions
        val values = context.chartValuesProvider.getChartValues()
        val xStep = values.xStep.coerceAtLeast(0.0001f)
        layout.update(
            originX = bounds.left + hd.startPadding - context.horizontalScroll,
            pixelsPerX = hd.xSpacing / xStep,
            minX = values.minX,
            pointCount = pointCount,
            boundsLeft = bounds.left,
            boundsRight = bounds.right,
        )
    }
}

/** Parámetros de hit-test actualizados en cada frame de dibujo. */
private class ChartHitLayout {
    @Volatile var ready: Boolean = false
    var originX: Float = 0f
    var pixelsPerX: Float = 0f
    var minX: Float = 0f
    var pointCount: Int = 0
    var boundsLeft: Float = 0f
    var boundsRight: Float = 0f

    fun update(
        originX: Float,
        pixelsPerX: Float,
        minX: Float,
        pointCount: Int,
        boundsLeft: Float,
        boundsRight: Float,
    ) {
        this.originX = originX
        this.pixelsPerX = pixelsPerX
        this.minX = minX
        this.pointCount = pointCount
        this.boundsLeft = boundsLeft
        this.boundsRight = boundsRight
        ready = pointCount > 0 && pixelsPerX > 0f
    }

    fun clear() {
        ready = false
        pointCount = 0
    }

    /** Índice de serie más cercano al toque (misma geometría que las guías). */
    fun nearestIndex(tapX: Float): Int {
        if (!ready || pointCount <= 0 || pixelsPerX <= 0f) return -1
        val xValue = minX + (tapX - originX) / pixelsPerX
        val rounded = xValue.roundToInt()
        if (rounded in 0 until pointCount) {
            // Si cae casi a mitad de camino, elige por distancia en píxeles.
            val distToRounded = abs(tapX - (originX + (rounded - minX) * pixelsPerX))
            val neighbor = if (xValue >= rounded) rounded + 1 else rounded - 1
            if (neighbor in 0 until pointCount) {
                val distNeighbor = abs(tapX - (originX + (neighbor - minX) * pixelsPerX))
                if (distNeighbor < distToRounded) return neighbor
            }
            return rounded
        }
        return rounded.coerceIn(0, pointCount - 1)
    }
}

/** Línea + estela degradada del color de la sesión. */
private fun sessionColoredLineSpec(color: Color): LineChart.LineSpec =
    LineChart.LineSpec(
        lineThicknessDp = 2f,
        lineColor = color.toArgb(),
        lineBackgroundShader = DynamicShaders.fromBrush(
            Brush.verticalGradient(
                colors = listOf(
                    color.copy(alpha = 0.42f),
                    color.copy(alpha = 0.12f),
                    Color.Transparent,
                ),
            ),
        ),
    )

/** Guías y etiquetas del eje de series solo en índices enteros (sin 0.5, 1.5, …). */
private class IntegerSeriesHorizontalItemPlacer(
    pointCount: Int,
) : AxisItemPlacer.Horizontal {

    private val delegate = AxisItemPlacer.Horizontal.default()
    private val indices: List<Float> = (0 until pointCount).map { it.toFloat() }

    private fun inVisibleRange(range: ClosedFloatingPointRange<Float>): List<Float> =
        indices.filter { it in range }

    override fun getAddFirstLabelPadding(context: MeasureContext): Boolean = false

    override fun getAddLastLabelPadding(context: MeasureContext): Boolean = false

    override fun getLabelValues(
        context: ChartDrawContext,
        visibleXRange: ClosedFloatingPointRange<Float>,
        fullXRange: ClosedFloatingPointRange<Float>,
    ): List<Float> = inVisibleRange(visibleXRange)

    override fun getMeasuredLabelValues(
        context: MeasureContext,
        horizontalDimensions: HorizontalDimensions,
        fullXRange: ClosedFloatingPointRange<Float>,
    ): List<Float> = indices

    override fun getLineValues(
        context: ChartDrawContext,
        visibleXRange: ClosedFloatingPointRange<Float>,
        fullXRange: ClosedFloatingPointRange<Float>,
    ): List<Float> = inVisibleRange(visibleXRange)

    override fun getStartHorizontalAxisInset(
        context: MeasureContext,
        horizontalDimensions: HorizontalDimensions,
        tickThickness: Float,
    ): Float = delegate.getStartHorizontalAxisInset(context, horizontalDimensions, tickThickness)

    override fun getEndHorizontalAxisInset(
        context: MeasureContext,
        horizontalDimensions: HorizontalDimensions,
        tickThickness: Float,
    ): Float = delegate.getEndHorizontalAxisInset(context, horizontalDimensions, tickThickness)
}
