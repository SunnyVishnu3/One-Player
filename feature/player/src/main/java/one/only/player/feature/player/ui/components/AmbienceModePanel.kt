package one.only.player.feature.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import one.only.player.core.model.AmbientVisualMode
import one.only.player.core.model.PlayerPreferences
import one.only.player.core.ui.components.PreferenceSlider
import one.only.player.core.ui.components.PreferenceSwitchWithDivider
import one.only.player.core.ui.components.RadioTextButton

@Composable
fun AmbienceModePanel(
    modifier: Modifier = Modifier,
    isAmbienceModeEnabled: Boolean,
    setAmbienceModeEnabled: (Boolean) -> Unit,
    preferences: PlayerPreferences,
    onPreferencesChange: (PlayerPreferences) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Ambience Mode",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )

        PreferenceSwitchWithDivider(
            title = "Enable Ambience Mode",
            isChecked = isAmbienceModeEnabled,
            onClick = { setAmbienceModeEnabled(!isAmbienceModeEnabled) },
        )

        if (isAmbienceModeEnabled) {
            Text(
                text = "Visual Mode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AmbientVisualMode.entries.forEach { mode ->
                    RadioTextButton(
                        text = mode.label,
                        isSelected = preferences.ambientVisualMode == mode,
                        onClick = { onPreferencesChange(preferences.copy(ambientVisualMode = mode)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

            if (preferences.ambientVisualMode == AmbientVisualMode.GLOW) {
                val glowPreset = preferences.ambientGlowPreset
                Text(
                    text = "Glow Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                PreferenceSlider(
                    title = "Blur Samples",
                    value = glowPreset.blurSamples.toFloat(),
                    valueRange = 5f..64f,
                    onValueChange = { onPreferencesChange(preferences.copy(ambientGlowPreset = glowPreset.copy(blurSamples = it.toInt()))) },
                    description = glowPreset.blurSamples.toString(),
                )
                PreferenceSlider(
                    title = "Spread",
                    value = glowPreset.maxRadius,
                    valueRange = 0.05f..0.80f,
                    onValueChange = { onPreferencesChange(preferences.copy(ambientGlowPreset = glowPreset.copy(maxRadius = it))) },
                    description = "%.2f".format(glowPreset.maxRadius),
                )
                PreferenceSlider(
                    title = "Intensity",
                    value = glowPreset.glowIntensity,
                    valueRange = 0.5f..3.0f,
                    onValueChange = { onPreferencesChange(preferences.copy(ambientGlowPreset = glowPreset.copy(glowIntensity = it))) },
                    description = "%.1f".format(glowPreset.glowIntensity),
                )
                PreferenceSlider(
                    title = "Opacity",
                    value = glowPreset.opacity,
                    valueRange = 0.1f..2.0f,
                    onValueChange = { onPreferencesChange(preferences.copy(ambientGlowPreset = glowPreset.copy(opacity = it))) },
                    description = "%.1f".format(glowPreset.opacity),
                )
                PreferenceSlider(
                    title = "Saturation Boost",
                    value = glowPreset.satBoost,
                    valueRange = 1.0f..3.0f,
                    onValueChange = { onPreferencesChange(preferences.copy(ambientGlowPreset = glowPreset.copy(satBoost = it))) },
                    description = "%.1f".format(glowPreset.satBoost),
                )
                PreferenceSlider(
                    title = "Vignette Strength",
                    value = glowPreset.vignetteStrength,
                    valueRange = 0.0f..1.0f,
                    onValueChange = { onPreferencesChange(preferences.copy(ambientGlowPreset = glowPreset.copy(vignetteStrength = it))) },
                    description = "%.2f".format(glowPreset.vignetteStrength),
                )
            }

            if (preferences.ambientVisualMode == AmbientVisualMode.FRAME_EXTEND) {
                val frameExtendPreset = preferences.ambientFrameExtendPreset
                Text(
                    text = "Frame Extend Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                PreferenceSlider(
                    title = "Sample Budget",
                    value = frameExtendPreset.sampleBudget.toFloat(),
                    valueRange = 5f..64f,
                    onValueChange = { onPreferencesChange(preferences.copy(ambientFrameExtendPreset = frameExtendPreset.copy(sampleBudget = it.toInt()))) },
                    description = frameExtendPreset.sampleBudget.toString(),
                )
                PreferenceSlider(
                    title = "Extend Strength",
                    value = frameExtendPreset.extendStrength,
                    valueRange = 0.5f..3.0f,
                    onValueChange = { onPreferencesChange(preferences.copy(ambientFrameExtendPreset = frameExtendPreset.copy(extendStrength = it))) },
                    description = "%.1f".format(frameExtendPreset.extendStrength),
                )
                PreferenceSlider(
                    title = "Glow Mix",
                    value = frameExtendPreset.glowMix,
                    valueRange = 0.0f..1.0f,
                    onValueChange = { onPreferencesChange(preferences.copy(ambientFrameExtendPreset = frameExtendPreset.copy(glowMix = it))) },
                    description = "%.2f".format(frameExtendPreset.glowMix),
                )
                PreferenceSlider(
                    title = "Bezel Depth",
                    value = frameExtendPreset.bezelDepth,
                    valueRange = 0.0f..0.2f,
                    onValueChange = { onPreferencesChange(preferences.copy(ambientFrameExtendPreset = frameExtendPreset.copy(bezelDepth = it))) },
                    description = "%.2f".format(frameExtendPreset.bezelDepth),
                )
                PreferenceSlider(
                    title = "Opacity",
                    value = frameExtendPreset.opacity,
                    valueRange = 0.1f..2.0f,
                    onValueChange = { onPreferencesChange(preferences.copy(ambientFrameExtendPreset = frameExtendPreset.copy(opacity = it))) },
                    description = "%.1f".format(frameExtendPreset.opacity),
                )
            }
        }
    }
}
