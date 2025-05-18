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

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context
        get() = getApplication<Application>()

    private val mediaScanner = MediaScanner(context)
    private val preferencesManager = PreferencesManager(context)

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> =
        _isScanning.asStateFlow()    // Use Sets to avoid duplicates
    private val _notesFiles = mutableStateListOf<MediaFile>()
    val notesFiles: List<MediaFile> = _notesFiles

    private val _otherFiles = mutableStateListOf<MediaFile>()
    val otherFiles: List<MediaFile> = _otherFiles

    // Track file keys to avoid duplicates
    private val processedFileKeys = mutableSetOf<String>()

    private val _scanProgress = MutableStateFlow(0)
    val scanProgress: StateFlow<Int> = _scanProgress.asStateFlow()

    private val _scanTotal = MutableStateFlow(0)
    val scanTotal: StateFlow<Int> = _scanTotal.asStateFlow()

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
        _scanProgress.value = 0

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
        _scanProgress.value = _notesFiles.size + _otherFiles.size
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

    fun toggleSelection(mediaFile: MediaFile) {
        val list = if (mediaFile.isNotes) _notesFiles else _otherFiles
        val index = list.indexOf(mediaFile)

        if (index != -1) {
            // Check if this file is already selected by key to avoid duplicates
            val existingSelected = _selectedFiles.find { it.key == mediaFile.key }
            val isCurrentlySelected = existingSelected != null

            // Create updated file with opposite selection state
            val updatedFile = mediaFile.copy(isSelected = !isCurrentlySelected)
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
                    val cachedNotes = preferencesManager.getClassifiedNotesFiles().toMutableSet()
                    cachedNotes.remove(mediaFile.path)
                    preferencesManager.saveClassifiedNotesFiles(cachedNotes)
                } else {
                    val cachedOthers = preferencesManager.getClassifiedOtherFiles().toMutableSet()
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

    fun selectAllNotes(select: Boolean) {
        // First, deselect existing notes in the selected files list if we're deselecting
        if (!select) {
            // Get all notes files that are currently in the selected list
            val notesToRemove = _selectedFiles.filter { selectedFile ->
                _notesFiles.any { it.key == selectedFile.key }
            }
            // Remove them from selected files
            _selectedFiles.removeAll(notesToRemove)
        }

        // Update the selection state of all notes files
        _notesFiles.forEachIndexed { index, mediaFile ->
            val updatedFile = mediaFile.copy(isSelected = select)
            _notesFiles[index] = updatedFile

            // Add to selected files if we're selecting and not already there
            if (select && !_selectedFiles.any { it.key == updatedFile.key }) {
                _selectedFiles.add(updatedFile)
            }
        }
    }

    fun selectAllOthers(select: Boolean) {
        // First, deselect existing other files in the selected files list if we're deselecting
        if (!select) {
            // Get all other files that are currently in the selected list
            val othersToRemove = _selectedFiles.filter { selectedFile ->
                _otherFiles.any { it.key == selectedFile.key }
            }
            // Remove them from selected files
            _selectedFiles.removeAll(othersToRemove)
        }

        // Update the selection state of all other files
        _otherFiles.forEachIndexed { index, mediaFile ->
            val updatedFile = mediaFile.copy(isSelected = select)
            _otherFiles[index] = updatedFile

            // Add to selected files if we're selecting and not already there
            if (select && !_selectedFiles.any { it.key == updatedFile.key }) {
                _selectedFiles.add(updatedFile)
            }
        }
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