package one.only.player.settings.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import one.only.player.core.model.ThemeConfig
import one.only.player.core.ui.R

@Composable
fun ThemeConfig.name(): String {
    val stringRes = when (this) {
        ThemeConfig.SYSTEM -> R.string.follow_system_theme
        ThemeConfig.OFF -> R.string.light_theme
        ThemeConfig.ON -> R.string.dark_theme
        ThemeConfig.AMOLED -> R.string.amoled_theme
    }

    return stringResource(id = stringRes)
}
