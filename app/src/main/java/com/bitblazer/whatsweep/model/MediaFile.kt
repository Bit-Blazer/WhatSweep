package com.bitblazer.whatsweep.model

import android.net.Uri
import java.io.File

data class MediaFile(
    val uri: Uri,
    val file: File,
    val name: String,
    val path: String,
    val size: Long,
    val isImage: Boolean,
    val isPdf: Boolean = false,
    val pdfPage: Int = -1,  // -1 means not a PDF page
    var classification: Classification? = null,
    var isSelected: Boolean = false,
    val confidenceThreshold: Float = 0.7f,
    val thumbnailUri: Uri? = null  // For PDF thumbnails
) {    // Unique identifier for caching and preventing duplicates
    val key: String
        get() = path // Now we always use just the path, since PDFs are treated as whole documents

    val formattedSize: String
        get() = when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${size / (1024 * 1024)} MB"
        }

    val isNotes: Boolean
        get() = classification?.label == "notes" && (classification?.confidence
            ?: 0f) > confidenceThreshold

    val confidencePercentage: String
        get() = classification?.let { "${(it.confidence * 100).toInt()}%" } ?: "N/A"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediaFile) return false
        return key == other.key
    }

    override fun hashCode(): Int {
        return key.hashCode()
    }
}

data class Classification(
    val label: String, val confidence: Float
)