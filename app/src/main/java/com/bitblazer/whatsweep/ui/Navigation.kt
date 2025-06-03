package com.bitblazer.whatsweep.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bitblazer.whatsweep.ui.screens.ResultsScreen
import com.bitblazer.whatsweep.ui.screens.SettingsScreen
import com.bitblazer.whatsweep.viewmodel.MainViewModel

/**
 * Sealed class defining navigation destinations.
 */
sealed class Screen(val route: String) {
    object Results : Screen("results")
    object Settings : Screen("settings")
}

/**
 * Main app navigation component with enhanced navigation handling.
 */
@Composable
fun AppNavigation(
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    // Observe current back stack entry for navigation state
    val currentBackStackEntry = navController.currentBackStackEntryAsState()
    currentBackStackEntry.value?.destination

    NavHost(
        navController = navController,
        startDestination = Screen.Results.route,
        modifier = modifier
    ) {
        composable(
            route = Screen.Results.route,
        ) {
            ResultsScreen(
                viewModel = mainViewModel,
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route) {
                        // Prevent multiple settings screens in back stack
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = Screen.Settings.route,
        ) {
            SettingsScreen(
                onNavigateUp = {
                    navController.navigateUp()
                }
            )
        }
    }
}