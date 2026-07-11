package one.only.player.feature.player.ui.controls

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.milliseconds
import one.only.player.core.ui.R
import one.only.player.core.ui.designsystem.NextIcons
import one.only.player.feature.player.LocalControlsVisibilityState
import one.only.player.feature.player.extensions.formatted
import one.only.player.feature.player.extensions.noRippleClickable
import one.only.player.feature.player.state.MediaPresentationState
import one.only.player.feature.player.state.durationFormatted

@Composable
fun ControlsBottomModernView(
    modifier: Modifier = Modifier,
    mediaPresentationState: MediaPresentationState,
    pendingSeekPosition: Long?,
    isPlaying: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onRotateClick: () -> Unit,
    onPlaylistClick: () -> Unit,
    onPlaybackSpeedClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekEnd: () -> Unit,
    isSeeking: Boolean = false,
    useLegacySeekbar: Boolean = false,
    seekbarStyle: one.only.player.core.model.SeekbarStyle = one.only.player.core.model.SeekbarStyle.NORMAL,
    thumbnailBitmap: android.graphics.Bitmap? = null,
    skipMarkers: List<one.only.player.core.data.repository.IntroDbSegment> = emptyList(),
) {
    val systemBarsPadding = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val controlsVisibilityState = LocalControlsVisibilityState.current
    val displayedPosition = pendingSeekPosition ?: mediaPresentationState.position
    val displayedPendingPosition = (mediaPresentationState.duration - displayedPosition).coerceAtLeast(0L)
    Column(
        modifier = modifier
            .padding(systemBarsPadding)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ModernSeekbar(
            modifier = Modifier.padding(
                playerProgressHorizontalPadding(
                    containerHorizontalPadding = 8.dp,
                    trackEdgeInset = 7.dp,
                ),
            ),
            position = displayedPosition.toFloat(),
            duration = mediaPresentationState.duration.toFloat(),
            onSeek = {
                controlsVisibilityState?.showControls()
                onSeek(it.toLong())
            },
            onSeekFinished = { onSeekEnd() },
            isSeeking = isSeeking,
            useLegacySeekbar = useLegacySeekbar,
            seekbarStyle = seekbarStyle,
            thumbnailBitmap = thumbnailBitmap,
            skipMarkers = skipMarkers,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                modifier = Modifier.testTag("btn_play_pause_modern"),
                onClick = onPlayPauseClick,
            ) {
                Icon(
                    modifier = Modifier.size(28.dp),
                    imageVector = if (isPlaying) NextIcons.Pause else NextIcons.Play,
                    contentDescription = stringResource(R.string.player_controls_play_pause),
                    tint = Color.White,
                )
            }
            var shouldShowPendingPosition by rememberSaveable { mutableStateOf(false) }
            val positionText = when (shouldShowPendingPosition) {
                true -> "-${displayedPendingPosition.milliseconds.formatted()}"
                false -> displayedPosition.milliseconds.formatted()
            }
            Column(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .noRippleClickable {
                        shouldShowPendingPosition = !shouldShowPendingPosition
                    },
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = positionText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    maxLines = 1,
                )
                Text(
                    text = mediaPresentationState.durationFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                )
            }
            IconButton(
                modifier = Modifier.testTag("btn_previous_modern"),
                onClick = onPreviousClick,
                enabled = hasPrevious,
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = NextIcons.SkipPrevious,
                    contentDescription = stringResource(R.string.player_controls_previous),
                    tint = if (hasPrevious) Color.White else Color.White.copy(alpha = 0.4f),
                )
            }
            IconButton(
                modifier = Modifier.testTag("btn_next_modern"),
                onClick = onNextClick,
                enabled = hasNext,
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = NextIcons.SkipNext,
                    contentDescription = stringResource(R.string.player_controls_next),
                    tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.4f),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                modifier = Modifier.testTag("btn_rotate_modern"),
                onClick = onRotateClick,
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = NextIcons.Rotation,
                    contentDescription = stringResource(R.string.screen_rotation),
                    tint = Color.White,
                )
            }
            IconButton(
                modifier = Modifier.testTag("btn_playlist_modern"),
                onClick = onPlaylistClick,
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = NextIcons.PlaylistPlay,
                    contentDescription = stringResource(R.string.now_playing),
                    tint = Color.White,
                )
            }
            IconButton(
                modifier = Modifier.testTag("btn_speed_modern"),
                onClick = onPlaybackSpeedClick,
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = NextIcons.Speed,
                    contentDescription = stringResource(R.string.select_playback_speed),
                    tint = Color.White,
                )
            }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernSeekbar(
    modifier: Modifier = Modifier,
    position: Float,
    duration: Float,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    isSeeking: Boolean = false,
    useLegacySeekbar: Boolean = false,
    seekbarStyle: one.only.player.core.model.SeekbarStyle = one.only.player.core.model.SeekbarStyle.NORMAL,
    thumbnailBitmap: android.graphics.Bitmap? = null,
    skipMarkers: List<one.only.player.core.data.repository.IntroDbSegment> = emptyList(),
) {
    val accentColor = MaterialTheme.colorScheme.primary
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier = modifier.fillMaxWidth()) {
            SeekThumbnailPreviewBubble(
                position = position,
                duration = duration,
                visible = isSeeking,
                bitmap = thumbnailBitmap,
                isLoading = false,
                isPortrait = true,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (useLegacySeekbar) {
                Slider(
                    modifier = Modifier.fillMaxWidth(),
                    value = position.coerceIn(0f, duration.coerceAtLeast(0f)),
                    valueRange = 0f..duration.coerceAtLeast(0f),
                    onValueChange = onSeek,
                    onValueChangeFinished = onSeekFinished,
                    colors = SliderDefaults.colors(
                        activeTrackColor = accentColor,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        thumbColor = accentColor,
                    ),
                )
            } else {
                Slider(
                    modifier = Modifier.fillMaxWidth(),
                    value = position.coerceIn(0f, duration.coerceAtLeast(0f)),
                    valueRange = 0f..duration.coerceAtLeast(0f),
                    onValueChange = onSeek,
                    onValueChangeFinished = onSeekFinished,
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .border(2.dp, Color.White, CircleShape)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(accentColor),
                        )
                    },
                    track = { sliderState ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (seekbarStyle == one.only.player.core.model.SeekbarStyle.WAVY) {
                                val infiniteTransition = rememberInfiniteTransition(label = "wave_transition")
                                val phase by infiniteTransition.animateFloat(
                                    initialValue = 0f,
                                    targetValue = 2f * kotlin.math.PI.toFloat(),
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(2000, easing = LinearEasing),
                                        repeatMode = RepeatMode.Restart,
                                    ),
                                    label = "wave_phase",
                                )
                                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(24.dp)) {
                                    val progress = sliderState.value / duration.coerceAtLeast(1f)
                                    val activeWidth = size.width * progress
                                    val waveFrequency = 0.05f
                                    val waveAmplitude = 4.dp.toPx()
                                    val path = androidx.compose.ui.graphics.Path()
                                    var x = 0f
                                    path.moveTo(x, size.height / 2f)
                                    while (x < activeWidth) {
                                        val y = (size.height / 2f) + kotlin.math.sin((x * waveFrequency) - phase) * waveAmplitude
                                        path.lineTo(x, y.toFloat())
                                        x += 2f
                                    }
                                    drawPath(
                                        path = path,
                                        color = accentColor,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
                                    )
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.3f),
                                        start = androidx.compose.ui.geometry.Offset(activeWidth, size.height / 2f),
                                        end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f),
                                        strokeWidth = 4.dp.toPx(),
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                    )
                                }
                            } else {
                                val trackHeight = if (seekbarStyle == one.only.player.core.model.SeekbarStyle.THICK) 8.dp else 4.dp
                                SliderDefaults.Track(
                                    sliderState = sliderState,
                                    modifier = Modifier.height(trackHeight),
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = accentColor,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                    ),
                                    thumbTrackGapSize = 0.dp,
                                    drawStopIndicator = null,
                                )
                            }
                            if (duration > 0f && skipMarkers.isNotEmpty()) {
                                androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                                    skipMarkers.forEach { segment ->
                                        val startMs = segment.normalizedStart * 1000.0
                                        val endMs = segment.normalizedEnd * 1000.0
                                        val startPercent = (startMs / duration).coerceIn(0.0, 1.0).toFloat()
                                        val endPercent = (endMs / duration).coerceIn(0.0, 1.0).toFloat()
                                        if (endPercent > startPercent) {
                                            val startX = size.width * startPercent
                                            val endX = size.width * endPercent
                                            drawRect(
                                                color = Color(0xFFF44336).copy(alpha = 0.8f), // Red/Accent color for skip markers
                                                topLeft = androidx.compose.ui.geometry.Offset(x = startX, y = 0f),
                                                size = androidx.compose.ui.geometry.Size(width = endX - startX, height = size.height),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}
