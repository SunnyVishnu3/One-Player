package one.only.player.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navOptions
import one.only.player.settings.screens.appearance.LiquidGlassPreferencesScreen

const val liquidGlassPreferencesNavigationRoute = "liquid_glass_preferences_route"

fun NavController.navigateToLiquidGlassPreferences(navOptions: NavOptions? = navOptions { launchSingleTop = true }) {
    this.navigate(liquidGlassPreferencesNavigationRoute, navOptions)
}

fun NavGraphBuilder.liquidGlassPreferencesScreen(onNavigateUp: () -> Unit) {
    composable(route = liquidGlassPreferencesNavigationRoute) {
        LiquidGlassPreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
