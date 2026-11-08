package com.example.gymtrack.ui.nutrition

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.gymtrack.data.local.entities.FoodLogEntity
import com.example.gymtrack.domain.NutritionCalculator
import com.example.gymtrack.nutrition.FoodQtyUnit
import com.example.gymtrack.nutrition.OffFood
import com.example.gymtrack.ui.common.HintText
import com.example.gymtrack.ui.common.collectAsStateSafe
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddFoodDialog(
    vm: NutritionViewModel,
    onDismiss: () -> Unit,
    editLog: FoodLogEntity? = null,
    fromScratch: Boolean = false,
) {
    val query by vm.searchQuery.collectAsStateSafe()
    val results by vm.searchResults.collectAsStateSafe()
    val searching by vm.searching.collectAsStateSafe()
    val error by vm.searchError.collectAsStateSafe()
    val customFoods by vm.customFoods.collectAsStateSafe()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val editing = editLog != null
    var selected by remember {
        mutableStateOf(
            when {
                editLog != null -> editLog.toOffFood()
                fromScratch -> blankCustomFood()
                else -> null
            },
        )
    }
    var loadingDetails by remember { mutableStateOf(false) }
    var quantity by remember {
        mutableStateOf(
            editLog?.quantity?.let { qty -> fmtQty(qty) } ?: "100",
        )
    }
    var unit by remember {
        mutableStateOf(
            editLog?.let { log ->
                FoodQtyUnit.entries.find { it.code == log.unit } ?: FoodQtyUnit.GRAMS
            } ?: FoodQtyUnit.GRAMS,
        )
    }
    var foodName by remember { mutableStateOf(editLog?.name ?: "") }
    var protein by remember { mutableStateOf(fmtMacro(editLog?.toOffFood()?.protein100g ?: 0f).takeIf { fromScratch || editLog != null } ?: "") }
    var carbs by remember { mutableStateOf(fmtMacro(editLog?.toOffFood()?.carbs100g ?: 0f).takeIf { fromScratch || editLog != null } ?: "") }
    var fat by remember { mutableStateOf(fmtMacro(editLog?.toOffFood()?.fat100g ?: 0f).takeIf { fromScratch || editLog != null } ?: "") }
    var fiber by remember { mutableStateOf(fmtMacro(editLog?.toOffFood()?.fiber100g ?: 0f).takeIf { fromScratch || editLog != null } ?: "") }
    var saveAsCustom by remember { mutableStateOf(fromScratch) }

    fun applyFood(food: OffFood) {
        selected = food
        foodName = food.name
        unit = if (FoodQtyUnit.GRAMS in food.units) FoodQtyUnit.GRAMS else food.units.first()
        quantity = if (unit == FoodQtyUnit.GRAMS) "100" else "1"
        protein = fmtMacro(food.protein100g)
        carbs = fmtMacro(food.carbs100g)
        fat = fmtMacro(food.fat100g)
        fiber = fmtMacro(food.fiber100g)
        saveAsCustom = food.isCustom
    }

    val q = query.trim()
    val customShown = remember(customFoods, q) {
        if (q.isEmpty()) customFoods
        else customFoods.filter {
            it.name.contains(q, ignoreCase = true) || it.brand.contains(q, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = {
            vm.clearSearch()
            onDismiss()
        },
        title = {
            Text(
                when {
                    selected == null -> "Buscar alimento"
                    editing -> "Editar cantidad"
                    fromScratch -> "Añadir alimento"
                    else -> "Cantidad y macros"
                },
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val food = selected
                if (food == null) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = vm::setSearchQuery,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Alimento") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                focus.clearFocus()
                                vm.searchFoods()
                            },
                        ),
                    )
                    Button(
                        onClick = {
                            focus.clearFocus()
                            vm.searchFoods()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !searching && !loadingDetails,
                    ) { Text("Buscar") }
                    if (searching || loadingDetails) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    error?.let { HintText(it) }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (customShown.isNotEmpty()) {
                            item {
                                Text(
                                    "Alimentos agregados",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                            items(customShown, key = { "c-" + it.code }) { item ->
                                FoodSearchRow(item, enabled = !loadingDetails, onClick = { applyFood(item) })
                            }
                        }
                        if (results.isNotEmpty()) {
                            item {
                                Text(
                                    "Resultados",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            items(results, key = { "r-" + it.code + it.name }) { item ->
                                FoodSearchRow(
                                    item,
                                    enabled = !loadingDetails,
                                    onClick = {
                                        if (item.needsDetails) {
                                            loadingDetails = true
                                            scope.launch {
                                                applyFood(vm.loadDetails(item))
                                                loadingDetails = false
                                            }
                                        } else {
                                            applyFood(item)
                                        }
                                    },
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = foodName,
                            onValueChange = { foodName = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Nombre") },
                        )
                        if (!fromScratch) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                food.units.forEach { option ->
                                    val label = if (option == FoodQtyUnit.SERVING && !food.servingLabel.isNullOrBlank()) {
                                        "${option.label} (${food.servingLabel})"
                                    } else option.label
                                    FilterChip(
                                        selected = unit == option,
                                        onClick = { unit = option },
                                        label = { Text(label) },
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = quantity,
                            onValueChange = { quantity = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Cantidad (${unit.label.lowercase()})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                        )
                        Text("Macros / 100 g", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MacroField("Proteína", protein, { protein = it }, Modifier.weight(1f))
                            MacroField("Carbos", carbs, { carbs = it }, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MacroField("Grasa", fat, { fat = it }, Modifier.weight(1f))
                            MacroField("Fibra", fiber, { fiber = it }, Modifier.weight(1f))
                        }
                        val p = protein.replace(',', '.').toFloatOrNull()
                        val c = carbs.replace(',', '.').toFloatOrNull()
                        val g = fat.replace(',', '.').toFloatOrNull()
                        val fib = fiber.replace(',', '.').toFloatOrNull() ?: 0f
                        val qty = quantity.replace(',', '.').toFloatOrNull() ?: 0f
                        val kcal100 = if (p != null && c != null && g != null) {
                            NutritionCalculator.kcalFromMacros(p, c, g)
                        } else null
                        OutlinedTextField(
                            value = kcal100?.toString() ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("kcal / 100 g") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        if (kcal100 != null && qty > 0f && p != null && c != null && g != null && unit in food.units) {
                            val edited = food.withEditedMacros(foodName, p, c, g, fib)
                            val portion = edited.macrosFor(qty, unit)
                            val portionKcal = NutritionCalculator.kcalFromMacros(portion.protein, portion.carbs, portion.fat)
                            Text(
                                "${portionKcal} kcal · P ${portion.protein.toInt()}g · C ${portion.carbs.toInt()}g · G ${portion.fat.toInt()}g",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (!editing && !fromScratch) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Checkbox(checked = saveAsCustom, onCheckedChange = { saveAsCustom = it })
                                Text("Guardar en alimentos agregados")
                            }
                            TextButton(onClick = { selected = null }) { Text("Elegir otro") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val food = selected
            val qty = quantity.replace(',', '.').toFloatOrNull() ?: 0f
            val p = protein.replace(',', '.').toFloatOrNull()
            val c = carbs.replace(',', '.').toFloatOrNull()
            val g = fat.replace(',', '.').toFloatOrNull()
            val fib = fiber.replace(',', '.').toFloatOrNull() ?: 0f
            val nameOk = foodName.trim().isNotEmpty()
            val macrosOk = p != null && c != null && g != null && p >= 0f && c >= 0f && g >= 0f
            TextButton(
                enabled = food != null && qty > 0f && nameOk && macrosOk && food.let { unit in it.units },
                onClick = {
                    if (food != null && p != null && c != null && g != null) {
                        val edited = food.withEditedMacros(foodName, p, c, g, fib)
                        if (editLog != null) vm.updateFood(editLog, edited, qty, unit)
                        else vm.addFood(edited, qty, unit, saveAsCustom = saveAsCustom || fromScratch || edited.isCustom)
                    }
                    vm.clearSearch()
                    onDismiss()
                },
            ) { Text(if (editing) "Guardar" else "Agregar") }
        },
        dismissButton = {
            TextButton(onClick = {
                vm.clearSearch()
                onDismiss()
            }) { Text("Cancelar") }
        },
    )
}

@Composable
private fun FoodSearchRow(
    item: OffFood,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(item.name, fontWeight = FontWeight.SemiBold)
        val subtitle = listOfNotNull(
            item.brand.takeIf { it.isNotBlank() },
            if (item.isCustom) "Agregado" else if (item.isGeneric) "Genérico" else null,
        ).joinToString(" · ")
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "${item.kcal100g.toInt()} kcal / 100 g · P ${item.protein100g.toInt()}g",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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

private fun blankCustomFood() = OffFood(
    code = "",
    name = "",
    brand = "",
    kcal100g = 0f,
    protein100g = 0f,
    carbs100g = 0f,
    fat100g = 0f,
    fiber100g = 0f,
    kcal100ml = null,
    servingGrams = null,
    servingLabel = null,
    quantityHint = "",
    isGeneric = true,
    needsDetails = false,
    cacheable = true,
    isCustom = true,
)

private fun OffFood.withEditedMacros(
    name: String,
    protein: Float,
    carbs: Float,
    fat: Float,
    fiber: Float,
): OffFood {
    val kcal100 = NutritionCalculator.kcalFromMacros(protein, carbs, fat).toFloat()
    val g = servingGrams?.takeIf { it > 0f }
    return copy(
        name = name.trim(),
        protein100g = protein,
        carbs100g = carbs,
        fat100g = fat,
        fiber100g = fiber,
        kcal100g = kcal100,
        kcal100ml = kcal100ml?.let { kcal100 },
        servingKcal = g?.let { kcal100 * it / 100f },
        servingProtein = g?.let { protein * it / 100f },
        servingCarbs = g?.let { carbs * it / 100f },
        servingFat = g?.let { fat * it / 100f },
        servingFiber = g?.let { fiber * it / 100f },
    )
}

private fun fmtMacro(value: Float): String =
    String.format(Locale.US, "%.1f", value).trimEnd('0').trimEnd('.')

private fun fmtQty(qty: Float): String =
    if (qty == qty.toLong().toFloat()) qty.toLong().toString() else qty.toString()
