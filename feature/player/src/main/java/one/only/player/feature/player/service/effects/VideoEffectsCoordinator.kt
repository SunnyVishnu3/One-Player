package one.only.player.feature.player.service.effects

import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.only.player.core.common.Logger
import one.only.player.core.model.DecoderPriority
import one.only.player.core.model.PlayerPreferences
import one.only.player.feature.player.extensions.copy
import one.only.player.feature.player.extensions.isVideoEffectsAvailable

internal class VideoEffectsCoordinator(
    private val scope: CoroutineScope,
    private val currentPreferencesProvider: () -> PlayerPreferences,
    private val currentPlayerProvider: () -> ExoPlayer?,
    initialDecoderPriority: DecoderPriority = DecoderPriority.AUTOMATIC,
) {

    private var currentState = VideoEffectsState(
        filters = VideoFilterPreferences.default(),
        decoderPriority = initialDecoderPriority,
    )
    private var activeEffect: VideoFiltersEffect? = null
    private var activeAmbientEffect: DynamicAmbientEffect? = null
    private var isCurrentVideoHdr = false
    private var hasRenderedFirstFrameForCurrentItem = false
    private var pendingJob: Job? = null
    private var transition = VideoFilterTransition.default()
    private var isAmbientModeEnabled: Boolean = false
    private var ambientScaleX: Float = 1.0f
    private var ambientScaleY: Float = 1.0f
    private var ambientOffsetX: Float = 0.0f
    private var ambientOffsetY: Float = 0.0f
    private var ambientContainerWidth: Int = 1920
    private var ambientContainerHeight: Int = 1080

    var currentFormat: Format? = null
        private set
    var currentDecoderName: String? = null
        private set
    var activeDecoderPriority: DecoderPriority = initialDecoderPriority
        private set

    val isCurrentHdr: Boolean
        get() = isCurrentVideoHdr

    val isEffectActive: Boolean
        get() = activeEffect != null

    fun setDecoderPriority(decoderPriority: DecoderPriority) {
        activeDecoderPriority = decoderPriority
    }

    fun resetForMediaItem(player: ExoPlayer?) {
        currentFormat = null
        currentDecoderName = null
        isCurrentVideoHdr = false
        hasRenderedFirstFrameForCurrentItem = false
        updateAvailability(player ?: return)
    }

    fun resetPipeline() {
        currentState = VideoEffectsState(
            filters = VideoFilterPreferences.default(),
            decoderPriority = activeDecoderPriority,
        )
        activeEffect = null
        activeAmbientEffect = null
        transition = VideoFilterTransition.default()
    }

    fun setDecoderName(decoderName: String) {
        currentDecoderName = decoderName
    }

    fun onVideoInputFormatChanged(
        player: ExoPlayer?,
        format: Format,
    ) {
        val wasVideoHdr = isCurrentVideoHdr
        currentFormat = format
        isCurrentVideoHdr = format.isHdrVideoFormat()
        if (wasVideoHdr != isCurrentVideoHdr || activeEffect != null) {
            player?.let { apply(it, currentPreferencesProvider(), force = true) }
        }
    }

    fun markFirstFrameRendered(
        player: ExoPlayer,
        format: Format?,
        preferences: PlayerPreferences,
    ) {
        isCurrentVideoHdr = format?.isHdrVideoFormat() == true
        hasRenderedFirstFrameForCurrentItem = true
        apply(player, preferences, force = true)
    }

    fun apply(preferences: PlayerPreferences) {
        val player = currentPlayer() ?: return
        apply(player, preferences)
    }

    fun apply(
        player: ExoPlayer,
        preferences: PlayerPreferences,
        force: Boolean = false,
    ) {
        isAmbientModeEnabled = preferences.isAmbienceModeEnabled
        schedule(
            player = player,
            videoFilters = preferences.toVideoFilterPreferences(),
            delayMs = 0L,
            shouldSkipStalePreferences = true,
            logPrefix = "Apply",
            force = force,
        )
    }

    fun preview(
        player: ExoPlayer?,
        preferences: PlayerPreferences,
    ) {
        if (player == null) return
        isAmbientModeEnabled = preferences.isAmbienceModeEnabled
        schedule(
            player = player,
            videoFilters = preferences.toVideoFilterPreferences(),
            delayMs = VIDEO_FILTER_PREVIEW_DELAY_MS,
            shouldSkipStalePreferences = false,
            logPrefix = "Preview",
        )
    }

    fun updateAvailability(player: ExoPlayer) {
        val currentMediaItem = player.currentMediaItem ?: return
        val isVideoEffectsAvailable = shouldApplyVideoEffects(activeDecoderPriority)
        if (currentMediaItem.mediaMetadata.isVideoEffectsAvailable == isVideoEffectsAvailable) return

        player.replaceMediaItem(
            player.currentMediaItemIndex,
            currentMediaItem.copy(isVideoEffectsAvailable = isVideoEffectsAvailable),
        )
        Logger.debug(TAG, "Video effects availability: available=$isVideoEffectsAvailable decoder=$activeDecoderPriority")
    }

    fun isAvailable(): Boolean = shouldApplyVideoEffects(activeDecoderPriority)

    fun setAmbientModeEnabled(player: ExoPlayer?, isEnabled: Boolean, preferences: PlayerPreferences) {
        if (isAmbientModeEnabled == isEnabled) return
        isAmbientModeEnabled = isEnabled
        if (player != null) {
            apply(player, preferences, force = true)
        }
    }

    private fun schedule(
        player: ExoPlayer,
        videoFilters: VideoFilterPreferences,
        delayMs: Long,
        shouldSkipStalePreferences: Boolean,
        logPrefix: String,
        force: Boolean = false,
    ) {
        pendingJob?.cancel()
        if (!force && currentState == VideoEffectsState(videoFilters, activeDecoderPriority, isPipelineInitialized = true)) return

        pendingJob = scope.launch {
            fun hasStalePreferences() = shouldSkipStalePreferences &&
                currentPreferencesProvider().toVideoFilterPreferences() != videoFilters

            if (delayMs > 0L) delay(delayMs)
            if (hasStalePreferences()) return@launch

            val decoderPriority = activeDecoderPriority
            val nextTransition = transition.to(
                targetFilters = videoFilters,
                startMs = android.os.SystemClock.elapsedRealtime(),
                durationMs = VIDEO_FILTER_TRANSITION_DURATION_MS,
            )
            if (hasStalePreferences()) return@launch

            applyEffects(player, videoFilters, decoderPriority, nextTransition)
            Logger.debug(TAG, "$logPrefix video filters: $videoFilters effect=${activeEffect != null}")
        }.also { job ->
            job.invokeOnCompletion {
                if (pendingJob == job) pendingJob = null
            }
        }
    }

    private fun applyEffects(
        player: ExoPlayer,
        videoFilters: VideoFilterPreferences,
        decoderPriority: DecoderPriority,
        nextTransition: VideoFilterTransition,
    ) {
        val effect = activeEffect
        val ambientEffect = activeAmbientEffect
        val canUpdateActiveEffect = effect != null && shouldUseEffect(videoFilters, decoderPriority) && 
            (isAmbientModeEnabled == (ambientEffect != null))
            
        if (canUpdateActiveEffect) {
            transition = nextTransition
            effect.updateTransition(nextTransition)
            currentState = VideoEffectsState(
                filters = videoFilters,
                decoderPriority = decoderPriority,
                isPipelineInitialized = true,
            )
            refreshPausedFrame(player)
            updateAvailability(player)
            return
        }

        val effects = buildEffects(nextTransition, decoderPriority)

        if (effects.isEmpty() && activeEffect == null && activeAmbientEffect == null) {
            currentState = VideoEffectsState(
                filters = videoFilters,
                decoderPriority = decoderPriority,
                isPipelineInitialized = false,
            )
            Logger.debug(TAG, "Skip setVideoEffects: no filters and pipeline not initialized")
            updateAvailability(player)
            return
        }
        transition = if (effects.isEmpty()) VideoFilterTransition.default() else nextTransition
        currentState = VideoEffectsState(
            filters = videoFilters,
            decoderPriority = decoderPriority,
            isPipelineInitialized = true,
        )
        activeEffect = effects.filterIsInstance<VideoFiltersEffect>().firstOrNull()
        activeAmbientEffect = effects.filterIsInstance<DynamicAmbientEffect>().firstOrNull()
        player.setVideoEffects(effects)
        refreshPausedFrame(player)
        updateAvailability(player)
    }

    private fun refreshPausedFrame(player: ExoPlayer) {
        if (player.playWhenReady) return
        if (player.playbackState != Player.STATE_READY) return
        val position = player.currentPosition.takeIf { it != C.TIME_UNSET } ?: return
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
        val targetPosition = duration
            ?.let { (position + PAUSED_FRAME_REFRESH_OFFSET_MS).coerceAtMost(it) }
            ?.takeIf { it != position }
            ?: (position - PAUSED_FRAME_REFRESH_OFFSET_MS).coerceAtLeast(0L)
        if (targetPosition == position) return
        player.seekTo(targetPosition)
        player.seekTo(position)
    }

    private fun buildEffects(
        nextTransition: VideoFilterTransition,
        decoderPriority: DecoderPriority,
    ): List<Effect> {
        if (!shouldApplyVideoEffects(decoderPriority)) return emptyList()

        val effects = mutableListOf<Effect>()

        if (shouldUseEffect(nextTransition.targetFilters, decoderPriority)) {
            effects.add(
                VideoFiltersEffect(
                    transition = nextTransition,
                    transitionDurationMs = VIDEO_FILTER_TRANSITION_DURATION_MS,
                )
            )
        }

        if (isAmbientModeEnabled && currentFormat != null) {
            effects.add(
                DynamicAmbientEffect(
                    scaleX = ambientScaleX,
                    scaleY = ambientScaleY,
                    offsetX = ambientOffsetX,
                    offsetY = ambientOffsetY,
                    containerWidth = ambientContainerWidth,
                    containerHeight = ambientContainerHeight,
                )
            )
        }

        return effects
    }

    fun updateAmbientParameters(
        player: ExoPlayer?,
        scaleX: Float,
        scaleY: Float,
        offsetX: Float,
        offsetY: Float,
        containerWidth: Int,
        containerHeight: Int,
    ) {
        val sizeChanged = ambientContainerWidth != containerWidth || ambientContainerHeight != containerHeight
        ambientScaleX = scaleX
        ambientScaleY = scaleY
        ambientOffsetX = offsetX
        ambientOffsetY = offsetY
        ambientContainerWidth = containerWidth
        ambientContainerHeight = containerHeight

        val ambientEffect = activeAmbientEffect
        if (ambientEffect != null) {
            ambientEffect.updateParameters(scaleX, scaleY, offsetX, offsetY, containerWidth, containerHeight)
            if (sizeChanged && player != null) {
                apply(player, currentPreferencesProvider(), force = true)
            }
        }
    }

    private fun shouldUseEffect(
        filters: VideoFilterPreferences,
        decoderPriority: DecoderPriority,
    ): Boolean = shouldApplyVideoEffects(decoderPriority) && filters.shouldCreateEffect()

    private fun currentPlayer(): ExoPlayer? = currentPlayerProvider()

    private companion object {
        private const val TAG = "VideoEffectsCoordinator"
        private const val VIDEO_FILTER_PREVIEW_DELAY_MS = 40L
        private const val VIDEO_FILTER_TRANSITION_DURATION_MS = 160L
        private const val PAUSED_FRAME_REFRESH_OFFSET_MS = 50L
    }
}

