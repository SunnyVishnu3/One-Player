package one.only.player.core.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import one.only.player.core.ui.R

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import one.only.player.core.ui.components.LocalLayerBackdrop
import one.only.player.core.ui.components.LocalLiquidGlassPreferences
import one.only.player.core.ui.components.liquidGlass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import one.only.player.core.ui.utils.InteractiveHighlight
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun LiquidButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    content: @Composable RowScope.() -> Unit
) {
    val backdrop = LocalLayerBackdrop.current
    val preferences = LocalLiquidGlassPreferences.current
    val animationScope = rememberCoroutineScope()

    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope
        )
    }

    if (backdrop != null) {
        val finalTint = if (tint.isSpecified) tint else MaterialTheme.colorScheme.primary
        CompositionLocalProvider(LocalContentColor provides finalTint) {
            Row(
                modifier
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            vibrancy()
                            if (preferences.blur > 0f) blur(preferences.blur.dp.toPx())
                            if (preferences.isLensEnabled) {
                                lens(
                                    refractionHeight = preferences.refractionHeight.dp.toPx(),
                                    refractionAmount = preferences.refractionAmount.dp.toPx(),
                                    chromaticAberration = preferences.chromaticAberration > 0f
                                )
                            }
                        },
                        layerBlock = if (isInteractive) {
                            {
                                val width = size.width
                                val height = size.height

                                val progress = interactiveHighlight.pressProgress
                                val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height, progress)

                                val maxOffset = size.minDimension
                                val initialDerivative = 0.05f
                                val offset = interactiveHighlight.offset
                                translationX = maxOffset * tanh((initialDerivative * offset.x / maxOffset).toDouble()).toFloat()
                                translationY = maxOffset * tanh((initialDerivative * offset.y / maxOffset).toDouble()).toFloat()

                                val maxDragScale = 4f.dp.toPx() / size.height
                                val offsetAngle = atan2(offset.y, offset.x)
                                scaleX =
                                    scale +
                                            maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                                            (width / height).fastCoerceAtMost(1f)
                                scaleY =
                                    scale +
                                            maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                                            (height / width).fastCoerceAtMost(1f)
                            }
                        } else {
                            null
                        },
                        onDrawSurface = {
                            if (tint.isSpecified) {
                                drawRect(tint, blendMode = BlendMode.Hue)
                                drawRect(tint.copy(alpha = preferences.tintOpacity))
                            }
                            if (surfaceColor.isSpecified) {
                                drawRect(surfaceColor)
                            }
                        }
                    )
                    .clickable(
                        interactionSource = null,
                        indication = if (isInteractive) null else LocalIndication.current,
                        role = Role.Button,
                        onClick = onClick
                    )
                    .then(
                        if (isInteractive) {
                            Modifier
                                .then(interactiveHighlight.modifier)
                                .then(interactiveHighlight.gestureModifier)
                        } else {
                            Modifier
                        }
                    )
                    .height(48f.dp)
                    .padding(horizontal = 16f.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (surfaceColor.isSpecified) surfaceColor else MaterialTheme.colorScheme.primary,
                contentColor = if (tint.isSpecified) tint else MaterialTheme.colorScheme.onPrimary
            ),
            content = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    content()
                }
            }
        )
    }
}

@Composable
fun DoneButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
) {
    val backdrop = LocalLayerBackdrop.current
    val preferences = LocalLiquidGlassPreferences.current
    if (backdrop != null) {
        LiquidButton(
            onClick = onClick,
            modifier = modifier,
            isInteractive = isEnabled,
            tint = Color(preferences.buttonColor),
            surfaceColor = Color.White.copy(alpha = 0.1f)
        ) {
            Text(text = stringResource(R.string.done))
        }
    } else {
        TextButton(
            enabled = isEnabled,
            onClick = onClick,
            modifier = modifier,
        ) {
            Text(text = stringResource(R.string.done))
        }
    }
}

@Composable
fun CancelButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
) {
    val backdrop = LocalLayerBackdrop.current
    val preferences = LocalLiquidGlassPreferences.current
    if (backdrop != null) {
        LiquidButton(
            onClick = onClick,
            modifier = modifier,
            isInteractive = isEnabled,
            tint = Color(preferences.buttonColor),
            surfaceColor = Color.White.copy(alpha = 0.1f)
        ) {
            Text(text = stringResource(R.string.cancel))
        }
    } else {
        TextButton(
            enabled = isEnabled,
            onClick = onClick,
            modifier = modifier,
        ) {
            Text(text = stringResource(R.string.cancel))
        }
    }
}
