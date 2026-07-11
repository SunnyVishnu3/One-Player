package one.only.player.feature.player.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import one.only.player.core.model.SeekbarStyle
import one.only.player.core.ui.R

@Composable
fun BoxScope.SeekbarStyleSelectorView(
    modifier: Modifier = Modifier,
    shouldShow: Boolean,
    currentSeekbarStyle: SeekbarStyle,
    onSeekbarStyleClick: (SeekbarStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayView(
        modifier = modifier,
        shouldShow = shouldShow,
        title = stringResource(R.string.seekbar_style),
        testTag = "panel_seekbar_style",
    ) {
        SeekbarStyleSelectorContent(
            currentSeekbarStyle = currentSeekbarStyle,
            onSeekbarStyleClick = onSeekbarStyleClick,
            onDismiss = onDismiss,
        )
    }
}

@Composable
fun SeekbarStyleSelectorContent(
    currentSeekbarStyle: SeekbarStyle,
    onSeekbarStyleClick: (SeekbarStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
            .padding(horizontal = 24.dp)
            .selectableGroup(),
    ) {
        SeekbarStyle.entries.forEach { seekbarStyle ->
            RadioButtonRow(
                isSelected = seekbarStyle == currentSeekbarStyle,
                text = seekbarStyle.shortName(),
                testTag = "btn_seekbar_${seekbarStyle.name.lowercase()}",
                onClick = {
                    onSeekbarStyleClick(seekbarStyle)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun SeekbarStyle.shortName(): String = when (this) {
    SeekbarStyle.NORMAL -> stringResource(R.string.seekbar_style_normal)
    SeekbarStyle.THICK -> stringResource(R.string.seekbar_style_thick)
    SeekbarStyle.WAVY -> stringResource(R.string.seekbar_style_wavy)
}
