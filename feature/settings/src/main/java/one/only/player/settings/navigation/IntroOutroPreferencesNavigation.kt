package one.only.player.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import one.only.player.settings.screens.introoutro.IntroOutroPreferencesScreen

const val introOutroPreferencesNavigationRoute = "intro_outro_preferences_route"

fun NavController.navigateToIntroOutroPreferences(navOptions: NavOptions? = null) {
    this.navigate(introOutroPreferencesNavigationRoute, navOptions)
}

fun NavGraphBuilder.introOutroPreferencesScreen(onNavigateUp: () -> Unit) {
    composable(route = introOutroPreferencesNavigationRoute) {
        IntroOutroPreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
