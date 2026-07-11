package one.only.player.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AmbientVisualMode(
    val label: String,
) {
    GLOW("Glow"),
    FRAME_EXTEND("Frame Extend"),
    YOUTUBE("YouTube"),
}

@Serializable
data class AmbientGlowPreset(
    val blurSamples: Int,
    val maxRadius: Float,
    val glowIntensity: Float,
    val satBoost: Float,
    val vignetteStrength: Float,
    val warmth: Float,
    val fadeCurve: Float,
    val opacity: Float,
)

@Serializable
data class AmbientFrameExtendPreset(
    val sampleBudget: Int,
    val extendStrength: Float,
    val detailProtection: Float,
    val glowMix: Float,
    val ditherNoise: Float,
    val bezelDepth: Float,
    val vignetteStrength: Float,
    val opacity: Float,
)

object AmbientShaderPresets {
    val glowFast =
        AmbientGlowPreset(
            blurSamples = 8,
            maxRadius = 0.15f,
            glowIntensity = 1.2f,
            satBoost = 1.0f,
            vignetteStrength = 0.3f,
            warmth = 0.0f,
            fadeCurve = 1.2f,
            opacity = 0.8f,
        )

    val glowBalanced =
        AmbientGlowPreset(
            blurSamples = 18,
            maxRadius = 0.28f,
            glowIntensity = 1.45f,
            satBoost = 1.25f,
            vignetteStrength = 0.55f,
            warmth = 0.0f,
            fadeCurve = 1.7f,
            opacity = 1.0f,
        )

    val glowHighQuality =
        AmbientGlowPreset(
            blurSamples = 24,
            maxRadius = 0.35f,
            glowIntensity = 1.5f,
            satBoost = 1.3f,
            vignetteStrength = 0.7f,
            warmth = 0.0f,
            fadeCurve = 1.8f,
            opacity = 1.0f,
        )

    val frameExtendFast =
        AmbientFrameExtendPreset(
            sampleBudget = 8,
            extendStrength = 0.40f,
            detailProtection = 0.86f,
            glowMix = 0.30f,
            ditherNoise = 0.0f,
            bezelDepth = 0.0f,
            vignetteStrength = 0.3f,
            opacity = 0.8f,
        )

    val frameExtendBalanced =
        AmbientFrameExtendPreset(
            sampleBudget = 24,
            extendStrength = 0.70f,
            detailProtection = 0.72f,
            glowMix = 0.12f,
            ditherNoise = 0.020f,
            bezelDepth = 0.0f,
            vignetteStrength = 0.55f,
            opacity = 1.0f,
        )

    val frameExtendHighQuality =
        AmbientFrameExtendPreset(
            sampleBudget = 32,
            extendStrength = 0.84f,
            detailProtection = 0.62f,
            glowMix = 0.08f,
            ditherNoise = 0.028f,
            bezelDepth = 0.0f,
            vignetteStrength = 0.62f,
            opacity = 1.0f,
        )
}
