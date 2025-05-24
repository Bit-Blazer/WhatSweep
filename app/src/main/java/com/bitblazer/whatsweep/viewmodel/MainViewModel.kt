package com.bitblazer.whatsweep.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bitblazer.whatsweep.model.MediaFile
import com.bitblazer.whatsweep.repository.MediaScanner
import com.bitblazer.whatsweep.util.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

// Data class to track scanning progress
data class ScanProgress(
    val filesProcessed: Int = 0, val totalFilesFound: Int = -1  // -1 means total not yet known
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context
        get() = getApplication<Application>()

    private val mediaScanner = MediaScanner(context)
    private val preferencesManager = PreferencesManager(context)

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Add scan progress tracking
    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    // Use Sets to avoid duplicates
    private val _notesFiles = mutableStateListOf<MediaFile>()
    val notesFiles: List<MediaFile> = _notesFiles

    private val _otherFiles = mutableStateListOf<MediaFile>()
    val otherFiles: List<MediaFile> = _otherFiles

    // Track file keys to avoid duplicates
    private val processedFileKeys = mutableSetOf<String>()

    private val _selectedFiles = mutableStateListOf<MediaFile>()
    val selectedFiles: List<MediaFile> = _selectedFiles

    init {
        // Sort the lists when initialized
        sortMediaLists()
    }

    fun scanWhatsAppFolder() {
        _isScanning.value = true
        _notesFiles.clear()
        _otherFiles.clear()
        processedFileKeys.clear()
        _scanProgress.value = ScanProgress()  // Reset progress counter

        viewModelScope.launch {
            try {
                mediaScanner.scanWhatsAppFolder().collect { mediaFile ->
                    processMediaFile(mediaFile)
                }
                // Sort lists after scan is complete
                sortMediaLists()
            } finally {
                _isScanning.value = false
            }
        }
    }

    private fun processMediaFile(mediaFile: MediaFile) {
        // Skip if we've already processed this file key
        if (processedFileKeys.contains(mediaFile.key)) {
            return
        }

        processedFileKeys.add(mediaFile.key)

        if (mediaFile.isNotes) {
            _notesFiles.add(mediaFile)
        } else {
            _otherFiles.add(mediaFile)
        }

        // Update progress counter
        val current = _scanProgress.value
        _scanProgress.value = current.copy(filesProcessed = current.filesProcessed + 1)
    }

    /**
     * Sort media lists so images appear first, then PDFs
     */
    private fun sortMediaLists() {
        val sortedNotes =
            _notesFiles.sortedWith(compareBy<MediaFile> { !it.isImage }  // Images first
                .thenBy { it.name }               // Then by name
            )
        val sortedOthers =
            _otherFiles.sortedWith(compareBy<MediaFile> { !it.isImage }  // Images first
                .thenBy { it.name }               // Then by name
            )

        _notesFiles.clear()
        _notesFiles.addAll(sortedNotes)

        _otherFiles.clear()
        _otherFiles.addAll(sortedOthers)
    }

    /**
     * Toggle selection state of a media file
     */
    fun toggleSelection(mediaFile: MediaFile) {
        val existingSelected = _selectedFiles.find { it.key == mediaFile.key }
        val isCurrentlySelected = existingSelected != null
        toggleSelection(mediaFile, !isCurrentlySelected)
    }

    /**
     * Set selection state of a media file to a specific value
     */
    fun toggleSelection(mediaFile: MediaFile, selected: Boolean) {
        val list = if (mediaFile.isNotes) _notesFiles else _otherFiles
        val index = list.indexOf(mediaFile)

        if (index != -1) {
            // Check if this file is already selected by key to avoid duplicates
            val existingSelected = _selectedFiles.find { it.key == mediaFile.key }
            val isCurrentlySelected = existingSelected != null

            // Only update if the selection state is different
            if (isCurrentlySelected != selected) {
                // Create updated file with new selection state
                val updatedFile = mediaFile.copy(isSelected = selected)
                list[index] = updatedFile

                // Update the selected files list
                if (updatedFile.isSelected) {
                    if (existingSelected == null) {  // Don't add if already in the list
                        _selectedFiles.add(updatedFile)
                    }
                } else {
                    if (existingSelected != null) {
                        _selectedFiles.remove(existingSelected)
                    }
                }
            }
        }
    }

    fun deleteSelectedFiles(): Int {
        var count = 0
        val filesToDelete = _selectedFiles.toList()

        filesToDelete.forEach { mediaFile ->
            if (mediaFile.file.exists() && mediaFile.file.delete()) {
                count++

                // Delete thumbnail file if it exists (for PDFs)
                if (mediaFile.isPdf && mediaFile.thumbnailUri != null) {
                    try {
                        val thumbnailFile = File(mediaFile.thumbnailUri.path!!)
                        if (thumbnailFile.exists()) {
                            thumbnailFile.delete()
                        }
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Error deleting thumbnail: ${e.message}")
                    }
                }

                // Also remove from cache if using PreferencesManager
                if (mediaFile.isNotes) {
                    val cachedNotes = preferencesManager.getClassifiedNotesFiles().toMutableMap()
                    cachedNotes.remove(mediaFile.path)
                    preferencesManager.saveClassifiedNotesFiles(cachedNotes)
                } else {
                    val cachedOthers = preferencesManager.getClassifiedOtherFiles().toMutableMap()
                    cachedOthers.remove(mediaFile.path)
                    preferencesManager.saveClassifiedOtherFiles(cachedOthers)
                }

                // Remove from our lists
                removeFileFromLists(mediaFile)
            }
        }

        _selectedFiles.clear()
        return count
    }


    private fun removeFileFromLists(mediaFile: MediaFile) {
        if (mediaFile.isNotes) {
            _notesFiles.remove(mediaFile)
        } else {
            _otherFiles.remove(mediaFile)
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaScanner.onDestroy()
    }
}