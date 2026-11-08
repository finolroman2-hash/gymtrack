package com.example.gymtrack.ui.nutrition

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.gymtrack.data.local.entities.FoodLogEntity
import com.example.gymtrack.ui.common.HintText
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NutritionSection(
    vm: NutritionViewModel,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf("Principal", "Estadísticas")
    val pager = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    var showAddFood by remember { mutableStateOf(false) }
    var editLog by remember { mutableStateOf<FoodLogEntity?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = pager.currentPage) {
            tabs.forEachIndexed { i, title ->
                Tab(
                    selected = pager.currentPage == i,
                    onClick = { scope.launch { pager.animateScrollToPage(i) } },
                    text = { Text(title) },
                )
            }
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f),
        ) { page ->
            if (page == 0) {
                NutritionPrincipalScreen(
                    vm = vm,
                    onAddFood = { showAddFood = true },
                    onEditFood = { editLog = it },
                )
            } else {
                NutritionStatsScreen(vm = vm)
            }
        }
    }

    if (showAddFood) {
        AddFoodDialog(vm = vm, onDismiss = { showAddFood = false })
    }
    editLog?.let { log ->
        AddFoodDialog(
            vm = vm,
            editLog = log,
            onDismiss = { editLog = null },
        )
    }
}

@Composable
fun NutritionPlaceholder(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        HintText(hint)
    }
}

@Composable
fun NutritionCalendarDialog(
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var month by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    val today = remember { LocalDate.now() }
    val locale = remember { Locale("es", "ES") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Mes anterior")
                }
                Text(
                    month.month.getDisplayName(TextStyle.FULL, locale)
                        .replaceFirstChar { it.titlecase(locale) } + " ${month.year}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = { month = month.plusMonths(1) }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Mes siguiente")
                }
            }
        },
        text = {
            NutritionMonthGrid(
                month = month,
                today = today,
                selectedDate = selectedDate,
                onSelectDate = {
                    onSelectDate(it)
                    onDismiss()
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}

@Composable
private fun NutritionMonthGrid(
    month: YearMonth,
    today: LocalDate,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    val start = first.with(DayOfWeek.MONDAY).let { d ->
        if (d.isAfter(first)) d.minusWeeks(1) else d
    }
    val weeks = remember(month) {
        generateSequence(start) { it.plusWeeks(1) }
            .take(6)
            .map { weekStart -> (0 until 7).map { weekStart.plusDays(it.toLong()) } }
            .filter { week -> week.any { it.month == month.month } }
            .toList()
    }
    val currentWeekStart = today.with(DayOfWeek.MONDAY)
    val labels = listOf("L", "M", "X", "J", "V", "S", "D")
    val tubeGreen = MaterialTheme.colorScheme.primary

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            labels.forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        weeks.forEach { week ->
            val isCurrentWeek = week.first() == currentWeekStart
            WeekTubeRow(
                days = week,
                month = month,
                today = today,
                selectedDate = selectedDate,
                highlighted = isCurrentWeek,
                tubeColor = tubeGreen,
                onSelectDate = onSelectDate,
            )
        }
    }
}

@Composable
private fun WeekTubeRow(
    days: List<LocalDate>,
    month: YearMonth,
    today: LocalDate,
    selectedDate: LocalDate,
    highlighted: Boolean,
    tubeColor: Color,
    onSelectDate: (LocalDate) -> Unit,
) {
    val shape = RoundedCornerShape(50)
    val inner = Modifier
        .fillMaxWidth()
        .then(
            if (highlighted) {
                Modifier
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
                            )
                        )
                    )
                    .padding(vertical = 4.dp, horizontal = 4.dp)
            } else {
                Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
            }
        )

    Row(
        modifier = inner,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        days.forEach { day ->
            val inMonth = day.month == month.month
            val isToday = day == today
            val isSelected = day == selectedDate
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .then(
                        when {
                            isSelected -> Modifier.background(tubeColor)
                            isToday -> Modifier.background(tubeColor.copy(alpha = 0.35f))
                            else -> Modifier
                        }
                    )
                    .clickable { onSelectDate(day) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday || highlighted || isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
