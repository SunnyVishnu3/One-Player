package one.only.player.core.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import one.only.player.core.ui.designsystem.NextIcons

import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import androidx.compose.foundation.shape.CircleShape

import one.only.player.core.ui.components.LocalLayerBackdrop
import one.only.player.core.ui.components.LocalLiquidGlassPreferences
import one.only.player.core.ui.components.liquidGlass

@Composable
fun NextSwitch(
    isChecked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    checkedIcon: ImageVector = NextIcons.Check,
) {
    val backdrop = LocalLayerBackdrop.current
    val preferences = LocalLiquidGlassPreferences.current

    if (backdrop != null) {
        LiquidToggle(
            selected = { isChecked },
            onSelect = { onCheckedChange?.invoke(it) },
            modifier = modifier,
            accentColor = Color(preferences.buttonColor)
        )
    } else {
        val thumbContent: (@Composable () -> Unit)? = if (isChecked) {
            {
                Icon(
                    imageVector = checkedIcon,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            }
        } else {
            null
        }

        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = isEnabled,
            thumbContent = thumbContent,
        )
    }
}
