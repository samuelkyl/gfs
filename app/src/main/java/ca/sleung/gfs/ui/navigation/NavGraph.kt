package ca.sleung.gfs.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ca.sleung.gfs.ui.screens.dashboard.DashboardScreen
import ca.sleung.gfs.ui.screens.history.HistoryScreen
import ca.sleung.gfs.ui.screens.onboarding.OnboardingScreen
import ca.sleung.gfs.ui.screens.settings.SettingsScreen

@Composable
fun GFSNavGraph(
    startDestination: NavDestination,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    fun navigateBackSafely() {
        val currentState = navController.currentBackStackEntry?.lifecycle?.currentState
        if (currentState == Lifecycle.State.RESUMED) {
            navController.navigateUp()
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            fadeIn(animationSpec = tween(220)) +
                slideInHorizontally(initialOffsetX = { it / 12 }, animationSpec = tween(220))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(180))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(200))
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(180)) +
                slideOutHorizontally(targetOffsetX = { it / 12 }, animationSpec = tween(180))
        }
    ) {
        composable<NavDestination.Onboarding> {
            OnboardingScreen(
                onOnboardingComplete = {
                    navController.navigate(NavDestination.Dashboard) {
                        popUpTo<NavDestination.Onboarding> { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<NavDestination.Dashboard> {
            DashboardScreen(
                onNavigateToHistory = {
                    navController.navigate(NavDestination.History) {
                        launchSingleTop = true
                    }
                },
                onNavigateToSettings = {
                    navController.navigate(NavDestination.Settings) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<NavDestination.History> {
            HistoryScreen(
                onNavigateBack = ::navigateBackSafely
            )
        }

        composable<NavDestination.Settings> {
            SettingsScreen(
                onNavigateBack = ::navigateBackSafely
            )
        }
    }
}
