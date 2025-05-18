package com.bitblazer.whatsweep.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bitblazer.whatsweep.util.PreferencesManager
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit, modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val prefsManager = remember { PreferencesManager(context) }

    var includePdfScanning by remember { mutableStateOf(prefsManager.includePdfScanning) }
    var showConfidenceScores by remember { mutableStateOf(prefsManager.showConfidenceScores) }
    var confidenceThreshold by remember { mutableFloatStateOf(prefsManager.confidenceThreshold) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") }, navigationIcon = {
                IconButton(onClick = onNavigateUp) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            })
        }) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Scan Settings",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSwitchItem(
                title = "Include PDF scanning",
                description = "Convert PDF pages to images and analyze them",
                checked = includePdfScanning,
                onCheckedChange = {
                    includePdfScanning = it
                    prefsManager.includePdfScanning = it
                })

            Spacer(modifier = Modifier.height(16.dp))

            androidx.compose.material3.Button(
                onClick = {
                    prefsManager.clearClassificationCache()
                }, modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear Classification Cache")
            }

            Text(
                text = "Clears the saved classification data. Next scan will process all files again.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text(
                text = "Classification Settings",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSwitchItem(
                title = "Show confidence scores",
                description = "Display classification confidence percentages on results screen",
                checked = showConfidenceScores,
                onCheckedChange = {
                    showConfidenceScores = it
                    prefsManager.showConfidenceScores = it
                })

            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Confidence Threshold", style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = "${(confidenceThreshold * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text(
                    text = "Minimum confidence level to classify an item as a note",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Slider(
                    value = confidenceThreshold, onValueChange = {
                        confidenceThreshold = it
                        prefsManager.confidenceThreshold = it
                    }, valueRange = 0.5f..0.95f, steps = 8, modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Lower (more inclusive)",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = "Higher (more selective)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "About",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "WhatSweep is an offline app for identifying and managing handwritten notes among your media files. It uses a custom TensorFlow Lite model to classify images and PDF pages. All processing happens on your device, and no data is sent to external servers.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Version 1.0", style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title, style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Switch(
            checked = checked, onCheckedChange = onCheckedChange
        )
    }
}