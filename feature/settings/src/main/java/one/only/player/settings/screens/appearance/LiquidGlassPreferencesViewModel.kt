package one.only.player.settings.screens.appearance

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.only.player.core.data.repository.PreferencesRepository
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.LiquidGlassPreferences

@HiltViewModel
class LiquidGlassPreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val uiStateInternal = MutableStateFlow(
        LiquidGlassPreferencesUiState(
            preferences = preferencesRepository.applicationPreferences.value,
        ),
    )
    val uiState = uiStateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { preferences ->
                uiStateInternal.update { it.copy(preferences = preferences) }
            }
        }
    }

    fun onEvent(event: LiquidGlassPreferencesEvent) {
        when (event) {
            LiquidGlassPreferencesEvent.ToggleEnabled -> toggleEnabled()
            is LiquidGlassPreferencesEvent.UpdateBlur -> updateBlur(event.value)
            is LiquidGlassPreferencesEvent.UpdateRefractionHeight -> updateRefractionHeight(event.value)
            is LiquidGlassPreferencesEvent.UpdateRefractionAmount -> updateRefractionAmount(event.value)
            is LiquidGlassPreferencesEvent.UpdateChromaticAberration -> updateChromaticAberration(event.value)
            LiquidGlassPreferencesEvent.ToggleRimLighting -> toggleRimLighting()
            LiquidGlassPreferencesEvent.ToggleLens -> toggleLens()
            is LiquidGlassPreferencesEvent.UpdateTintColor -> updateTintColor(event.color)
            is LiquidGlassPreferencesEvent.UpdateTintOpacity -> updateTintOpacity(event.value)
            is LiquidGlassPreferencesEvent.UpdateShapeRoundness -> updateShapeRoundness(event.value)
            is LiquidGlassPreferencesEvent.UpdateIconRoundness -> updateIconRoundness(event.value)
            is LiquidGlassPreferencesEvent.UpdateParallax -> updateParallax(event.value)
            is LiquidGlassPreferencesEvent.UpdateButtonColor -> updateButtonColor(event.color)
            is LiquidGlassPreferencesEvent.UpdateSliderColor -> updateSliderColor(event.color)
        }
    }

    private fun toggleEnabled() {
        updatePreferences { it.copy(isEnabled = !it.isEnabled) }
    }

    private fun updateBlur(value: Float) {
        updatePreferences { it.copy(blur = value) }
    }

    private fun updateRefractionHeight(value: Float) {
        updatePreferences { it.copy(refractionHeight = value) }
    }

    private fun updateRefractionAmount(value: Float) {
        updatePreferences { it.copy(refractionAmount = value) }
    }

    private fun updateChromaticAberration(value: Float) {
        updatePreferences { it.copy(chromaticAberration = value) }
    }

    private fun toggleRimLighting() {
        updatePreferences { it.copy(rimLighting = !it.rimLighting) }
    }

    private fun toggleLens() {
        updatePreferences { it.copy(isLensEnabled = !it.isLensEnabled) }
    }

    private fun updateTintColor(color: Long) {
        updatePreferences { it.copy(tintColor = color) }
    }

    private fun updateTintOpacity(value: Float) {
        updatePreferences { it.copy(tintOpacity = value) }
    }

    private fun updateShapeRoundness(value: Float) {
        updatePreferences { it.copy(shapeRoundness = value) }
    }

    private fun updateIconRoundness(value: Float) {
        updatePreferences { it.copy(iconRoundness = value) }
    }

    private fun updateParallax(value: Float) {
        updatePreferences { it.copy(parallax = value) }
    }

    private fun updateButtonColor(color: Long) {
        updatePreferences { it.copy(buttonColor = color) }
    }

    private fun updateSliderColor(color: Long) {
        updatePreferences { it.copy(sliderColor = color) }
    }

    private fun updatePreferences(transform: (LiquidGlassPreferences) -> LiquidGlassPreferences) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(liquidGlassPreferences = transform(it.liquidGlassPreferences))
            }
        }
    }
}

@Stable
data class LiquidGlassPreferencesUiState(
    val preferences: ApplicationPreferences = ApplicationPreferences(),
)

sealed interface LiquidGlassPreferencesEvent {
    data object ToggleEnabled : LiquidGlassPreferencesEvent
    data object ToggleLens : LiquidGlassPreferencesEvent
    data class UpdateBlur(val value: Float) : LiquidGlassPreferencesEvent
    data class UpdateRefractionHeight(val value: Float) : LiquidGlassPreferencesEvent
    data class UpdateRefractionAmount(val value: Float) : LiquidGlassPreferencesEvent
    data class UpdateChromaticAberration(val value: Float) : LiquidGlassPreferencesEvent
    data object ToggleRimLighting : LiquidGlassPreferencesEvent
    data class UpdateTintColor(val color: Long) : LiquidGlassPreferencesEvent
    data class UpdateTintOpacity(val value: Float) : LiquidGlassPreferencesEvent
    data class UpdateShapeRoundness(val value: Float) : LiquidGlassPreferencesEvent
    data class UpdateIconRoundness(val value: Float) : LiquidGlassPreferencesEvent
    data class UpdateParallax(val value: Float) : LiquidGlassPreferencesEvent
    data class UpdateButtonColor(val color: Long) : LiquidGlassPreferencesEvent
    data class UpdateSliderColor(val color: Long) : LiquidGlassPreferencesEvent
}
