package com.bitblazer.whatsweep.model

import android.net.Uri
import java.io.File

/**
 * Represents the result of ML classification with confidence score.
 *
 * @property label Classification label (e.g., "notes", "others")
 * @property confidence Confidence score between 0.0 and 1.0
 */
data class Classification(val label: String, val confidence: Float) {
    val labelType: ClassificationLabel
        get() = ClassificationLabel.from(label)

    val confidencePercentage: String
        get() = "${(confidence * 100).toInt()}%"
}

enum class FileType {
    IMAGE, PDF, UNKNOWN;

    companion object {
        fun from(file: File): FileType = when (file.extension.lowercase()) {
            "jpg",
            "jpeg",
            "png",
                -> IMAGE

            "pdf" -> PDF
            else -> UNKNOWN
        }
    }
}

enum class ClassificationLabel {
    NOTES, OTHERS, UNKNOWN;

    companion object {
        fun from(label: String): ClassificationLabel = when (label.lowercase()) {
            "notes" -> NOTES
            "others" -> OTHERS
            else -> UNKNOWN
        }
    }
}

/**
 * Represents a media file (image or PDF) with classification metadata.
 *
 * This data class encapsulates all information needed for displaying,
 * managing, and classifying media files within the application.
 *
 * @property file File reference for file system operations
 * @property classification ML classification result
 * @property isSelected UI selection state
 * @property thumbnailUri Optional thumbnail URI for PDFs
 */
data class MediaFile(
    val file: File,
    var classification: Classification = Classification("unknown", 0.0f),
    var isSelected: Boolean = false,
    val thumbnailUri: Uri? = null, // For PDF thumbnails
) {
    val name: String
        get() = file.nameWithoutExtension

    val type: FileType
        get() = FileType.from(file)

    /** Human-readable file size formatting. */
    val formattedSize: String
        get() {
            val bytes = file.length()
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
                else -> "${bytes / (1024 * 1024 * 1024)} GB"
            }
        }
}