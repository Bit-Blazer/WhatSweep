package com.bitblazer.whatsweep.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.bitblazer.whatsweep.model.ClassificationLabel
import com.bitblazer.whatsweep.model.FileType
import com.bitblazer.whatsweep.model.MediaFile
import com.bitblazer.whatsweep.util.PreferencesManager
import com.bitblazer.whatsweep.viewmodel.DeleteState
import com.bitblazer.whatsweep.viewmodel.MainViewModel
import com.bitblazer.whatsweep.viewmodel.ScanState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ResultsScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
) {
    val context = LocalContext.current

    // Collect state using proper StateFlow collectors
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val deleteState by viewModel.deleteState.collectAsStateWithLifecycle()
    val notesFiles by viewModel.notesFiles.collectAsStateWithLifecycle()
    val otherFiles by viewModel.otherFiles.collectAsStateWithLifecycle()
    val selectedFiles by viewModel.selectedFiles.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    // Create scroll behavior for TopAppBar
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // Grid/List view toggle
    var isGridView by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showManageStorageDialog by remember { mutableStateOf(false) }

    // Auto-close delete dialog when deletion is completed
    LaunchedEffect(deleteState) {
        if (deleteState is DeleteState.Completed || deleteState is DeleteState.Error) {
            kotlinx.coroutines.delay(1500) // Show result briefly
            showDeleteDialog = false
        }
    }

    // Handle READ_EXTERNAL_STORAGE permission
    val readStoragePermissionState =
        rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)

    // Handle MANAGE_EXTERNAL_STORAGE for Android 11+ (API 30+)
    var hasManageExternalStoragePermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else true // Not required for Android 10 and below
        )
    }

    // Calculate overall permission status
    var hasBasicPermissions = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        // For Android 10 and below, we need READ_EXTERNAL_STORAGE
        readStoragePermissionState.status.isGranted
    } else true // Not required for Android 11 and above

    val hasAllPermissions = hasBasicPermissions && hasManageExternalStoragePermission
    val permissionDenied =
        !hasBasicPermissions && readStoragePermissionState.status.shouldShowRationale

    // Request permissions on first launch
    LaunchedEffect(Unit) {
        if (!hasBasicPermissions) {
            readStoragePermissionState.launchPermissionRequest()
        }

        // For Android 11+, check if we need MANAGE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasManageExternalStoragePermission) {
            showManageStorageDialog = true
        }
    }

    // Monitor permission changes when app resumes
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Update MANAGE_EXTERNAL_STORAGE permission status
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    hasManageExternalStoragePermission = Environment.isExternalStorageManager()
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Show MANAGE_EXTERNAL_STORAGE permission dialog
    if (showManageStorageDialog) {
        AlertDialog(onDismissRequest = { showManageStorageDialog = false }, title = {
            Text(
                "Additional Permission Required", style = MaterialTheme.typography.headlineSmall
            )
        }, text = {
            Text(
                "To access WhatsApp media folders, this app needs permission to manage external storage. " + "Please tap 'Open Settings' and grant the 'Allow access to manage all files' permission.",
                style = MaterialTheme.typography.bodyMedium
            )
        }, confirmButton = {
            Button(
                onClick = {
                    showManageStorageDialog = false
                    val intent =
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = "package:${context.packageName}".toUri()
                        }
                    context.startActivity(intent)
                }) {
                Text("Open Settings", style = MaterialTheme.typography.labelLarge)
            }
        }, dismissButton = {
            Button(onClick = { showManageStorageDialog = false }) {
                Text("Later", style = MaterialTheme.typography.labelLarge)
            }
        })
    }

    val tabs = listOf(
        "Notes (${notesFiles.size})" to Icons.AutoMirrored.Outlined.Notes,
        "Others (${otherFiles.size})" to Icons.Outlined.AllInclusive
    )

    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            count = selectedFiles.size,
            deleteState = deleteState,
            onConfirm = { viewModel.deleteSelectedFiles() },
            onDismiss = {
                showDeleteDialog = false
                viewModel.resetDeleteState()
            })
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior, title = {
                if (selectedFiles.isEmpty()) {
                    Text("WhatSweep")
                } else {
                    Text("${selectedFiles.size} selected")
                }
            }, navigationIcon = {
                if (selectedFiles.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearSelection) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Clear selection"
                        )
                    }
                }
            }, colors = if (selectedFiles.isNotEmpty()) {
                // Apply primary color tint to app bar when items are selected
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                TopAppBarDefaults.topAppBarColors()
            }, actions = {
                if (selectedFiles.isNotEmpty()) {
                    // Show delete button when items are selected
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Outlined.Delete, contentDescription = "Delete selected files"
                        )
                    }
                    // Select all toggle
                    val currentPage = pagerState.currentPage
                    val currentPageFiles =
                        if (currentPage == 0) notesFiles else otherFiles                    // Check if all files in the current tab are selected
                    val allSelected =
                        currentPageFiles.isNotEmpty() && currentPageFiles.all { it.isSelected }
                    // Select all checkbox
                    Checkbox(
                        checked = allSelected, onCheckedChange = { checked ->
                            // Select or deselect all files in the current tab
                            if (currentPage == 0) {
                                notesFiles.forEach { viewModel.setSelectionState(it, checked) }
                            } else {
                                otherFiles.forEach { viewModel.setSelectionState(it, checked) }
                            }
                        }, colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.onPrimary,
                            uncheckedColor = MaterialTheme.colorScheme.onPrimary,
                            checkmarkColor = MaterialTheme.colorScheme.onPrimary
                        ), modifier = Modifier.padding(8.dp)
                    )
                } else {
                    // Default view: show scan button, view toggle, and settings
                    // Main scan button
                    Button(
                        onClick = { viewModel.scanWhatsAppFolder() },
                        enabled = scanState !is ScanState.Scanning && hasAllPermissions,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Scan", style = MaterialTheme.typography.labelLarge)
                    }

                    // View toggle button
                    if (notesFiles.isNotEmpty() || otherFiles.isNotEmpty()) {
                        // Only show view toggle if we have content to display
                        IconButton(onClick = { isGridView = !isGridView }) {
                            Icon(
                                if (isGridView) Icons.AutoMirrored.Outlined.FormatListBulleted else Icons.Outlined.GridView,
                                contentDescription = if (isGridView) "Switch to List View" else "Switch to Grid View"
                            )
                        }
                    }
                    // Settings button
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                }
            })
        }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Handle different states based on permissions and scan status
            when {
                // Show permission states when permissions are missing
                !hasAllPermissions -> {
                    PermissionScreen(permissionDenied = permissionDenied, onRequestPermissions = {
                        readStoragePermissionState.launchPermissionRequest()
                    }, onRequestManageStorage = { showManageStorageDialog = true })
                }

                // Handle scan states when permissions are granted
                hasAllPermissions -> {
                    when (scanState) {
                        is ScanState.Idle -> {
                            if (notesFiles.isEmpty() && otherFiles.isEmpty()) {
                                // Empty state - show welcome message and instructions
                                EmptyStateContent()
                            } else {
                                // Show content with tabs
                                ContentTabs(
                                    pagerState = pagerState,
                                    tabs = tabs,
                                    notesFiles = notesFiles,
                                    otherFiles = otherFiles,
                                    viewModel = viewModel,
                                    isGridView = isGridView,
                                    coroutineScope = coroutineScope
                                )
                            }
                        }

                        is ScanState.Scanning -> {
                            ScanningContent(
                                scanState = scanState as ScanState.Scanning,
                                notesFiles = notesFiles,
                                otherFiles = otherFiles
                            )
                        }

                        is ScanState.Completed -> {
                            // Show content with tabs after scan completion
                            ContentTabs(
                                pagerState = pagerState,
                                tabs = tabs,
                                notesFiles = notesFiles,
                                otherFiles = otherFiles,
                                viewModel = viewModel,
                                isGridView = isGridView,
                                coroutineScope = coroutineScope
                            )
                        }

                        is ScanState.Error -> {
                            ErrorStateContent(
                                errorMessage = (scanState as ScanState.Error).message,
                                onRetry = { viewModel.scanWhatsAppFolder() })
                        }
                    }
                }

                // Fallback: show empty state when permissions are still being requested
                else -> {
                    EmptyStateContent()
                }
            }            // Show error message if present
            errorMessage?.let { error ->
                ErrorMessageSnackbar(message = error, onDismiss = { viewModel.clearError() })
            }
        }
    }
}

