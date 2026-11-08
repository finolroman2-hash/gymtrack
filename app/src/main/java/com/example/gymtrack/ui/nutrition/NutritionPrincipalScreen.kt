package com.example.gymtrack.ui.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtrack.data.local.entities.FoodLogEntity
import com.example.gymtrack.ui.common.collectAsStateSafe
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun NutritionPrincipalScreen(
    vm: NutritionViewModel,
    onAddFood: () -> Unit,
    onEditFood: (FoodLogEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val date by vm.selectedDate.collectAsStateSafe()
    val logs by vm.dayLogs.collectAsStateSafe()
    val totals by vm.dayTotals.collectAsStateSafe()
    val goals by vm.goals.collectAsStateSafe()
    val fmt = remember { DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale("es", "ES")) }
    var deleteTarget by remember { mutableStateOf<FoodLogEntity?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            fmt.format(date),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Button(onClick = onAddFood, modifier = Modifier.fillMaxWidth()) {
            Text("Agregar comida")
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Totales del día", fontWeight = FontWeight.SemiBold)
                Text("${totals.kcal.toInt()} kcal")
                Text("P ${totals.protein.toInt()}g  ·  C ${totals.carbs.toInt()}g  ·  G ${totals.fat.toInt()}g  ·  F ${totals.fiber.toInt()}g")
                goals?.let {
                    Text(
                        "Objetivo ${it.calories} kcal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (logs.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(logs, key = { it.id }) { log ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(log.name, fontWeight = FontWeight.Medium)
                                if (log.brand.isNotBlank()) {
                                    Text(log.brand, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    "${log.quantity} ${log.unit} · ${log.kcal.toInt()} kcal · P ${log.protein.toInt()}g · C ${log.carbs.toInt()}g · G ${log.fat.toInt()}g",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onEditFood(log) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Editar")
                            }
                            IconButton(onClick = { deleteTarget = log }) {
                                Icon(Icons.Default.Delete, contentDescription = "Quitar")
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
    deleteTarget?.let { log ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("¿Eliminar alimento?") },
            text = { Text("¿Seguro que quieres quitar «${log.name}» de este día?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteFood(log)
                        deleteTarget = null
                    },
                ) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") }
            },
        )
    }
}

