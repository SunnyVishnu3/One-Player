package one.only.player.settings.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import one.only.player.core.ui.components.NextSegmentedListItem

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ColorMixer(
    initialColor: Color,
    onColorChanged: (Color) -> Unit,
    modifier: Modifier = Modifier,
    isFirstItem: Boolean = false,
    isLastItem: Boolean = false,
) {
    var color1 by remember { mutableStateOf(initialColor) }
    var color2 by remember { mutableStateOf(Color.White) }
    var mixFraction by remember { mutableFloatStateOf(0f) }
    val controller1 = rememberColorPickerController()
    val controller2 = rememberColorPickerController()

    val mixedColor = lerp(color1, color2, mixFraction)

    LaunchedEffect(mixedColor) {
        onColorChanged(mixedColor)
    }

    NextSegmentedListItem(
        modifier = modifier,
        isFirstItem = isFirstItem,
        isLastItem = isLastItem,
        content = {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(text = "Color 1", style = MaterialTheme.typography.titleSmall)
                HsvColorPicker(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    controller = controller1,
                    onColorChanged = { colorEnvelope ->
                        color1 = colorEnvelope.color
                    },
                    initialColor = initialColor
                )

                Text(text = "Color 2", style = MaterialTheme.typography.titleSmall)
                HsvColorPicker(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    controller = controller2,
                    onColorChanged = { colorEnvelope ->
                        color2 = colorEnvelope.color
                    },
                    initialColor = Color.White
                )

                Text(text = "Mix Ratio: ${(mixFraction * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = mixFraction,
                    onValueChange = { mixFraction = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(color1)
                    )
                    Text(text = "+")
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(color2)
                    )
                    Text(text = "=")
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(mixedColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "#${Integer.toHexString(mixedColor.toArgb()).uppercase()}",
                            color = if (mixedColor.luminance() > 0.5f) Color.Black else Color.White
                        )
                    }
                }
            }
        }
    )
}

private fun Color.luminance(): Float {
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}
