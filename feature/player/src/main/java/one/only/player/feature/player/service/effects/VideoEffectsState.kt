package one.only.player.feature.player.service.effects

import one.only.player.core.model.DecoderPriority

internal data class VideoEffectsState(
    val filters: VideoFilterPreferences,
    val decoderPriority: DecoderPriority,
    val isAmbientEnabled: Boolean = false,
    val ambientTargetAspectRatio: Float = 0f,
    val ambientVisualMode: one.only.player.core.model.AmbientVisualMode = one.only.player.core.model.AmbientVisualMode.GLOW,
    val ambientGlowPreset: one.only.player.core.model.AmbientGlowPreset = one.only.player.core.model.AmbientShaderPresets.glowBalanced,
    val ambientFrameExtendPreset: one.only.player.core.model.AmbientFrameExtendPreset = one.only.player.core.model.AmbientShaderPresets.frameExtendBalanced,
    val isPipelineInitialized: Boolean = false,
)
