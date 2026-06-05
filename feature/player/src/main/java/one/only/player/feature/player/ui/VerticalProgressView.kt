package one.only.player.feature.player.ui

import androidx.annotation.IntRange
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import one.only.player.core.ui.R
import one.only.player.core.ui.theme.OnlyPlayerTheme

private const val NORMAL_MAX_PERCENTAGE = 100

@Composable
fun VerticalProgressView(
    modifier: Modifier = Modifier,
    width: Dp = 32.dp,
    icon: Painter,
    @IntRange(from = 0, to = 200) value: Int,
    maxValue: Int = NORMAL_MAX_PERCENTAGE,
    boostColor: Color = Color(0xFFFC6E6E),
) {
    val normalizedValue = value.coerceIn(0, maxValue)
    val fillFraction = normalizedValue.toFloat() / maxValue.toFloat()
    val isBoostActive = maxValue > NORMAL_MAX_PERCENTAGE && value > NORMAL_MAX_PERCENTAGE

    Column(
        modifier = modifier
            .heightIn(max = 250.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(vertical = 20.dp, horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(width),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = normalizedValue.toString() + "%",
                style = MaterialTheme.typography.titleSmall.copy(
                    color = Color.White,
                ),
                autoSize = TextAutoSize.StepBased(maxFontSize = MaterialTheme.typography.titleSmall.fontSize),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .width(width)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .width(width)
                    .fillMaxHeight(fillFraction)
                    .background(
                        if (isBoostActive) {
                            SolidColor(boostColor)
                        } else {
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    ),
            )
        }
        Box(
            modifier = Modifier.size(width),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Preview
@Composable
private fun VerticalProgressPreview() {
    OnlyPlayerTheme {
        VerticalProgressView(
            value = 50,
            icon = painterResource(R.drawable.ic_volume),
        )
    }
}

@Preview
@Composable
private fun VerticalProgressBoostPreview() {
    OnlyPlayerTheme {
        VerticalProgressView(
            value = 150,
            maxValue = 200,
            icon = painterResource(R.drawable.ic_volume),
        )
    }
}
