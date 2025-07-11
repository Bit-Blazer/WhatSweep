package com.bitblazer.whatsweep.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bitblazer.whatsweep.BuildConfig
import com.bitblazer.whatsweep.util.PreferencesManager
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateUp: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val prefsManager = remember { PreferencesManager(context) }

    var includePdfScanning by remember { mutableStateOf(prefsManager.includePdfScanning) }
    var showConfidenceScores by remember { mutableStateOf(prefsManager.showConfidenceScores) }
    var confidenceThreshold by remember { mutableFloatStateOf(prefsManager.confidenceThreshold) }

    Scaffold(topBar = {
        TopAppBar(title = { Text(text = "Settings") }, navigationIcon = {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Navigate back to main screen"
                )
            }
        })
    }, snackbarHost = { SnackbarHost(snackbarHostState) }) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Scan Settings Section
            SettingsSection(title = "Scan Settings") {
                ScanSettingsCard(
                    includePdfScanning = includePdfScanning,
                    onIncludePdfScanningChange = { newValue ->
                        includePdfScanning = newValue
                        prefsManager.includePdfScanning = newValue
                    },
                    onClearCache = {
                        coroutineScope.launch {
                            prefsManager.clearCache()
                            context.cacheDir.deleteRecursively()
                            snackbarHostState.showSnackbar("Cache cleared successfully")
                        }
                    })
            }

            // Classification Settings Section
            SettingsSection(title = "Classification Settings") {
                ClassificationSettingsCard(
                    showConfidenceScores = showConfidenceScores,
                    onShowConfidenceScoresChange = { newValue ->
                        showConfidenceScores = newValue
                        prefsManager.showConfidenceScores = newValue
                    },
                    confidenceThreshold = confidenceThreshold,
                    onConfidenceThresholdChange = { newValue ->
                        confidenceThreshold = newValue
                        prefsManager.confidenceThreshold = newValue
                    })
            }

            // About Section
            SettingsSection(title = "About") { AboutCard() }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        content()
    }
}

@Composable
private fun ScanSettingsCard(
    includePdfScanning: Boolean,
    onIncludePdfScanningChange: (Boolean) -> Unit,
    onClearCache: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSwitchItem(
                title = "Include PDF scanning",
                description = "Convert PDF pages to images and analyze them",
                checked = includePdfScanning,
                onCheckedChange = onIncludePdfScanningChange
            )

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = "Clear Classification Cache",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "Clears the saved classification data. Next scan will process all files again.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Button(onClick = onClearCache, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Clear Cache", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ClassificationSettingsCard(
    showConfidenceScores: Boolean,
    onShowConfidenceScoresChange: (Boolean) -> Unit,
    confidenceThreshold: Float,
    onConfidenceThresholdChange: (Float) -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSwitchItem(
                title = "Show confidence scores",
                description = "Display confidence scores on results screen",
                checked = showConfidenceScores,
                onCheckedChange = onShowConfidenceScoresChange
            )

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row {
                    Text(
                        text = "Confidence Threshold",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "${(confidenceThreshold * 100).roundToInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "Minimum confidence level to classify an item as a note",
                    style = MaterialTheme.typography.bodyMedium
                )

                Slider(
                    value = confidenceThreshold,
                    onValueChange = onConfidenceThresholdChange,
                    valueRange = 0.5f..1f,
                    steps = 9
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Lower (more inclusive)", style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = "Higher (more selective)", style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutCard() {
    Card {
        Column(
            modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(text = "About WhatSweep", style = MaterialTheme.typography.titleMedium)
            }

            Text(
                text = "WhatSweep is an offline app for identifying and managing handwritten notes among your media files. It uses a custom TensorFlow Lite model to classify images and PDF pages.",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "🔒 All processing happens on your device, and no data is sent to external servers.",
                style = MaterialTheme.typography.bodyMedium
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Version", style = MaterialTheme.typography.titleSmall)
                Text(text = BuildConfig.VERSION_NAME, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
        }

        Switch(
            checked = checked, onCheckedChange = onCheckedChange, thumbContent = {
                Icon(
                    imageVector = if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize)
                )
            })
    }
}