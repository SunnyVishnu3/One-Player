package one.only.player.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import one.only.player.core.model.LiquidGlassPreferences
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens

val LocalLayerBackdrop = compositionLocalOf<LayerBackdrop?> { null }
val LocalLiquidGlassPreferences = compositionLocalOf { LiquidGlassPreferences() }

@Composable
fun LiquidGlassProvider(
    preferences: LiquidGlassPreferences,
    content: @Composable () -> Unit,
) {
    if (!preferences.isEnabled) {
        CompositionLocalProvider(
            LocalLayerBackdrop provides null,
            LocalLiquidGlassPreferences provides preferences
        ) {
            content()
        }
        return
    }

    val backdrop = rememberLayerBackdrop()
    CompositionLocalProvider(
        LocalLayerBackdrop provides backdrop,
        LocalLiquidGlassPreferences provides preferences
    ) {
        content()
    }
}

fun Modifier.liquidGlass(
    backdrop: LayerBackdrop?,
    preferences: LiquidGlassPreferences,
    shape: () -> Shape
): Modifier {
    return if (backdrop != null) {
        this.drawBackdrop(
            backdrop = backdrop,
            shape = shape,
            effects = {
                if (preferences.blur > 0f) {
                    blur(preferences.blur.dp.toPx())
                }
                if (preferences.isLensEnabled) {
                    lens(
                        refractionHeight = preferences.refractionHeight.dp.toPx(),
                        refractionAmount = preferences.refractionAmount.dp.toPx(),
                        chromaticAberration = preferences.chromaticAberration > 0f
                    )
                }
            }
        )
    } else {
        this
    }
}

fun Modifier.liquidGlassBackground(backdrop: LayerBackdrop?): Modifier {
    return if (backdrop != null) {
        this.layerBackdrop(backdrop)
    } else {
        this
    }
}
