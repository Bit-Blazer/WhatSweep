package com.bitblazer.whatsweep.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.bitblazer.whatsweep.model.MediaFile
import com.bitblazer.whatsweep.util.PreferencesManager
import com.bitblazer.whatsweep.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ResultsScreen(
    viewModel: MainViewModel, onNavigateUp: () -> Unit, modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefsManager = remember { PreferencesManager(context) }
    val showConfidenceScores = remember { prefsManager.showConfidenceScores }

    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val notesFiles = viewModel.notesFiles
    val otherFiles = viewModel.otherFiles
    val selectedFiles = viewModel.selectedFiles

    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    var showDeleteDialog by remember { mutableStateOf(false) }

    val tabs = listOf(
        "Notes (${notesFiles.size})", "Others (${otherFiles.size})"
    )

    if (showDeleteDialog) {
        DeleteConfirmationDialog(count = selectedFiles.size, onConfirm = {
            val deleted = viewModel.deleteSelectedFiles()
            showDeleteDialog = false
        }, onDismiss = { showDeleteDialog = false })
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Scan Results") }, navigationIcon = {
            IconButton(onClick = onNavigateUp) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }, actions = {
            if (!isScanning && selectedFiles.isNotEmpty()) {
                IconButton(
                    onClick = { }) {
                    Icon(
                        Icons.Default.Share, contentDescription = "Share"
                    )
                }
            }
        })
    }, floatingActionButton = {
        if (!isScanning && selectedFiles.isNotEmpty()) {
            FloatingActionButton(
                onClick = { showDeleteDialog = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isScanning) {
                Box(
                    modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Scanning and classifying files...")
                        Spacer(modifier = Modifier.height(16.dp))
                        androidx.compose.material3.LinearProgressIndicator()
                    }
                }
            } else if (notesFiles.isEmpty() && otherFiles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "No files found", style = MaterialTheme.typography.headlineSmall
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Make sure WhatsApp is installed and has media in its folder.\n\n" + "You can also try the 'Select Custom Folder' option to scan a specific directory.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onNavigateUp
                        ) {
                            Text("Go Back")
                        }
                    }
                }
            } else {
                TabRow(
                    selectedTabIndex = pagerState.currentPage
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(selected = pagerState.currentPage == index, onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        }, text = { Text(title) })
                    }
                }

                HorizontalPager(
                    state = pagerState, modifier = Modifier.weight(1f)
                ) { page ->
                    when (page) {
                        0 -> MediaGrid(
                            mediaFiles = notesFiles,
                            onItemClick = { viewModel.toggleSelection(it) },
                            onSelectAll = { viewModel.selectAllNotes(it) },
                            showConfidenceScores = showConfidenceScores
                        )

                        1 -> MediaGrid(
                            mediaFiles = otherFiles,
                            onItemClick = { viewModel.toggleSelection(it) },
                            onSelectAll = { viewModel.selectAllOthers(it) },
                            showConfidenceScores = showConfidenceScores
                        )
                    }
                }

                if (selectedFiles.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "${selectedFiles.size} files selected",
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MediaGrid(
    mediaFiles: List<MediaFile>,
    onItemClick: (MediaFile) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    showConfidenceScores: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Group files by type (images first, then PDFs)
    val imageFiles = mediaFiles.filter { it.isImage }
    val pdfFiles = mediaFiles.filter { it.isPdf }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        if (mediaFiles.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var selectAllChecked by remember { mutableStateOf(false) }

                Checkbox(
                    checked = selectAllChecked, onCheckedChange = { checked ->
                        selectAllChecked = checked
                        onSelectAll(checked)
                    })

                Text("Select All")

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "${mediaFiles.size} files", style = MaterialTheme.typography.bodyMedium
                )
            }

            // Images Section
            if (imageFiles.isNotEmpty()) {
                SectionTitle(title = "Images (${imageFiles.size})")

                MediaGrid(
                    mediaFiles = imageFiles,
                    onItemClick = onItemClick,
                    showConfidenceScores = showConfidenceScores
                )
            }

            // PDF Section
            if (pdfFiles.isNotEmpty()) {
                SectionTitle(title = "PDF Pages (${pdfFiles.size})")

                MediaGrid(
                    mediaFiles = pdfFiles,
                    onItemClick = onItemClick,
                    showConfidenceScores = showConfidenceScores
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No files found in this category")
            }
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
fun MediaGrid(
    mediaFiles: List<MediaFile>,
    onItemClick: (MediaFile) -> Unit,
    showConfidenceScores: Boolean = false
) {
    //calculate dp size as (mediaFiles.size / 3 + 1) * 140
    val width = if (mediaFiles.size > 3) {
        120.dp * 3 + 16.dp * 2
    } else {
        120.dp * mediaFiles.size + 16.dp * (mediaFiles.size - 1)
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.height(width) // Approximate height based on number of items
    ) {
        items(
            // Group media files by their file name to prevent duplicates
            items = mediaFiles.distinctBy { it.key }, key = { it.key }) { mediaFile ->
            MediaItem(
                mediaFile = mediaFile,
                onClick = { onItemClick(mediaFile) },
                showConfidenceScore = showConfidenceScores
            )
        }
    }
}

@Composable
fun MediaItem(
    mediaFile: MediaFile,
    onClick: () -> Unit,
    showConfidenceScore: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
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
                .data(if (mediaFile.isPdf && mediaFile.thumbnailUri != null) mediaFile.thumbnailUri else mediaFile.uri)
                .crossfade(true).build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            error = if (mediaFile.isPdf) {
                // Fallback icon for PDFs without thumbnails
                painterResource(id = android.R.drawable.ic_menu_report_image)
            } else null
        )
        if (mediaFile.isPdf) {
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

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = mediaFile.formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )

                    if (showConfidenceScore && mediaFile.classification != null) {
                        Text(
                            text = mediaFile.confidencePercentage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (mediaFile.isNotes) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        if (mediaFile.isSelected) {
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
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Files") },
        text = { Text("Are you sure you want to delete $count selected files? This action cannot be undone.") },
        confirmButton = {
            Button(
                onClick = onConfirm
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        })
}