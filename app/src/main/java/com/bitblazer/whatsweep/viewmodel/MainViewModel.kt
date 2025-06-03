package com.bitblazer.whatsweep.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
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

/**
 * Data class representing the progress of media scanning operation.
 *
 * @property filesProcessed Number of files that have been processed
 * @property totalFilesFound Total number of files found (-1 if not yet determined)
 * @property currentDirectory Name of directory currently being scanned
 */
data class ScanProgress(
    val filesProcessed: Int = 0,
    val totalFilesFound: Int = -1,  // -1 means total not yet known
    val currentDirectory: String = ""
)

/**
 * Data class representing the progress of file deletion operation.
 *
 * @property filesDeleted Number of files that have been successfully deleted
 * @property totalFilesToDelete Total number of files to be deleted
 * @property currentFileName Name of file currently being deleted
 * @property errors List of error messages for files that failed to delete
 */
data class DeleteProgress(
    val filesDeleted: Int = 0,
    val totalFilesToDelete: Int = 0,
    val currentFileName: String = "",
    val errors: List<String> = emptyList()
)

/**
 * UI state representing the scanning operation status.
 */
sealed class ScanState {
    object Idle : ScanState()
    data class Scanning(val progress: ScanProgress) : ScanState()
    data class Completed(val notesCount: Int, val otherCount: Int) : ScanState()
    data class Error(val message: String, val exception: Throwable? = null) : ScanState()
}

/**
 * UI state representing the deletion operation status.
 */
sealed class DeleteState {
    object Idle : DeleteState()
    data class Deleting(val progress: DeleteProgress) : DeleteState()
    data class Completed(val successCount: Int, val totalCount: Int, val errors: List<String>) :
        DeleteState()

    data class Error(val message: String, val exception: Throwable? = null) : DeleteState()
}