@Composable
private fun EmptyStateContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)
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

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Tap the 'Scan' button in the top bar to begin.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ScanningContent(
    scanState: ScanState.Scanning,
    notesFiles: List<MediaFile>,
    otherFiles: List<MediaFile>,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Scanning and classifying files...",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp)) // Show processed file count
            Text(
                text = buildString {
                    append("Processed: ${scanState.progress.filesProcessed}")
                    if (scanState.progress.totalFilesFound > 0) {
                        append("/${scanState.progress.totalFilesFound}")
                    }
                    append(" files")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Show classification counts
            Text(
                text = "Found: ${notesFiles.size} notes, ${otherFiles.size} other files",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (scanState.progress.currentDirectory.isNotEmpty()) {
                Text(
                    text = "Processing: ${scanState.progress.currentDirectory}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            if (scanState.progress.totalFilesFound > 0) {
                // Show determinate progress when we know the total
                LinearProgressIndicator(
                    progress = {
                        scanState.progress.filesProcessed.toFloat() / scanState.progress.totalFilesFound.toFloat()
                    }, modifier = Modifier.fillMaxWidth(0.7f)
                )
            } else {
                // Show indeterminate progress when total is unknown
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.7f))
            }
        }
    }
}

@Composable
private fun ErrorStateContent(
    errorMessage: String,
    onRetry: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Scan Error",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = onRetry) {
                Text(
                    "Retry Scan", style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun PermissionScreen(
    permissionDenied: Boolean,
    onRequestPermissions: () -> Unit,
    onRequestManageStorage: () -> Unit,
) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)
        ) {
            // Choose appropriate content based on Android version and state
            val bodyText: String
            val primaryButtonAction: () -> Unit

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ - Only need MANAGE_EXTERNAL_STORAGE
                bodyText =
                    "To access WhatsApp media files, this app needs permission to manage external storage. " + "Please tap 'Grant Permission' and allow 'All files access' in the settings."
                primaryButtonAction = onRequestManageStorage
            } else {
                // Android 10 and below - Need READ_EXTERNAL_STORAGE
                if (permissionDenied) {
                    bodyText =
                        "Storage permission is required to scan files. Please grant the permission to continue."
                    primaryButtonAction = onRequestPermissions
                } else {
                    bodyText =
                        "WhatSweep needs storage permission to scan and analyze your WhatsApp media files."
                    primaryButtonAction = onRequestPermissions
                }
            }

            Text(
                text = "Storage Permission Required",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = bodyText,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = if (permissionDenied) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { primaryButtonAction.invoke() }, modifier = Modifier.fillMaxWidth(0.7f)
            ) {
                Text("Grant Permission", style = MaterialTheme.typography.labelLarge)
            }

            // Show additional options for denied permissions
            if (permissionDenied) {
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }) {
                    Text("Open App Settings", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContentTabs(
    pagerState: PagerState,
    tabs: List<Pair<String, ImageVector>>,
    notesFiles: List<MediaFile>,
    otherFiles: List<MediaFile>,
    viewModel: MainViewModel,
    isGridView: Boolean,
    coroutineScope: CoroutineScope,
) {
    PrimaryTabRow(selectedTabIndex = pagerState.currentPage, divider = { HorizontalDivider() }) {
        tabs.forEachIndexed { index, (title, icon) ->
            LeadingIconTab(
                selected = pagerState.currentPage == index,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                text = { Text(title) },
                icon = { Icon(icon, contentDescription = null) })
        }
    }

    HorizontalPager(state = pagerState) { page ->
        when (page) {
            0 -> MediaGrid(
                mediaFiles = notesFiles,
                onItemClick = { viewModel.toggleSelection(it) },
                isGridView = isGridView
            )

            1 -> MediaGrid(
                mediaFiles = otherFiles,
                onItemClick = { viewModel.toggleSelection(it) },
                isGridView = isGridView
            )
        }
    }
}

@Composable
private fun ErrorMessageSnackbar(
    message: String,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(message) {
        // Auto dismiss after 5 seconds
        kotlinx.coroutines.delay(5000)
        onDismiss()
    }

    // This would ideally be implemented using SnackbarHost, but for simplicity
    // we'll show it as a surface at the bottom
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.small
        ) {
            Row(
                modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )

                TextButton(onClick = onDismiss) {
                    Text("Dismiss", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun MediaGrid(
    mediaFiles: List<MediaFile>,
    onItemClick: (MediaFile) -> Unit,
    isGridView: Boolean = true,
) {
    // Group files by type (images first, then PDFs)
    val imageFiles = mediaFiles.filter { it.type == FileType.IMAGE }
    val pdfFiles = mediaFiles.filter { it.type == FileType.PDF }

    if (mediaFiles.isNotEmpty()) {
        if (isGridView) {
            // Use LazyVerticalGrid for grid layout with sections
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Images Section
                if (imageFiles.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionTitle(title = "Images (${imageFiles.size})")
                    }

                    items(items = imageFiles) { mediaFile ->
                        MediaGridItem(mediaFile = mediaFile, onClick = { onItemClick(mediaFile) })
                    }
                }

                // PDF Section
                if (pdfFiles.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionTitle(title = "PDF Documents (${pdfFiles.size})")
                    }

                    items(items = pdfFiles) { mediaFile ->
                        MediaGridItem(mediaFile = mediaFile, onClick = { onItemClick(mediaFile) })
                    }
                }
            }
        } else {
            // Use LazyColumn for list layout with sections
            LazyColumn(
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Images Section
                if (imageFiles.isNotEmpty()) {
                    item { SectionTitle(title = "Images (${imageFiles.size})") }

                    items(items = imageFiles) { mediaFile ->
                        MediaListItem(mediaFile = mediaFile, onClick = { onItemClick(mediaFile) })
                    }
                }

                // Add spacing between sections
                if (imageFiles.isNotEmpty() && pdfFiles.isNotEmpty()) {
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }

                // PDF Section
                if (pdfFiles.isNotEmpty()) {
                    item { SectionTitle(title = "PDF Documents (${pdfFiles.size})") }

                    items(items = pdfFiles) { mediaFile ->
                        MediaListItem(mediaFile = mediaFile, onClick = { onItemClick(mediaFile) })
                    }
                }
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp), contentAlignment = Alignment.Center
        ) {
            Text(
                "No files found in this category",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun MediaGridItem(
    mediaFile: MediaFile,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val prefsManager = remember { PreferencesManager(context) }
    val showConfidenceScores = remember { prefsManager.showConfidenceScores }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                width = if (mediaFile.isSelected) 3.dp else 1.dp,
                color = if (mediaFile.isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = MaterialTheme.shapes.medium
            )
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(if (mediaFile.type == FileType.PDF && mediaFile.thumbnailUri != null) mediaFile.thumbnailUri else mediaFile.file.toUri())
                .crossfade(true).build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            error = if (mediaFile.type == FileType.PDF) {
                // Fallback icon for PDFs without thumbnails
                painterResource(id = android.R.drawable.ic_menu_report_image)
            } else null
        )

        // File type badge (PDF)
        if (mediaFile.type == FileType.PDF) {
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopStart)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "PDF",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // File info at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                .align(Alignment.BottomCenter)
                .padding(4.dp)
        ) {
            Column {
                Text(
                    text = mediaFile.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = mediaFile.formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )

                    if (showConfidenceScores) {
                        Text(
                            text = mediaFile.classification.confidencePercentage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (mediaFile.classification.labelType == ClassificationLabel.NOTES) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Selection overlay
        if (mediaFile.isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            )

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.small
                    )
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
fun MediaListItem(
    mediaFile: MediaFile,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val prefsManager = remember { PreferencesManager(context) }
    val showConfidenceScores = remember { prefsManager.showConfidenceScores }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (mediaFile.isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .border(
                width = if (mediaFile.isSelected) 2.dp else 1.dp,
                color = if (mediaFile.isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = MaterialTheme.shapes.medium
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(MaterialTheme.shapes.small)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                .data(
                    if (mediaFile.type == FileType.PDF && mediaFile.thumbnailUri != null) mediaFile.thumbnailUri
                    else mediaFile.file.toUri()
                ).crossfade(true).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                error = if (mediaFile.type == FileType.PDF) {
                    painterResource(id = android.R.drawable.ic_menu_report_image)
                } else null
            )

            // Selection indicator
            if (mediaFile.isSelected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.small
                        )
                        .align(Alignment.TopEnd)
                ) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.Center)
                    )
                }
            }
        }

        // File info
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = mediaFile.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = mediaFile.formattedSize,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (mediaFile.type == FileType.PDF) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "PDF",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // Confidence score
        if (showConfidenceScores) {
            Text(
                text = mediaFile.classification.confidencePercentage,
                style = MaterialTheme.typography.bodyMedium,
                color = if (mediaFile.classification.labelType == ClassificationLabel.NOTES) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    count: Int,
    deleteState: DeleteState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Auto-dismiss dialog when deletion is completed successfully
    LaunchedEffect(deleteState) {
        if (deleteState is DeleteState.Completed) {
            kotlinx.coroutines.delay(1500) // Show completion message briefly
            onDismiss()
        }
    }

    AlertDialog(onDismissRequest = {
        // Only allow dismissing if not currently deleting
        if (deleteState !is DeleteState.Deleting) {
            onDismiss()
        }
    }, title = {
        Text(
            when (deleteState) {
                is DeleteState.Idle -> "Delete Files"
                is DeleteState.Deleting -> "Deleting Files..."
                is DeleteState.Completed -> "Deletion Complete"
                is DeleteState.Error -> "Deletion Error"
            }, style = MaterialTheme.typography.headlineSmall
        )
    }, text = {
        Column {
            when (deleteState) {
                is DeleteState.Idle -> {
                    Text(
                        "Are you sure you want to delete $count selected files? This action cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                is DeleteState.Deleting -> {
                    val progress = deleteState.progress
                    Text(
                        "Deleting ${progress.filesDeleted} of ${progress.totalFilesToDelete} files...",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = {
                            progress.filesDeleted.toFloat() / progress.totalFilesToDelete.toFloat()
                        }, modifier = Modifier.fillMaxWidth()
                    )

                    if (progress.currentFileName.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Deleting: ${progress.currentFileName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (progress.errors.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${progress.errors.size} errors occurred",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                is DeleteState.Completed -> {
                    val successCount = deleteState.successCount
                    val totalCount = deleteState.totalCount
                    val errorCount = deleteState.errors.size

                    if (errorCount == 0) {
                        Text(
                            "Successfully deleted all $successCount files.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            "Deleted $successCount of $totalCount files.\n$errorCount files could not be deleted.",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        if (deleteState.errors.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))

                            if (deleteState.errors.size > 5) {
                                // Show scrollable error list for many errors
                                val errorScrollState = rememberScrollState()
                                Box(
                                    modifier = Modifier
                                        .height(120.dp)
                                        .fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Errors:\n${deleteState.errors.joinToString("\n")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(errorScrollState)
                                            .padding(end = 12.dp)
                                    )
                                }
                            } else {
                                // Show simple error list for few errors
                                Text(
                                    text = "Errors:\n${deleteState.errors.joinToString("\n")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                is DeleteState.Error -> {
                    Text(
                        "An error occurred during deletion: ${deleteState.message}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }, confirmButton = {
        when (deleteState) {
            is DeleteState.Idle -> {
                Button(onClick = onConfirm) {
                    Text("Delete", style = MaterialTheme.typography.labelLarge)
                }
            }

            is DeleteState.Deleting -> {
                // No confirm button while deleting
            }

            is DeleteState.Completed,
            is DeleteState.Error,
                -> {
                Button(onClick = onDismiss) {
                    Text(
                        "OK", style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }, dismissButton = {
        when (deleteState) {
            is DeleteState.Idle -> {
                Button(onClick = onDismiss) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
                }
            }

            is DeleteState.Deleting -> {
                // No dismiss button while deleting to prevent accidental cancellation
            }

            is DeleteState.Completed,
            is DeleteState.Error,
                -> {
                // No dismiss button, only OK button
            }
        }
    })
}