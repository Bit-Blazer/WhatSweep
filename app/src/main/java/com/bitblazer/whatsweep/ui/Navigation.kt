package com.bitblazer.whatsweep.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
sealed class Screen(val route: String, val title: String) {
    object Results : Screen("results", "Results")
    object Settings : Screen("settings", "Settings")
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
    val currentDestination = currentBackStackEntry.value?.destination

    // Handle cleanup when leaving Results screen
    LaunchedEffect(currentDestination?.route) {
        if (currentDestination?.route != Screen.Results.route) {
            // Optionally pause scanning when navigating away from results
            // This prevents unnecessary processing while user is in settings
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Results.route,
        modifier = modifier
    ) {
        composable(
            route = Screen.Results.route,
        ) {
            // Handle back button behavior on main screen
            BackHandler(enabled = mainViewModel.selectedFiles.value.isNotEmpty()) {
                // Clear selection when back button is pressed
                mainViewModel.clearSelection()
            }

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