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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import one.only.player.core.model.AmbientMode
import one.only.player.core.model.AmbientQuality
import one.only.player.core.model.PlayerPreferences
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
                AmbientMode.entries.forEach { mode ->
                    RadioTextButton(
                        text = mode.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                        isSelected = preferences.ambientMode == mode,
                        onClick = { onPreferencesChange(preferences.copy(ambientMode = mode)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

            Text(
                text = "Quality",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AmbientQuality.entries.forEach { quality ->
                    RadioTextButton(
                        text = quality.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                        isSelected = preferences.ambientQuality == quality,
                        onClick = { onPreferencesChange(preferences.copy(ambientQuality = quality)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
