package one.only.player.settings.screens.appearance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.only.player.core.ui.R
import one.only.player.core.ui.components.ListSectionTitle
import one.only.player.core.ui.components.NextTopAppBar
import one.only.player.core.ui.components.PreferenceSlider
import one.only.player.core.ui.components.PreferenceSwitch
import one.only.player.core.ui.designsystem.NextIcons
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.settings.composables.ColorMixer

import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LiquidGlassPreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: LiquidGlassPreferencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences = uiState.preferences.liquidGlassPreferences

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = "Liquid Glass",
                navigationIcon = {
                    FilledTonalIconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(state = rememberScrollState())
                .padding(innerPadding.withBottomFallback())
                .padding(horizontal = 16.dp),
        ) {
            ListSectionTitle(text = "General")
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                PreferenceSwitch(
                    title = "Enable Liquid Glass",
                    description = "Apply liquid glass effects to UI",
                    icon = NextIcons.Style,
                    isChecked = preferences.isEnabled,
                    onClick = { viewModel.onEvent(LiquidGlassPreferencesEvent.ToggleEnabled) },
                    isFirstItem = true,
                    isLastItem = true
                )
            }

            if (preferences.isEnabled) {
                ListSectionTitle(text = "Effects")
                Column(
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    PreferenceSlider(
                        title = "Blur",
                        description = preferences.blur.toInt().toString(),
                        value = preferences.blur,
                        valueRange = 0f..100f,
                        onValueChange = { viewModel.onEvent(LiquidGlassPreferencesEvent.UpdateBlur(it)) },
                        isFirstItem = true
                    )
                    PreferenceSwitch(
                        title = "Lens Effect",
                        description = "Enable refraction/distortion",
                        isChecked = preferences.isLensEnabled,
                        onClick = { viewModel.onEvent(LiquidGlassPreferencesEvent.ToggleLens) }
                    )
                    if (preferences.isLensEnabled) {
                        PreferenceSlider(
                            title = "Refraction Height",
                            description = preferences.refractionHeight.toInt().toString(),
                            value = preferences.refractionHeight,
                            valueRange = 0f..50f,
                            onValueChange = { viewModel.onEvent(LiquidGlassPreferencesEvent.UpdateRefractionHeight(it)) }
                        )
                        PreferenceSlider(
                            title = "Refraction Amount",
                            description = preferences.refractionAmount.toInt().toString(),
                            value = preferences.refractionAmount,
                            valueRange = 0f..100f,
                            onValueChange = { viewModel.onEvent(LiquidGlassPreferencesEvent.UpdateRefractionAmount(it)) }
                        )
                    }
                    PreferenceSlider(
                        title = "Chromatic Aberration",
                        description = String.format(Locale.US, "%.2f", preferences.chromaticAberration),
                        value = preferences.chromaticAberration,
                        valueRange = 0f..10f,
                        onValueChange = { viewModel.onEvent(LiquidGlassPreferencesEvent.UpdateChromaticAberration(it)) }
                    )
                    PreferenceSwitch(
                        title = "Rim Lighting",
                        description = "Simulate light hitting edges",
                        isChecked = preferences.rimLighting,
                        onClick = { viewModel.onEvent(LiquidGlassPreferencesEvent.ToggleRimLighting) }
                    )
                    PreferenceSlider(
                        title = "Parallax",
                        description = String.format(Locale.US, "%.2f", preferences.parallax),
                        value = preferences.parallax,
                        valueRange = 0f..1f,
                        onValueChange = { viewModel.onEvent(LiquidGlassPreferencesEvent.UpdateParallax(it)) },
                        isLastItem = true
                    )
                }

                ListSectionTitle(text = "Shape")
                Column(
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    PreferenceSlider(
                        title = "Shape Roundness",
                        description = preferences.shapeRoundness.toInt().toString(),
                        value = preferences.shapeRoundness,
                        valueRange = 0f..64f,
                        onValueChange = { viewModel.onEvent(LiquidGlassPreferencesEvent.UpdateShapeRoundness(it)) },
                        isFirstItem = true
                    )
                    PreferenceSlider(
                        title = "Icon Roundness",
                        description = preferences.iconRoundness.toInt().toString(),
                        value = preferences.iconRoundness,
                        valueRange = 0f..32f,
                        onValueChange = { viewModel.onEvent(LiquidGlassPreferencesEvent.UpdateIconRoundness(it)) },
                        isLastItem = true
                    )
                }

                ListSectionTitle(text = "Colors")
                Column(
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    PreferenceSlider(
                        title = "Tint Opacity",
                        description = String.format(Locale.US, "%.2f", preferences.tintOpacity),
                        value = preferences.tintOpacity,
                        valueRange = 0f..1f,
                        onValueChange = { viewModel.onEvent(LiquidGlassPreferencesEvent.UpdateTintOpacity(it)) },
                        isFirstItem = true
                    )
                    ListSectionTitle(text = "Tint Color")
                    ColorMixer(
                        initialColor = Color(preferences.tintColor),
                        onColorChanged = { viewModel.onEvent(LiquidGlassPreferencesEvent.UpdateTintColor(it.toArgb().toLong())) }
                    )

                    ListSectionTitle(text = "Button Color")
                    ColorMixer(
                        initialColor = Color(preferences.buttonColor),
                        onColorChanged = { viewModel.onEvent(LiquidGlassPreferencesEvent.UpdateButtonColor(it.toArgb().toLong())) }
                    )

                    ListSectionTitle(text = "Slider Color")
                    ColorMixer(
                        initialColor = Color(preferences.sliderColor),
                        onColorChanged = { viewModel.onEvent(LiquidGlassPreferencesEvent.UpdateSliderColor(it.toArgb().toLong())) },
                        isLastItem = true
                    )
                }
            }
        }
    }
}
