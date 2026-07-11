package one.only.player.feature.player.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private fun percentage(
    value: Int,
    range: ClosedRange<Int>,
): Float = ((value - range.start - 0f) / (range.endInclusive - range.start)).coerceIn(0f, 1f)

@Composable
fun VerticalSlider(
    value: Int,
    range: ClosedRange<Int>,
    modifier: Modifier = Modifier,
    overflowValue: Int? = null,
    overflowRange: ClosedRange<Int>? = null,
    colorStart: Color = MaterialTheme.colorScheme.primaryContainer,
    colorEnd: Color = MaterialTheme.colorScheme.primary,
) {
    val coercedValue = value.coerceIn(range)
    Box(
        modifier = modifier
            .height(130.dp)
            .width(36.dp)
            .clip(MaterialTheme.shapes.large)
            .background(Color.Black.copy(alpha = 0.3f)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val targetHeight by animateFloatAsState(
            targetValue = percentage(coercedValue, range),
            animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f),
            label = "vsliderheight",
        )
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(targetHeight.coerceAtLeast(0.05f))
                .clip(MaterialTheme.shapes.large)
                .background(Brush.verticalGradient(listOf(colorStart, colorEnd))),
        )
        if (overflowRange != null && overflowValue != null) {
            val overflowHeight by animateFloatAsState(
                targetValue = percentage(overflowValue, overflowRange),
                label = "vslideroverflowheight",
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(overflowHeight)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.errorContainer),
            )
        }
    }
}

@Composable
fun VerticalProgressView(
    modifier: Modifier = Modifier,
    icon: Painter,
    value: Int,
    maxValue: Int = 100,
    boostColor: Color = Color(0xFFFC6E6E),
) {
    val isBoost = maxValue > 100
    val normalValue = value.coerceIn(0, 100)
    val boostValue = if (isBoost && value > 100) value - 100 else null
    val boostRange = if (isBoost) 0..(maxValue - 100) else null

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.Black.copy(alpha = 0.5f),
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "$value%",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 48.dp),
            )
            VerticalSlider(
                value = normalValue,
                range = 0..100,
                overflowValue = boostValue,
                overflowRange = boostRange,
                colorStart = MaterialTheme.colorScheme.primaryContainer,
                colorEnd = MaterialTheme.colorScheme.primary,
            )
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
