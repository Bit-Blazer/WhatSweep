package com.bitblazer.whatsweep.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bitblazer.whatsweep.ui.screens.HomeScreen
import com.bitblazer.whatsweep.ui.screens.ResultsScreen
import com.bitblazer.whatsweep.ui.screens.SettingsScreen
import com.bitblazer.whatsweep.viewmodel.MainViewModel

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Results : Screen("results")
    object Settings : Screen("settings")
}

@Composable
fun AppNavigation(
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController, startDestination = Screen.Home.route, modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = mainViewModel,
                onNavigateToResults = { navController.navigate(Screen.Results.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) })
        }

        composable(Screen.Results.route) {
            ResultsScreen(
                viewModel = mainViewModel, onNavigateUp = { navController.navigateUp() })
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateUp = { navController.navigateUp() })
        }
    }
}