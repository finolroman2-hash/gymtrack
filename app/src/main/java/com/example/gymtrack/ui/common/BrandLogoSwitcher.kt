package com.example.gymtrack.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min

private val GymTitleRed = Color(0xFFD32F2F)
private val MealTitleGreen = Color(0xFF2E7D32)

/**
 * Título 3D de dos caras: GYM TRACK delante y MEAL TRACK detrás.
 * Al deslizar rotan de posición. Una animación de “invitación” gira un poco y vuelve.
 */
@Composable
fun BrandLogoSwitcher(
    nutritionMode: Boolean,
    onNutritionModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val cameraDistancePx = 12f * density
    val angle = remember { Animatable(if (nutritionMode) 180f else 0f) }
    var dragging by remember { mutableStateOf(false) }

    fun restAngle(meal: Boolean) = if (meal) 180f else 0f

    LaunchedEffect(nutritionMode, dragging) {
        if (dragging) return@LaunchedEffect
        val rest = restAngle(nutritionMode)
        if (abs(angle.value - rest) > 0.8f) {
            angle.animateTo(rest, tween(280, easing = FastOutSlowInEasing))
        }
        while (true) {
            delay(3400)
            val peek = if (nutritionMode) 180f - 38f else 38f
            angle.animateTo(peek, tween(520, easing = FastOutSlowInEasing))
            delay(220)
            angle.animateTo(
                restAngle(nutritionMode),
                spring(
                    dampingRatio = 0.52f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }

    val description = if (nutritionMode) {
        "Meal Track. Desliza a la derecha para Gym Track"
    } else {
        "Gym Track. Desliza a la izquierda para Meal Track"
    }

    Box(
        modifier = modifier
            .semantics { contentDescription = description }
            .pointerInput(nutritionMode) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onHorizontalDrag = { change, delta ->
                        change.consume()
                        val next = (angle.value - delta * 0.55f).coerceIn(0f, 180f)
                        scope.launch { angle.snapTo(next) }
                    },
                    onDragEnd = {
                        val goMeal = angle.value >= 90f
                        scope.launch {
                            angle.animateTo(
                                restAngle(goMeal),
                                tween(280, easing = FastOutSlowInEasing),
                            )
                            dragging = false
                            if (goMeal != nutritionMode) onNutritionModeChange(goMeal)
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            angle.animateTo(restAngle(nutritionMode), tween(200))
                            dragging = false
                        }
                    },
                )
            },
    ) {
        val rot = angle.value
        TitleFace(
            text = "GYM TRACK",
            color = GymTitleRed,
            rotationY = rot,
            cameraDistancePx = cameraDistancePx,
        )
        TitleFace(
            text = "MEAL TRACK",
            color = MealTitleGreen,
            rotationY = rot + 180f,
            cameraDistancePx = cameraDistancePx,
        )
    }
}

@Composable
private fun TitleFace(
    text: String,
    color: Color,
    rotationY: Float,
    cameraDistancePx: Float,
) {
    val wrapped = ((rotationY % 360f) + 360f) % 360f
    val facing = wrapped < 90f || wrapped > 270f
    val depth = (0.55f + 0.45f * abs(cos(Math.toRadians(wrapped.toDouble())).toFloat()))
        .let { min(1f, it) }
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.ExtraBold,
        color = color,
        maxLines = 1,
        modifier = Modifier.graphicsLayer {
            this.rotationY = rotationY
            cameraDistance = cameraDistancePx
            transformOrigin = TransformOrigin.Center
            alpha = if (facing) 1f else 0f
            scaleX = 0.92f + 0.08f * depth
            scaleY = 0.92f + 0.08f * depth
            shadowElevation = if (facing) 10.dp.toPx() * depth else 0f
        },
    )
}
