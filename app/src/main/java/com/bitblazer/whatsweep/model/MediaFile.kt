package com.bitblazer.whatsweep.model

import android.net.Uri
import java.io.File

/**
 * Represents a media file (image or PDF) with classification metadata.
 *
 * This data class encapsulates all information needed for displaying,
 * managing, and classifying media files within the application.
 *
 * @property uri Content URI for loading the file
 * @property file File reference for file system operations
 * @property name Human-readable filename
 * @property path Absolute file path used as unique identifier
 * @property size File size in bytes
 * @property isImage True if file is an image format
 * @property isPdf True if file is a PDF document
 * @property classification ML classification result
 * @property isSelected UI selection state
 * @property thumbnailUri Optional thumbnail URI for PDFs
 */
data class MediaFile(
    val uri: Uri,
    val file: File,
    val name: String,
    val path: String,
    val size: Long,
    val isImage: Boolean,
    val isPdf: Boolean = false,
    var classification: Classification? = null,
    var isSelected: Boolean = false,
    val thumbnailUri: Uri? = null  // For PDF thumbnails
) {

    /**
     * Unique identifier for caching and deduplication.
     * Uses file path as PDFs are treated as complete documents.
     */
    val key: String
        get() = path

    /**
     * Human-readable file size formatting.
     */
    val formattedSize: String
        get() = formatFileSize(size)

    /**
     * Determines if this file is classified as notes based on ML confidence.
     */
    val isNotes: Boolean
        get() = classification?.let { it.label == "notes" } == true

    /**
     * Confidence score as percentage string for UI display.
     */
    val confidencePercentage: String
        get() = classification?.let { "${(it.confidence * 100).toInt()}%" } ?: "N/A"

    // Use path-based equality for proper deduplication
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediaFile) return false
        return key == other.key
    }

    override fun hashCode(): Int = key.hashCode()

    companion object {
        /**
         * Formats file size in human-readable format.
         */
        private fun formatFileSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                bytes < 1024 * 1024 * 1024 -> "${(bytes / (1024 * 1024))} MB"
                else -> "${(bytes / (1024 * 1024 * 1024))} GB"
            }
        }
    }
}

/**
 * Represents the result of ML classification with confidence score.
 *
 * @property label Classification label (e.g., "notes", "not_notes")
 * @property confidence Confidence score between 0.0 and 1.0
 */
data class Classification(
    val label: String, val confidence: Float
) {
    init {
        require(confidence in 0f..1f) { "Confidence must be between 0.0 and 1.0" }
    }
}

