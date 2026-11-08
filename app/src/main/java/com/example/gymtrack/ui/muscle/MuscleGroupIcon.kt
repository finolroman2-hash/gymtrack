package com.example.gymtrack.ui.muscle

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Icono de grupo muscular (PNG con fondo transparente).
 * Modo claro: `muscle_*` — trazo oscuro. Modo oscuro: `muscle_*_night` — trazo claro.
 */
@Composable
fun MuscleGroupIcon(
    tag: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    contentDescription: String? = null,
    darkTheme: Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f,
) {
    val layout = MuscleTagUi.layoutFor(tag)
    val sx = layout.scaleX * layout.uniformScale
    val sy = layout.scaleY * layout.uniformScale

    when (val spec = MuscleTagUi.iconSpec(tag, darkTheme)) {
        is MuscleTagUi.IconSpec.Single -> {
            Image(
                painter = painterResource(spec.resId),
                contentDescription = contentDescription,
                modifier = modifier
                    .size(size)
                    .offset(x = size * layout.offsetXFactor)
                    .graphicsLayer {
                        scaleX = sx
                        scaleY = sy
                    },
                contentScale = ContentScale.Fit,
            )
        }
        is MuscleTagUi.IconSpec.Paired -> {
            Box(
                modifier = modifier.size(size),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier
                        .size(
                            width = size * layout.pairedWidthMultiplier * layout.uniformScale,
                            height = size * layout.uniformScale,
                        )
                        .offset(x = (size * layout.offsetXFactor))
                        .graphicsLayer {
                            scaleX = layout.scaleX
                            scaleY = layout.scaleY
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(layout.pairedGap(size)),
                ) {
                    Image(
                        painter = painterResource(spec.leftResId),
                        contentDescription = contentDescription,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentScale = ContentScale.Fit,
                    )
                    Image(
                        painter = painterResource(spec.rightResId),
                        contentDescription = null,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}
