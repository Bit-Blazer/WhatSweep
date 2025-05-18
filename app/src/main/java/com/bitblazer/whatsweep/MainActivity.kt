package com.bitblazer.whatsweep

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.bitblazer.whatsweep.ui.AppNavigation
import com.bitblazer.whatsweep.ui.theme.AppTheme
import com.bitblazer.whatsweep.viewmodel.MainViewModel


class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppTheme {
                AppNavigation(mainViewModel = viewModel)
            }
        }
    }
}