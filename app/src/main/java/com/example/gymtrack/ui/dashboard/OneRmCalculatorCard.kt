package com.example.gymtrack.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.gymtrack.data.local.entities.UnitType
import com.example.gymtrack.domain.ProgressionCalculator

@Composable
fun OneRmCalculatorCard(
    selectedUnitType: UnitType,
    modifier: Modifier = Modifier,
) {
    var calcWeight by rememberSaveable { mutableStateOf("") }
    var calcReps by rememberSaveable { mutableStateOf("") }
    var calcRir by rememberSaveable { mutableStateOf("") }

    val weightUnitLabel = ProgressionCalculator.weightLabel(selectedUnitType)

    val calcOneRm = androidx.compose.runtime.remember(calcWeight, calcReps, calcRir) {
        val w = calcWeight.replace(',', '.').toFloatOrNull()
        val r = calcReps.toIntOrNull()
        val rir = calcRir.toIntOrNull() ?: 0
        if (w != null && r != null && r > 0) {
            ProgressionCalculator.estimateOneRm(w, r, rir)
        } else null
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Calculadora de 1RM", fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = calcWeight,
                    onValueChange = { calcWeight = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Peso") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = calcReps,
                    onValueChange = { calcReps = it.filter(Char::isDigit) },
                    label = { Text("Reps") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = calcRir,
                    onValueChange = { calcRir = it.filter(Char::isDigit) },
                    label = { Text("RIR") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            calcOneRm?.let { rm ->
                Text(
                    "1RM estimado: ${"%.1f".format(rm)} $weightUnitLabel",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
