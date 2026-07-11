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
    private var activeFilterEffect: VideoFiltersEffect? = null
    private var activeDebandingEffect: VideoDebandingEffect? = null
    private var activeAmbientEffect: AmbientVideoEffect? = null
    private var isCurrentVideoHdr = false
    private var pendingJob: Job? = null
    private var transition = VideoFilterTransition.default()

    var currentFormat: Format? = null
        private set
    var currentDecoderName: String? = null
        private set
    var activeDecoderPriority: DecoderPriority = initialDecoderPriority
        private set

    val isCurrentHdr: Boolean
        get() = isCurrentVideoHdr

    val isEffectActive: Boolean
        get() = activeFilterEffect != null || activeAmbientEffect != null

    fun setDecoderPriority(decoderPriority: DecoderPriority) {
        activeDecoderPriority = decoderPriority
    }

    fun resetForMediaItem(player: ExoPlayer?) {
        currentFormat = null
        currentDecoderName = null
        isCurrentVideoHdr = false
        updateAvailability(player ?: return)
    }

    fun resetPipeline() {
        val wasAmbientEnabled = currentState.isAmbientEnabled
        val ambientTargetAspectRatio = currentState.ambientTargetAspectRatio
        currentState = VideoEffectsState(
            filters = VideoFilterPreferences.default(),
            decoderPriority = activeDecoderPriority,
            isAmbientEnabled = wasAmbientEnabled,
            ambientTargetAspectRatio = ambientTargetAspectRatio,
        )
        activeFilterEffect = null
        activeDebandingEffect = null
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
        currentFormat = format
        isCurrentVideoHdr = format.isHdrVideoFormat()
    }

    fun markFirstFrameRendered(
        player: ExoPlayer,
        format: Format?,
        preferences: PlayerPreferences,
    ) {
        isCurrentVideoHdr = format?.isHdrVideoFormat() == true
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
        schedule(
            player = player,
            videoFilters = preferences.toVideoFilterPreferences(),
            isAmbientEnabled = currentState.isAmbientEnabled,
            ambientTargetAspectRatio = currentState.ambientTargetAspectRatio,
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
        schedule(
            player = player,
            videoFilters = preferences.toVideoFilterPreferences(),
            isAmbientEnabled = currentState.isAmbientEnabled,
            ambientTargetAspectRatio = currentState.ambientTargetAspectRatio,
            delayMs = VIDEO_FILTER_PREVIEW_DELAY_MS,
            shouldSkipStalePreferences = false,
            logPrefix = "Preview",
        )
    }

    fun setAmbientMode(
        player: ExoPlayer?,
        isEnabled: Boolean,
        targetAspectRatio: Float,
    ) {
        val currentPlayer = player ?: currentPlayer() ?: return
        schedule(
            player = currentPlayer,
            videoFilters = currentPreferencesProvider().toVideoFilterPreferences(),
            isAmbientEnabled = isEnabled,
            ambientTargetAspectRatio = normalizedAmbientTargetAspectRatio(targetAspectRatio),
            delayMs = 0L,
            shouldSkipStalePreferences = true,
            logPrefix = "Apply",
            force = true,
        )
    }

    fun updateAvailability(player: ExoPlayer) {
        val currentMediaItem = player.currentMediaItem ?: return
        val isVideoEffectsAvailable = isAvailable()
        if (currentMediaItem.mediaMetadata.isVideoEffectsAvailable == isVideoEffectsAvailable) return

        player.replaceMediaItem(
            player.currentMediaItemIndex,
            currentMediaItem.copy(isVideoEffectsAvailable = isVideoEffectsAvailable),
        )
        Logger.debug(TAG, "Video effects availability: available=$isVideoEffectsAvailable decoder=$activeDecoderPriority")
    }

    fun isAvailable(): Boolean = shouldApplyVideoEffects(activeDecoderPriority)

    private fun schedule(
        player: ExoPlayer,
        videoFilters: VideoFilterPreferences,
        isAmbientEnabled: Boolean,
        ambientTargetAspectRatio: Float,
        delayMs: Long,
        shouldSkipStalePreferences: Boolean,
        logPrefix: String,
        force: Boolean = false,
    ) {
        pendingJob?.cancel()
        val normalizedAmbientTargetAspectRatio = normalizedAmbientTargetAspectRatio(ambientTargetAspectRatio)
        val targetState = VideoEffectsState(
            filters = videoFilters,
            decoderPriority = activeDecoderPriority,
            isAmbientEnabled = isAmbientEnabled,
            ambientTargetAspectRatio = normalizedAmbientTargetAspectRatio,
            ambientVisualMode = currentPreferencesProvider().ambientVisualMode,
            ambientGlowPreset = currentPreferencesProvider().ambientGlowPreset,
            ambientFrameExtendPreset = currentPreferencesProvider().ambientFrameExtendPreset,
            isPipelineInitialized = true,
        )
        if (!force && currentState == targetState) return

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

            applyEffects(
                player = player,
                videoFilters = videoFilters,
                isAmbientEnabled = isAmbientEnabled,
                ambientTargetAspectRatio = normalizedAmbientTargetAspectRatio,
                decoderPriority = decoderPriority,
                nextTransition = nextTransition,
            )
            Logger.debug(TAG, "$logPrefix video effects: filters=$videoFilters ambient=$isAmbientEnabled effect=$isEffectActive")
        }.also { job ->
            job.invokeOnCompletion {
                if (pendingJob == job) pendingJob = null
            }
        }
    }

    private fun applyEffects(
        player: ExoPlayer,
        videoFilters: VideoFilterPreferences,
        isAmbientEnabled: Boolean,
        ambientTargetAspectRatio: Float,
        decoderPriority: DecoderPriority,
        nextTransition: VideoFilterTransition,
    ) {
        val prefs = currentPreferencesProvider()
        val filterEffect = activeFilterEffect
        val shouldUseFilterEffect = shouldUseFilterEffect(videoFilters, decoderPriority)
        val shouldUseAmbientEffect = shouldUseAmbientEffect(isAmbientEnabled, decoderPriority)
        val canUpdateActiveFilterEffect = filterEffect != null &&
            shouldUseFilterEffect &&
            (activeDebandingEffect != null) == shouldUseDebandingEffect(videoFilters, decoderPriority) &&
            (activeAmbientEffect != null) == shouldUseAmbientEffect &&
            currentState.isAmbientEnabled == isAmbientEnabled &&
            currentState.ambientTargetAspectRatio == ambientTargetAspectRatio &&
            currentState.ambientVisualMode == prefs.ambientVisualMode &&
            currentState.ambientGlowPreset == prefs.ambientGlowPreset &&
            currentState.ambientFrameExtendPreset == prefs.ambientFrameExtendPreset
        if (canUpdateActiveFilterEffect) {
            transition = nextTransition
            filterEffect.updateTransition(nextTransition)
            activeDebandingEffect?.updateSettings(
                iterations = videoFilters.debandingIterations,
                threshold = videoFilters.debandingThreshold,
                range = videoFilters.debandingRange,
                grain = videoFilters.debandingGrain,
            )
            currentState = VideoEffectsState(
                filters = videoFilters,
                decoderPriority = decoderPriority,
                isAmbientEnabled = isAmbientEnabled,
                ambientTargetAspectRatio = ambientTargetAspectRatio,
                ambientVisualMode = prefs.ambientVisualMode,
                ambientGlowPreset = prefs.ambientGlowPreset,
                ambientFrameExtendPreset = prefs.ambientFrameExtendPreset,
                isPipelineInitialized = true,
            )
            refreshPausedFrame(player)
            updateAvailability(player)
            return
        }

        val effects = buildEffects(
            nextTransition = nextTransition,
            decoderPriority = decoderPriority,
            isAmbientEnabled = isAmbientEnabled,
            ambientTargetAspectRatio = ambientTargetAspectRatio,
        )
        if (effects.isEmpty() && activeFilterEffect == null && activeDebandingEffect == null && activeAmbientEffect == null) {
            currentState = VideoEffectsState(
                filters = videoFilters,
                decoderPriority = decoderPriority,
                isAmbientEnabled = isAmbientEnabled,
                ambientTargetAspectRatio = ambientTargetAspectRatio,
                ambientVisualMode = prefs.ambientVisualMode,
                ambientGlowPreset = prefs.ambientGlowPreset,
                ambientFrameExtendPreset = prefs.ambientFrameExtendPreset,
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
            isAmbientEnabled = isAmbientEnabled,
            ambientTargetAspectRatio = ambientTargetAspectRatio,
            ambientVisualMode = prefs.ambientVisualMode,
            ambientGlowPreset = prefs.ambientGlowPreset,
            ambientFrameExtendPreset = prefs.ambientFrameExtendPreset,
            isPipelineInitialized = true,
        )
        activeFilterEffect = effects.filterIsInstance<VideoFiltersEffect>().firstOrNull()
        activeDebandingEffect = effects.filterIsInstance<VideoDebandingEffect>().firstOrNull()
        activeAmbientEffect = effects.filterIsInstance<AmbientVideoEffect>().firstOrNull()
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
        isAmbientEnabled: Boolean,
        ambientTargetAspectRatio: Float,
    ): List<Effect> {
        val effects = mutableListOf<Effect>()
        if (shouldUseFilterEffect(nextTransition.targetFilters, decoderPriority)) {
            effects += VideoFiltersEffect(
                transition = nextTransition,
                transitionDurationMs = VIDEO_FILTER_TRANSITION_DURATION_MS,
            )
        }
        if (shouldUseDebandingEffect(nextTransition.targetFilters, decoderPriority)) {
            effects += VideoDebandingEffect(
                iterations = nextTransition.targetFilters.debandingIterations,
                threshold = nextTransition.targetFilters.debandingThreshold,
                range = nextTransition.targetFilters.debandingRange,
                grain = nextTransition.targetFilters.debandingGrain,
            )
        }
        if (shouldUseAmbientEffect(isAmbientEnabled, decoderPriority)) {
            val prefs = currentPreferencesProvider()
            effects += AmbientVideoEffect(
                targetAspectRatio = ambientTargetAspectRatio,
                mode = prefs.ambientVisualMode,
                glowPreset = prefs.ambientGlowPreset,
                frameExtendPreset = prefs.ambientFrameExtendPreset,
            )
        }
        return effects
    }

    private fun shouldUseFilterEffect(
        filters: VideoFilterPreferences,
        decoderPriority: DecoderPriority,
    ): Boolean = shouldApplyVideoEffects(decoderPriority) && filters.shouldCreateEffect()

    private fun shouldUseDebandingEffect(
        filters: VideoFilterPreferences,
        decoderPriority: DecoderPriority,
    ): Boolean = shouldApplyVideoEffects(decoderPriority) && filters.isDebandingEnabled

    private fun shouldUseAmbientEffect(
        isEnabled: Boolean,
        decoderPriority: DecoderPriority,
    ): Boolean = isEnabled && shouldApplyVideoEffects(decoderPriority)

    private fun normalizedAmbientTargetAspectRatio(targetAspectRatio: Float): Float = targetAspectRatio
        .takeIf { it.isFinite() && it > 0f }
        ?: DEFAULT_AMBIENT_TARGET_ASPECT_RATIO

    private fun currentPlayer(): ExoPlayer? = currentPlayerProvider()

    private companion object {
        private const val TAG = "VideoEffectsCoordinator"
        private const val VIDEO_FILTER_PREVIEW_DELAY_MS = 40L
        private const val VIDEO_FILTER_TRANSITION_DURATION_MS = 160L
        private const val PAUSED_FRAME_REFRESH_OFFSET_MS = 50L
        private const val DEFAULT_AMBIENT_TARGET_ASPECT_RATIO = 16f / 9f
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
        isDebandingEnabled = isVideoDebandingFilterEnabled,
        debandingIterations = if (isVideoDebandingFilterEnabled) {
            videoDebandingIterations.coerceIn(0, 16)
        } else {
            PlayerPreferences.DEFAULT_VIDEO_DEBANDING_ITERATIONS
        },
        debandingThreshold = if (isVideoDebandingFilterEnabled) {
            videoDebandingThreshold.coerceIn(0f, 4096f)
        } else {
            PlayerPreferences.DEFAULT_VIDEO_DEBANDING_THRESHOLD
        },
        debandingRange = if (isVideoDebandingFilterEnabled) {
            videoDebandingRange.coerceIn(1f, 64f)
        } else {
            PlayerPreferences.DEFAULT_VIDEO_DEBANDING_RANGE
        },
        debandingGrain = if (isVideoDebandingFilterEnabled) {
            videoDebandingGrain.coerceIn(0f, 4096f)
        } else {
            PlayerPreferences.DEFAULT_VIDEO_DEBANDING_GRAIN
        },
    )
    return if (filters.shouldCreateEffect()) filters else VideoFilterPreferences.default()
}

internal fun Format.isHdrVideoFormat(): Boolean {
    val transfer = colorInfo?.colorTransfer
    return transfer == C.COLOR_TRANSFER_ST2084 || transfer == C.COLOR_TRANSFER_HLG
}
