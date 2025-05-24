package com.bitblazer.whatsweep.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bitblazer.whatsweep.ui.screens.ResultsScreen
import com.bitblazer.whatsweep.ui.screens.SettingsScreen
import com.bitblazer.whatsweep.viewmodel.MainViewModel

sealed class Screen(val route: String) {
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
        navController = navController, startDestination = Screen.Results.route, modifier = modifier
    ) {
        composable(Screen.Results.route) {
            ResultsScreen(
                viewModel = mainViewModel,
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) })
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateUp = { navController.navigateUp() })
        }
    }
}