internal fun PlayerPreferences.toVideoFilterPreferences(): VideoFilterPreferences {
    if (!shouldApplyVideoFilters) return VideoFilterPreferences.default()

    val filters = VideoFilterPreferences(
        shouldApply = true,
        isBrightnessEnabled = isVideoBrightnessFilterEnabled,
        brightness = if (isVideoBrightnessFilterEnabled) {
            videoBrightness.coerceIn(PlayerPreferences.MIN_VIDEO_BRIGHTNESS, PlayerPreferences.MAX_VIDEO_BRIGHTNESS)
        } else {
            PlayerPreferences.DEFAULT_VIDEO_BRIGHTNESS
        },
        isContrastEnabled = isVideoContrastFilterEnabled,
        contrast = if (isVideoContrastFilterEnabled) {
            videoContrast.coerceIn(PlayerPreferences.MIN_VIDEO_CONTRAST, PlayerPreferences.MAX_VIDEO_CONTRAST)
        } else {
            PlayerPreferences.DEFAULT_VIDEO_CONTRAST
        },
        isSaturationEnabled = isVideoSaturationFilterEnabled,
        saturation = if (isVideoSaturationFilterEnabled) {
            videoSaturation.coerceIn(PlayerPreferences.MIN_VIDEO_SATURATION, PlayerPreferences.MAX_VIDEO_SATURATION)
        } else {
            PlayerPreferences.DEFAULT_VIDEO_SATURATION
        },
        isHueEnabled = isVideoHueFilterEnabled,
        hue = if (isVideoHueFilterEnabled) {
            videoHue.coerceIn(PlayerPreferences.MIN_VIDEO_HUE, PlayerPreferences.MAX_VIDEO_HUE)
        } else {
            PlayerPreferences.DEFAULT_VIDEO_HUE
        },
        isGammaEnabled = isVideoGammaFilterEnabled,
        gamma = if (isVideoGammaFilterEnabled) {
            videoGamma.coerceIn(PlayerPreferences.MIN_VIDEO_GAMMA, PlayerPreferences.MAX_VIDEO_GAMMA)
        } else {
            PlayerPreferences.DEFAULT_VIDEO_GAMMA
        },
        isSharpeningEnabled = isVideoSharpeningFilterEnabled,
        sharpening = if (isVideoSharpeningFilterEnabled) {
            videoSharpening.coerceIn(PlayerPreferences.DEFAULT_VIDEO_SHARPENING, PlayerPreferences.MAX_VIDEO_SHARPENING)
        } else {
            PlayerPreferences.DEFAULT_VIDEO_SHARPENING
        },
        isLineDarkenEnabled = isVideoLineDarkenFilterEnabled,
        lineDarken = if (isVideoLineDarkenFilterEnabled) {
            videoLineDarken.coerceIn(PlayerPreferences.DEFAULT_VIDEO_LINE_DARKEN, PlayerPreferences.MAX_VIDEO_LINE_DARKEN)
        } else {
            PlayerPreferences.DEFAULT_VIDEO_LINE_DARKEN
        },
        isLineThinEnabled = isVideoLineThinFilterEnabled,
        lineThin = if (isVideoLineThinFilterEnabled) {
            videoLineThin.coerceIn(PlayerPreferences.DEFAULT_VIDEO_LINE_THIN, PlayerPreferences.MAX_VIDEO_LINE_THIN)
        } else {
            PlayerPreferences.DEFAULT_VIDEO_LINE_THIN
        },
    )
    return if (filters.shouldCreateEffect()) filters else VideoFilterPreferences.default()
}

internal fun Format.isHdrVideoFormat(): Boolean {
    val transfer = colorInfo?.colorTransfer
    return transfer == C.COLOR_TRANSFER_ST2084 || transfer == C.COLOR_TRANSFER_HLG
}
