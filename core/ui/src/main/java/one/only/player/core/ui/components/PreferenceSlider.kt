package one.only.player.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import androidx.compose.foundation.shape.RoundedCornerShape
import one.only.player.core.ui.components.LocalLayerBackdrop
import one.only.player.core.ui.components.LocalLiquidGlassPreferences
import one.only.player.core.ui.components.liquidGlass

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PreferenceSlider(
    modifier: Modifier = Modifier,
    sliderModifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    isEnabled: Boolean = true,
    isSliderEnabled: Boolean = isEnabled,
    isFirstItem: Boolean = false,
    isLastItem: Boolean = false,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {},
    trailingContent: @Composable () -> Unit = {},
) {
    val backdrop = LocalLayerBackdrop.current
    val preferences = LocalLiquidGlassPreferences.current
    NextSegmentedListItem(
        modifier = modifier,
        onClick = {},
        onLongClick = null,
        isEnabled = isEnabled,
        isFirstItem = isFirstItem,
        isLastItem = isLastItem,
        leadingContent = icon?.let {
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        supportingContent = {
            Column {
                description?.let {
                    Text(text = description)
                }
                if (backdrop != null) {
                    LiquidSlider(
                        value = { value },
                        onValueChange = onValueChange,
                        valueRange = valueRange,
                        modifier = sliderModifier,
                        accentColor = Color(preferences.sliderColor)
                    )
                } else {
                    Slider(
                        modifier = sliderModifier.fillMaxWidth(),
                        enabled = isSliderEnabled,
                        value = value,
                        valueRange = valueRange,
                        onValueChange = onValueChange,
                        onValueChangeFinished = onValueChangeFinished,
                    )
                }
            }
        },
        content = {
            Text(text = title)
        },
        trailingContent = trailingContent,
    )
}
