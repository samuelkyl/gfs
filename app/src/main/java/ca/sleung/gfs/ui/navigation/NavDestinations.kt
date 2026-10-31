package ca.sleung.gfs.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations for GFS (Guilt-Free Spending).
 */
sealed interface NavDestination {
    @Serializable
    data object Onboarding : NavDestination

    @Serializable
    data object Dashboard : NavDestination

    @Serializable
    data object History : NavDestination

    @Serializable
    data object Settings : NavDestination
}