/**
 * Main ViewModel handling media scanning, classification, and file management.
 *
 * Responsibilities:
 * - Coordinating media scanning operations
 * - Managing UI state for notes and other files
 * - Handling file selection and deletion
 * - Providing reactive state updates for UI
 *
 * Architecture: Follows MVVM pattern with clear separation of concerns.
 * All business logic is contained here, keeping UI components stateless.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val context: Context
        get() = getApplication<Application>()

    // Dependencies - injected for better testability
    private val mediaScanner = MediaScanner(context)
    private val preferencesManager = PreferencesManager(context)

    // Scan state management
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    // Delete state management
    private val _deleteState = MutableStateFlow<DeleteState>(DeleteState.Idle)
    val deleteState: StateFlow<DeleteState> = _deleteState.asStateFlow()

    // Media files state - using StateFlow for reactive UI updates
    private val _notesFiles = MutableStateFlow<List<MediaFile>>(emptyList())
    val notesFiles: StateFlow<List<MediaFile>> = _notesFiles.asStateFlow()

    private val _otherFiles = MutableStateFlow<List<MediaFile>>(emptyList())
    val otherFiles: StateFlow<List<MediaFile>> = _otherFiles.asStateFlow()

    private val _selectedFiles = MutableStateFlow<List<MediaFile>>(emptyList())
    val selectedFiles: StateFlow<List<MediaFile>> = _selectedFiles.asStateFlow()

    // Error message state for general UI messages (separate from scan state errors)
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Internal tracking for deduplication
    private val processedFileKeys = mutableSetOf<String>()

    init {
        Log.d(TAG, "MainViewModel initialized")
    }

    /**
     * Initiates WhatsApp folder scanning with proper error handling and state management.
     *
     * This operation runs on a background thread and updates UI state reactively.
     * Handles deduplication and sorts results for optimal user experience.
     */
    fun scanWhatsAppFolder() {
        if (_scanState.value is ScanState.Scanning) {
            Log.w(TAG, "Scan already in progress, ignoring request")
            return
        }

        Log.d(TAG, "Starting WhatsApp folder scan")
        _scanState.value = ScanState.Scanning(ScanProgress())

        // Clear previous results
        clearCurrentResults()

        viewModelScope.launch {
            try {
                mediaScanner.scanWhatsAppFolder().collect { mediaFile ->
                    processMediaFile(mediaFile)
                }

                // Sort and finalize results
                sortMediaLists()

                val notesCount = _notesFiles.value.size
                val otherCount = _otherFiles.value.size

                _scanState.value = ScanState.Completed(notesCount, otherCount)
                Log.d(TAG, "Scan completed: $notesCount notes, $otherCount others")

            } catch (exception: Exception) {
                Log.e(TAG, "Scan failed", exception)
                _scanState.value = ScanState.Error(
                    message = "Failed to scan WhatsApp folder: ${exception.localizedMessage}",
                    exception = exception
                )
            }
        }
    }

    /**
     * Clears current scan results and resets state.
     */
    private fun clearCurrentResults() {
        _notesFiles.value = emptyList()
        _otherFiles.value = emptyList()
        _selectedFiles.value = emptyList()
        processedFileKeys.clear()
    }

    /**
     * Processes a newly discovered media file, handling deduplication and categorization.
     */
    private fun processMediaFile(mediaFile: MediaFile) {
        // Skip duplicates based on unique key
        if (processedFileKeys.contains(mediaFile.key)) {
            Log.v(TAG, "Skipping duplicate file: ${mediaFile.key}")
            return
        }

        processedFileKeys.add(mediaFile.key)

        // Update appropriate list based on classification
        if (mediaFile.isNotes) {
            _notesFiles.value = _notesFiles.value + mediaFile
            Log.v(TAG, "Added notes file: ${mediaFile.name}")
        } else {
            _otherFiles.value = _otherFiles.value + mediaFile
            Log.v(TAG, "Added other file: ${mediaFile.name}")
        }

        // Update scan progress if in scanning state
        val currentState = _scanState.value
        if (currentState is ScanState.Scanning) {
            val updatedProgress = currentState.progress.copy(
                filesProcessed = currentState.progress.filesProcessed + 1
            )
            _scanState.value = ScanState.Scanning(updatedProgress)
        }
    }

    /**
     * Sorts media lists for optimal user experience.
     * Images appear first, then PDFs, sorted alphabetically within each type.
     */
    private fun sortMediaLists() {
        val sortComparator = compareBy<MediaFile> { !it.isImage }  // Images first
            .thenBy { it.name.lowercase() }  // Then alphabetically

        _notesFiles.value = _notesFiles.value.sortedWith(sortComparator)
        _otherFiles.value = _otherFiles.value.sortedWith(sortComparator)

        Log.d(TAG, "Media lists sorted")
    }

    /**
     * Toggles selection state of a media file.
     *
     * @param mediaFile The file to toggle selection for
     */
    fun toggleSelection(mediaFile: MediaFile) {
        val currentlySelected = _selectedFiles.value.any { it.key == mediaFile.key }
        setSelectionState(mediaFile, !currentlySelected)
    }

    /**
     * Sets the selection state of a media file to a specific value.
     *
     * @param mediaFile The file to update
     * @param selected Whether the file should be selected
     */
    fun setSelectionState(mediaFile: MediaFile, selected: Boolean) {
        // Update the file in the appropriate list
        updateFileInList(mediaFile) { it.copy(isSelected = selected) }

        // Update selected files list
        if (selected) {
            if (_selectedFiles.value.none { it.key == mediaFile.key }) {
                _selectedFiles.value = _selectedFiles.value + mediaFile.copy(isSelected = true)
            }
        } else {
            _selectedFiles.value = _selectedFiles.value.filterNot { it.key == mediaFile.key }
        }

        Log.v(TAG, "File ${mediaFile.name} selection: $selected")
    }

    /**
     * Updates a file in the appropriate list with a transformation function.
     */
    private fun updateFileInList(mediaFile: MediaFile, transform: (MediaFile) -> MediaFile) {
        if (mediaFile.isNotes) {
            _notesFiles.value = _notesFiles.value.map { file ->
                if (file.key == mediaFile.key) transform(file) else file
            }
        } else {
            _otherFiles.value = _otherFiles.value.map { file ->
                if (file.key == mediaFile.key) transform(file) else file
            }
        }
    }

    /**
     * Deletes all currently selected files from storage and updates the UI with progress.
     * This operation runs on a background thread and updates delete state reactively.
     */
    fun deleteSelectedFiles() {
        if (_deleteState.value is DeleteState.Deleting) {
            Log.w(TAG, "Delete already in progress, ignoring request")
            return
        }

        val filesToDelete = _selectedFiles.value.toList()
        if (filesToDelete.isEmpty()) {
            Log.w(TAG, "No files selected for deletion")
            return
        }

        Log.d(TAG, "Starting deletion of ${filesToDelete.size} selected files")

        viewModelScope.launch {
            try {
                // Initialize delete state
                _deleteState.value = DeleteState.Deleting(
                    DeleteProgress(
                        filesDeleted = 0,
                        totalFilesToDelete = filesToDelete.size,
                        currentFileName = "",
                        errors = emptyList()
                    )
                )

                var successCount = 0
                val errors = mutableListOf<String>()

                filesToDelete.forEachIndexed { index, mediaFile ->
                    try {
                        // Update progress to show current file being deleted
                        val currentState = _deleteState.value as DeleteState.Deleting
                        _deleteState.value = DeleteState.Deleting(
                            currentState.progress.copy(
                                currentFileName = mediaFile.name
                            )
                        )

                        if (deleteMediaFile(mediaFile)) {
                            successCount++
                            removeFileFromLists(mediaFile)
                            Log.d(TAG, "Successfully deleted: ${mediaFile.name}")
                        } else {
                            val errorMsg = "Failed to delete: ${mediaFile.name}"
                            errors.add(errorMsg)
                            Log.w(TAG, errorMsg)
                        }
                    } catch (e: Exception) {
                        val errorMsg = "Error deleting ${mediaFile.name}: ${e.localizedMessage}"
                        errors.add(errorMsg)
                        Log.e(TAG, "Error deleting file: ${mediaFile.name}", e)
                    }

                    // Update progress after each file
                    val updatedState = _deleteState.value as DeleteState.Deleting
                    _deleteState.value = DeleteState.Deleting(
                        updatedState.progress.copy(
                            filesDeleted = successCount, errors = errors.toList()
                        )
                    )

                    // Add a small delay to make progress visible for better UX
                    kotlinx.coroutines.delay(100)
                }

                // Clear selection
                _selectedFiles.value = emptyList()

                // Complete deletion
                _deleteState.value = DeleteState.Completed(
                    successCount = successCount,
                    totalCount = filesToDelete.size,
                    errors = errors.toList()
                )

                Log.d(TAG, "Deletion completed: $successCount/${filesToDelete.size} files deleted")

                // Auto-reset to idle after a short delay
                kotlinx.coroutines.delay(2000)
                _deleteState.value = DeleteState.Idle

            } catch (exception: Exception) {
                Log.e(TAG, "Delete operation failed", exception)
                _deleteState.value = DeleteState.Error(
                    message = "Failed to delete files: ${exception.localizedMessage}",
                    exception = exception
                )

                // Auto-reset to idle after error
                kotlinx.coroutines.delay(3000)
                _deleteState.value = DeleteState.Idle
            }
        }
    }

    /**
     * Resets the delete state to idle (used when dismissing dialogs)
     */
    fun resetDeleteState() {
        _deleteState.value = DeleteState.Idle
    }

    /**
     * Deletes a single media file and its associated thumbnail if applicable.
     */
    private fun deleteMediaFile(mediaFile: MediaFile): Boolean {
        var success = false

        // Delete main file
        if (mediaFile.file.exists()) {
            success = mediaFile.file.delete()

            // Delete thumbnail for PDFs
            if (success && mediaFile.isPdf && mediaFile.thumbnailUri != null) {
                try {
                    val thumbnailFile = File(mediaFile.thumbnailUri.path!!)
                    if (thumbnailFile.exists()) {
                        thumbnailFile.delete()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not delete thumbnail for ${mediaFile.name}", e)
                }
            }
        }

        // Remove from cache
        if (success) {
            removeCachedClassification(mediaFile)
        }

        return success
    }

    /**
     * Removes cached classification data for a deleted file.
     */
    private fun removeCachedClassification(mediaFile: MediaFile) {
        try {
            if (mediaFile.isNotes) {
                val cachedNotes = preferencesManager.getClassifiedNotesFiles().toMutableMap()
                cachedNotes.remove(mediaFile.path)
                preferencesManager.saveClassifiedNotesFiles(cachedNotes)
            } else {
                val cachedOthers = preferencesManager.getClassifiedOtherFiles().toMutableMap()
                cachedOthers.remove(mediaFile.path)
                preferencesManager.saveClassifiedOtherFiles(cachedOthers)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not update classification cache", e)
        }
    }

    /**
     * Removes a file from the appropriate UI list.
     */
    private fun removeFileFromLists(mediaFile: MediaFile) {
        if (mediaFile.isNotes) {
            _notesFiles.value = _notesFiles.value.filterNot { it.key == mediaFile.key }
        } else {
            _otherFiles.value = _otherFiles.value.filterNot { it.key == mediaFile.key }
        }
    }

    /**
     * Clears the current error message.
     */
    fun clearError() {
        _errorMessage.value = null
        Log.d(TAG, "Error message cleared")
    }

    /**
     * Properly releases resources when ViewModel is cleared.
     * Ensures MediaScanner and ML Kit resources are properly cleaned up.
     */
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "MainViewModel clearing resources")

        try {
            mediaScanner.onDestroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error during MediaScanner cleanup", e)
        }
    }
}