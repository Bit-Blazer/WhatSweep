package com.bitblazer.whatsweep.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitblazer.whatsweep.viewmodel.MainViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToResults: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    var permissionDenied by remember { mutableStateOf(false) }

    // Request storage permissions based on Android version
    val readMediaPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberMultiplePermissionsState(
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES
            )
        )
    } else {
        rememberMultiplePermissionsState(
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        )
    }

    // Check if we have MANAGE_EXTERNAL_STORAGE permission (for Android 11+)
    val managedStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        remember { mutableStateOf(Environment.isExternalStorageManager()) }
    } else {
        remember { mutableStateOf(true) }
    }

    // Show a dialog to request MANAGE_EXTERNAL_STORAGE permission
    var showManageStorageDialog by remember { mutableStateOf(false) }

    // Launch the permission request when the screen is first composed
    LaunchedEffect(Unit) {
        readMediaPermissions.launchMultiplePermissionRequest()

        // For Android 11+, check if we need the enhanced storage permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            showManageStorageDialog = true
        }
    }

    // Dialog to guide users to grant MANAGE_EXTERNAL_STORAGE permission
    if (showManageStorageDialog) {
        AlertDialog(
            onDismissRequest = { showManageStorageDialog = false },
            title = { Text("Additional Permission Required") },
            text = {
                Text(
                    "To access WhatsApp media folders, this app needs permission to manage external storage. " + "Please tap 'Open Settings' and grant the 'Allow access to manage all files' permission."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showManageStorageDialog = false
                        val intent =
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        context.startActivity(intent)
                    }) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showManageStorageDialog = false }) {
                    Text("Later")
                }
            })
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val takeFlags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)

//            val documentFile = DocumentFile.fromTreeUri(context, uri)
//            documentFile?.let { docFile ->
//                // Use the DocumentFile's uri rather than trying to convert to a File
//                val documentFileUri = docFile.uri
//                viewModel.scanDocumentTree(documentFileUri, context)
//                onNavigateToResults()
//            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("WhatSweep") }, actions = {
                androidx.compose.material3.IconButton(
                    onClick = onNavigateToSettings
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            })
        }) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "WhatSweep",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Scan and identify study notes in your WhatsApp media and local folders",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (permissionDenied) {
                Text(
                    text = "Storage permission is required to scan files. Please grant the permission in app settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }) {
                    Text("Open App Settings")
                }
            } else {
                Button(
                    onClick = {
                        if (hasStoragePermissions(readMediaPermissions)) {
                            viewModel.scanWhatsAppFolder()
                            onNavigateToResults()
                        } else {
                            permissionDenied = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.8f),
                    enabled = !isScanning && hasStoragePermissions(readMediaPermissions)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                    Text(
                        text = "Scan WhatsApp Media", modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (hasStoragePermissions(readMediaPermissions)) {
                            folderPicker.launch(null)
                        } else {
                            permissionDenied = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.8f),
                    enabled = !isScanning && hasStoragePermissions(readMediaPermissions)
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null)
                    Text(
                        text = "Select Custom Folder", modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
private fun hasStoragePermissions(
    readMediaPermissions: com.google.accompanist.permissions.MultiplePermissionsState
): Boolean {
    return readMediaPermissions.allPermissionsGranted
}