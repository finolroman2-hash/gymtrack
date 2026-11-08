package com.example.gymtrack.ui.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.gymtrack.data.local.entities.NutritionGoalsEntity
import com.example.gymtrack.domain.NutritionActivity
import com.example.gymtrack.domain.NutritionCalculator
import com.example.gymtrack.domain.NutritionGoalType
import com.example.gymtrack.domain.NutritionSex
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionGoalsDialog(
    current: NutritionGoalsEntity?,
    onSave: (
        NutritionSex,
        Int,
        Float,
        Float,
        NutritionActivity,
        NutritionGoalType,
        Float?,
        Float,
        Float,
        Float,
        Float,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    var sex by remember { mutableStateOf(runCatching { NutritionSex.valueOf(current?.sex ?: "MALE") }.getOrDefault(NutritionSex.MALE)) }
    var age by remember { mutableStateOf(current?.age?.toString() ?: "25") }
    var height by remember { mutableStateOf(current?.heightCm?.toString() ?: "175") }
    var weight by remember { mutableStateOf(current?.weightKg?.toString() ?: "75") }
    var activity by remember {
        mutableStateOf(runCatching { NutritionActivity.valueOf(current?.activity ?: "MODERATE") }.getOrDefault(NutritionActivity.MODERATE))
    }
    var goal by remember {
        mutableStateOf(runCatching { NutritionGoalType.valueOf(current?.goal ?: "MAINTAIN") }.getOrDefault(NutritionGoalType.MAINTAIN))
    }
    var bodyFat by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf(fmtMacro(current?.proteinG)) }
    var carbs by remember { mutableStateOf(fmtMacro(current?.carbsG)) }
    var fat by remember { mutableStateOf(fmtMacro(current?.fatG)) }
    var fiber by remember { mutableStateOf(fmtMacro(current?.fiberG)) }

    val proteinVal = protein.replace(',', '.').toFloatOrNull()
    val carbsVal = carbs.replace(',', '.').toFloatOrNull()
    val fatVal = fat.replace(',', '.').toFloatOrNull()
    val fiberVal = fiber.replace(',', '.').toFloatOrNull() ?: 0f
    val calories = if (proteinVal != null && carbsVal != null && fatVal != null) {
        NutritionCalculator.kcalFromMacros(proteinVal, carbsVal, fatVal)
    } else null

    fun applyCalculated() {
        val a = age.toIntOrNull() ?: return
        val h = height.replace(',', '.').toFloatOrNull() ?: return
        val w = weight.replace(',', '.').toFloatOrNull() ?: return
        if (a !in 14..80 || h < 120f || w < 35f) return
        val plan = NutritionCalculator.plan(sex, a, h, w, activity, goal, bodyFat.replace(',', '.').toFloatOrNull())
        protein = fmtMacro(plan.proteinG)
        carbs = fmtMacro(plan.carbsG)
        fat = fmtMacro(plan.fatG)
        fiber = fmtMacro(plan.fiberG)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Objetivos") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EnumDropdown("Sexo", sex, NutritionSex.entries.toList(), {
                    if (it == NutritionSex.MALE) "Hombre" else "Mujer"
                }) { sex = it }
                OutlinedTextField(age, { age = it.filter(Char::isDigit).take(2) }, label = { Text("Edad") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(height, { height = it.filter { ch -> ch.isDigit() || ch == '.' } }, label = { Text("Altura (cm)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(weight, { weight = it.filter { ch -> ch.isDigit() || ch == '.' } }, label = { Text("Peso (kg)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
                EnumDropdown("Actividad", activity, NutritionActivity.entries.toList(), { it.label }) { activity = it }
                EnumDropdown("Meta", goal, NutritionGoalType.entries.toList(), { it.label }) { goal = it }
                OutlinedTextField(
                    bodyFat,
                    { bodyFat = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("% graso (opcional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                TextButton(onClick = { applyCalculated() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Calcular macros")
                }
                Text("Macros diarios", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MacroField("Proteína (g)", protein, { protein = it }, Modifier.weight(1f))
                    MacroField("Carbos (g)", carbs, { carbs = it }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MacroField("Grasa (g)", fat, { fat = it }, Modifier.weight(1f))
                    MacroField("Fibra (g)", fiber, { fiber = it }, Modifier.weight(1f))
                }
                OutlinedTextField(
                    value = calories?.toString() ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Calorías (suma de macros)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = proteinVal != null && carbsVal != null && fatVal != null &&
                    proteinVal >= 0f && carbsVal >= 0f && fatVal >= 0f,
                onClick = {
                    val a = age.toIntOrNull() ?: return@TextButton
                    val h = height.replace(',', '.').toFloatOrNull() ?: return@TextButton
                    val w = weight.replace(',', '.').toFloatOrNull() ?: return@TextButton
                    if (a !in 14..80 || h < 120f || w < 35f) return@TextButton
                    val p = proteinVal ?: return@TextButton
                    val c = carbsVal ?: return@TextButton
                    val g = fatVal ?: return@TextButton
                    onSave(sex, a, h, w, activity, goal, bodyFat.replace(',', '.').toFloatOrNull(), p, c, g, fiberVal.coerceAtLeast(0f))
                    onDismiss()
                },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun MacroField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
        singleLine = true,
    )
}

private fun fmtMacro(value: Float?): String {
    if (value == null) return ""
    return String.format(Locale.US, "%.1f", value).trimEnd('0').trimEnd('.')
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    value: T,
    options: List<T>,
    nameOf: (T) -> String,
    onPick: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = nameOf(value),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(nameOf(option)) },
                    onClick = {
                        onPick(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
