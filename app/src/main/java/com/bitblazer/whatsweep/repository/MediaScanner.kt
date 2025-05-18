package com.bitblazer.whatsweep.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.graphics.createBitmap
import com.bitblazer.whatsweep.ml.ClassifierService
import com.bitblazer.whatsweep.model.Classification
import com.bitblazer.whatsweep.model.MediaFile
import com.bitblazer.whatsweep.util.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.IOException

class MediaScanner(private val context: Context) {

    private val classifierService = ClassifierService(context)
    private val preferencesManager =
        PreferencesManager(context)    // Cache to track processed files within a single scanning session
    private val processedFilesInSession = mutableSetOf<String>()

    companion object {
        private const val TAG = "MediaScanner"
        private const val WHATSAPP_IMAGES_DIR = "WhatsApp Images"
        private const val WHATSAPP_DOCUMENTS_DIR = "WhatsApp Documents"
        private const val MAX_PDF_SAMPLE_PAGES = 5 // Maximum number of pages to sample from a PDF
    }

    /**
     * Main entry point to scan WhatsApp media folders
     */
    fun scanWhatsAppFolder(): Flow<MediaFile> = flow {
        Log.d(TAG, "Starting WhatsApp folder scan")

        // Clear the in-session cache at the start of each scan
        processedFilesInSession.clear()

        // Load cached file paths
        val cachedNotes = preferencesManager.getClassifiedNotesFiles()
        val cachedOthers = preferencesManager.getClassifiedOtherFiles()

        // Find all possible WhatsApp media directories based on Android version
        val whatsAppMediaDirs = getWhatsAppMediaDirectories()

        if (whatsAppMediaDirs.isEmpty()) {
            Log.w(TAG, "No WhatsApp media directories found")
            return@flow
        }

        var foundFiles = false
        val newNotesFiles = mutableSetOf<String>()
        val newOtherFiles = mutableSetOf<String>()

        // Try each possible directory until we find media files
        for (mediaDir in whatsAppMediaDirs) {
            Log.d(TAG, "Checking WhatsApp media directory: ${mediaDir.absolutePath}")

            // Scan Images subdirectory and all its subdirectories
            val imagesDir = File(mediaDir, WHATSAPP_IMAGES_DIR)
            if (imagesDir.exists() && imagesDir.isDirectory) {
                Log.d(TAG, "Scanning images in: ${imagesDir.absolutePath}")

                // Scan for new images
                scanDirectoryRecursively(
                    imagesDir, true, cachedNotes, cachedOthers
                ).collect { mediaFile ->
                    foundFiles = true

                    // Track the classification for saving to cache later
                    if (mediaFile.isNotes) {
                        newNotesFiles.add(mediaFile.key)
                    } else {
                        newOtherFiles.add(mediaFile.key)
                    }

                    emit(mediaFile)
                }

                // Emit cached images that still exist
                emitCachedFiles(imagesDir, true, cachedNotes, cachedOthers).collect { mediaFile ->
                    foundFiles = true
                    emit(mediaFile)
                }
            }

            // Scan Documents subdirectory and all its subdirectories (if PDF scanning is enabled)
            if (preferencesManager.includePdfScanning) {
                val documentsDir = File(mediaDir, WHATSAPP_DOCUMENTS_DIR)
                if (documentsDir.exists() && documentsDir.isDirectory) {
                    Log.d(TAG, "Scanning documents in: ${documentsDir.absolutePath}")

                    // Only scan PDFs
                    scanDirectoryRecursively(
                        documentsDir, false, cachedNotes, cachedOthers
                    ).collect { mediaFile ->
                        foundFiles = true

                        // Track the classification for saving to cache later
                        if (mediaFile.isNotes) {
                            newNotesFiles.add(mediaFile.key)
                        } else {
                            newOtherFiles.add(mediaFile.key)
                        }

                        emit(mediaFile)
                    }

                    // Emit cached PDFs that still exist
                    emitCachedFiles(
                        documentsDir, false, cachedNotes, cachedOthers
                    ).collect { mediaFile ->
                        foundFiles = true
                        emit(mediaFile)
                    }
                }
            }

            if (foundFiles) {
                Log.d(TAG, "Successfully found and processed files in WhatsApp directory")
            }
        }

        if (!foundFiles) {
            Log.w(TAG, "No WhatsApp media files found in any location")
        }

        // Update the cache with new classifications
        val updatedNotes = cachedNotes.toMutableSet().apply { addAll(newNotesFiles) }
        val updatedOthers = cachedOthers.toMutableSet().apply { addAll(newOtherFiles) }
        preferencesManager.saveClassifiedNotesFiles(updatedNotes)
        preferencesManager.saveClassifiedOtherFiles(updatedOthers)

    }.flowOn(Dispatchers.IO)

    /**
     * Gets all possible WhatsApp Media directory locations based on Android version
     */
    private fun getWhatsAppMediaDirectories(): List<File> {
        val dirs = mutableListOf<File>()

        // Get the external storage directory
        val externalStorageDir = Environment.getExternalStorageDirectory()

        when {
            // Android 10+ (API 29+) - Scoped Storage
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // Android/media/com.whatsapp/WhatsApp/Media
                dirs.add(File(externalStorageDir, "Android/media/com.whatsapp/WhatsApp/Media"))
            }

            // Android Nougat to Pie (API 24-28) - Legacy External Storage
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> {
                // Primary: WhatsApp/Media
                dirs.add(File(externalStorageDir, "WhatsApp/Media"))
                // Secondary: Android/media/com.whatsapp/WhatsApp/Media (for some devices)
                dirs.add(File(externalStorageDir, "Android/media/com.whatsapp/WhatsApp/Media"))
            }

            // Android Marshmallow and below (API 23 and lower)
            else -> {
                // WhatsApp/Media
                dirs.add(File(externalStorageDir, "WhatsApp/Media"))
            }
        }

        // Filter out directories that don't exist
        return dirs.filter { it.exists() && it.isDirectory }
    }

    /**
     * Recursively scans a directory and all its subdirectories for new media files
     * Skips files that are already in the cache
     */
    private fun scanDirectoryRecursively(
        directory: File, isImagesDir: Boolean, cachedNotes: Set<String>, cachedOthers: Set<String>
    ): Flow<MediaFile> = flow {
        val files = directory.listFiles() ?: emptyArray()

        for (file in files) {
            if (file.isDirectory) {
                // Recursively scan subdirectories
                scanDirectoryRecursively(
                    file, isImagesDir, cachedNotes, cachedOthers
                ).collect { mediaFile ->
                    emit(mediaFile)
                }
            } else {
                // Skip files we've already processed in this session
                // This prevents duplicate entries from the same file
                // Now we use just the file path for both images and PDFs since we're treating PDFs as whole documents
                val filePath = file.absolutePath
                if (processedFilesInSession.contains(filePath)) {
                    continue
                }

                // Skip files we've already classified in previous sessions
                if (cachedNotes.contains(filePath) || cachedOthers.contains(filePath)) {
                    continue
                }

                // Process files based on directory type
                if (isImagesDir) {
                    // In images directory, we only care about image files
                    if (isImageFile(file)) {
                        processImageFile(file)?.let { mediaFile ->
                            processedFilesInSession.add(mediaFile.key)
                            emit(mediaFile)
                        }
                    }
                } else {
                    // In documents directory, only process PDF files
                    if (file.extension.equals("pdf", ignoreCase = true)) {
                        processPdfFile(file).collect { mediaFile ->
                            processedFilesInSession.add(mediaFile.key)
                            emit(mediaFile)
                        }
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Retrieves cached files from a directory without running classification again
     */
    private fun emitCachedFiles(
        directory: File, isImagesDir: Boolean, cachedNotes: Set<String>, cachedOthers: Set<String>
    ): Flow<MediaFile> = flow {
        val files = directory.listFiles() ?: emptyArray()

        for (file in files) {
            if (file.isDirectory) {
                // Recursively process subdirectories
                emitCachedFiles(file, isImagesDir, cachedNotes, cachedOthers).collect { mediaFile ->
                    emit(mediaFile)
                }
            } else {
                // Check if this file is in our cache
                if (isImagesDir && isImageFile(file)) {
                    val filePath = file.absolutePath

                    // Skip files already processed in this session
                    if (processedFilesInSession.contains(filePath)) {
                        continue
                    }

                    if (cachedNotes.contains(filePath)) {
                        val mediaFile = createMediaFile(file, isImage = true).apply {
                            classification = Classification("notes", 1.0f)
                            processedFilesInSession.add(key)
                        }
                        emit(mediaFile)
                    } else if (cachedOthers.contains(filePath)) {
                        val mediaFile = createMediaFile(file, isImage = true).apply {
                            classification = Classification("not_notes", 1.0f)
                            processedFilesInSession.add(key)
                        }
                        emit(mediaFile)
                    }
                } else if (!isImagesDir && file.extension.equals("pdf", ignoreCase = true)) {
                    // For PDFs, check if the whole document is in the cache (now using just the file path)
                    val filePath = file.absolutePath

                    // Skip files already processed in this session
                    if (processedFilesInSession.contains(filePath)) {
                        continue
                    }
                    if (cachedNotes.contains(filePath) || cachedOthers.contains(filePath)) {
                        // Try to generate a thumbnail for the PDF
                        var thumbnailUri: Uri? = null
                        try {
                            val fileDescriptor =
                                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                            try {
                                PdfRenderer(fileDescriptor).use { renderer ->
                                    if (renderer.pageCount > 0) {
                                        renderer.openPage(0).use { page ->
                                            val bitmap = createBitmap(page.width, page.height)
                                            page.render(
                                                bitmap,
                                                null,
                                                null,
                                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                                            )
                                            thumbnailUri = savePdfThumbnail(file.name, bitmap)
                                        }
                                    }
                                }
                            } finally {
                                fileDescriptor.close()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error creating thumbnail for cached PDF ${file.name}", e)
                        }

                        // Create appropriate classification
                        val classification = if (cachedNotes.contains(filePath)) {
                            Classification("notes", 1.0f)
                        } else {
                            Classification("not_notes", 1.0f)
                        }

                        // Create a media file for the whole PDF document
                        val mediaFile = createMediaFile(
                            file,
                            isImage = false,
                            isPdf = true,
                            pdfPage = -1, // -1 means whole document
                            thumbnailUri = thumbnailUri
                        ).apply {
                            this.classification = classification
                            processedFilesInSession.add(this.key)
                        }
                        emit(mediaFile)
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Check if a file is an image based on extension
     */
    private fun isImageFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension == "jpg" || extension == "jpeg" || extension == "png"
    }

    /**
     * Process an image file and classify it
     */
    private suspend fun processImageFile(file: File): MediaFile? {
        try {
            // Create media file metadata
            val mediaFile = createMediaFile(file, isImage = true)

            // Load and classify the bitmap
            loadBitmap(file)?.let { bitmap ->
                val classification = classifierService.classifyImage(bitmap)
                mediaFile.classification = classification
            }

            return mediaFile
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image file: ${file.name}", e)
            return null
        }
    }

    /**
     * Load a bitmap from a file
     */
    private fun loadBitmap(file: File): Bitmap? {
        return try {
            val uri = Uri.fromFile(file)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap from: ${file.name}", e)
            null
        }
    }

    /**
     * Process a PDF file and classify it as a whole document
     * Samples up to MAX_PDF_SAMPLE_PAGES random pages and makes a classification decision
     * based on the majority of sampled pages
     *
     * Handles password-protected PDFs by catching security exceptions
     * Note: Password protected PDFs will be skipped to avoid app crashes.
     * When Android's PdfRenderer tries to open a password-protected PDF,
     * it throws a SecurityException which we catch and log.
     */
    private fun processPdfFile(pdfFile: File): Flow<MediaFile> = flow {
        try {
            Log.d(TAG, "Processing PDF file: ${pdfFile.name}")
            val fileDescriptor =
                ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)

            try {
                PdfRenderer(fileDescriptor).use { renderer ->
                    val pageCount = renderer.pageCount

                    // Skip empty PDFs
                    if (pageCount == 0) {
                        Log.d(TAG, "Skipping empty PDF: ${pdfFile.name}")
                        return@flow
                    }

                    // Determine how many pages to sample (up to MAX_PDF_SAMPLE_PAGES)
                    val pagesToSample = minOf(MAX_PDF_SAMPLE_PAGES, pageCount)

                    // Generate sample page indices - if few pages, just use them all in order
                    val samplePageIndices = if (pageCount <= MAX_PDF_SAMPLE_PAGES) {
                        (0 until pageCount).toList()
                    } else {
                        // Sample pages at regular intervals to get representative coverage
                        (0 until pageCount).filter { it % (pageCount / pagesToSample) == 0 }
                            .take(pagesToSample)
                    }

                    Log.d(TAG, "Sampling ${samplePageIndices.size} pages from ${pdfFile.name}")

                    // Track classifications for voting
                    var notesCount = 0
                    var otherCount = 0
                    var highestConfidence = 0f
                    var finalClassification: Classification? = null

                    // Process each sampled page
                    for (pageIndex in samplePageIndices) {
                        renderer.openPage(pageIndex).use { page ->
                            // Create bitmap of the PDF page
                            val bitmap = createBitmap(page.width, page.height)
                            page.render(
                                bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                            )

                            // Classify the bitmap
                            val classification = classifierService.classifyImage(bitmap)

                            // Count votes
                            if (classification.label == "notes" && classification.confidence > preferencesManager.confidenceThreshold) {
                                notesCount++

                                // Track highest confidence classification
                                if (classification.confidence > highestConfidence) {
                                    highestConfidence = classification.confidence
                                    finalClassification = classification
                                }
                            } else {
                                otherCount++

                                // If no "notes" classification has been found yet, store this one
                                if (finalClassification == null || classification.confidence > highestConfidence) {
                                    highestConfidence = classification.confidence
                                    finalClassification = classification
                                }
                            }
                        }
                    }                    // Create a thumbnail from the first page of the PDF
                    var thumbnailBitmap: Bitmap? = null
                    try {
                        renderer.openPage(0).use { page ->
                            thumbnailBitmap = createBitmap(page.width, page.height)
                            page.render(
                                thumbnailBitmap!!,
                                null,
                                null,
                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error creating thumbnail for PDF ${pdfFile.name}", e)
                    }

                    // Save thumbnail to a file
                    val thumbnailUri = if (thumbnailBitmap != null) {
                        savePdfThumbnail(pdfFile.name, thumbnailBitmap!!)
                    } else {
                        null
                    }

                    // Create a single MediaFile for the entire PDF with the final classification
                    // Use the majority vote to determine if it's notes or not
                    val isNotesMajority = notesCount > otherCount

                    // If we have a tied vote, use the highest confidence classification
                    val finalLabel = if (isNotesMajority) "notes" else "not_notes"
                    val mediaFile = createMediaFile(
                        file = pdfFile,
                        isImage = false,
                        isPdf = true,
                        pdfPage = -1, // No specific page, represents the whole document
                        thumbnailUri = thumbnailUri
                    )

                    mediaFile.classification =
                        finalClassification ?: Classification(finalLabel, 0.5f)

                    Log.d(
                        TAG,
                        "PDF ${pdfFile.name} classified as $finalLabel with confidence ${mediaFile.confidencePercentage}"
                    )
                    emit(mediaFile)
                }
            } catch (e: SecurityException) {
                // Handle password-protected PDFs
                Log.w(TAG, "Skipping password-protected PDF: ${pdfFile.name}")
            } catch (e: Exception) {
                // Handle other errors during PDF rendering
                Log.e(TAG, "Error rendering PDF: ${pdfFile.name}", e)
            } finally {
                try {
                    fileDescriptor.close()
                } catch (e: Exception) {
                    // Ignore close errors
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error opening PDF file: ${pdfFile.name}", e)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Save a PDF thumbnail to the app's cache directory
     * @param pdfName name of the source PDF file
     * @param bitmap thumbnail bitmap to save
     * @return Uri to the saved thumbnail, or null if saving failed
     */
    private fun savePdfThumbnail(pdfName: String, bitmap: Bitmap): Uri? {
        return try {
            // Create a unique filename for this PDF's thumbnail
            val filename = "pdf_thumb_${pdfName.replace(".", "_")}.jpg"
            val file = File(context.cacheDir, filename)

            // Save the bitmap to the file as JPEG with 80% quality
            file.outputStream().use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                outputStream.flush()
            }

            // Return the URI to the saved thumbnail
            Uri.fromFile(file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save PDF thumbnail for $pdfName", e)
            null
        }
    }

    /**
     * Create a MediaFile object from a file
     */
    private fun createMediaFile(
        file: File,
        isImage: Boolean,
        isPdf: Boolean = false,
        pdfPage: Int = -1,
        thumbnailUri: Uri? = null
    ): MediaFile {
        return MediaFile(
            uri = Uri.fromFile(file),
            file = file,
            name = file.name,
            path = file.absolutePath,
            size = file.length(),
            isImage = isImage,
            isPdf = isPdf,
            pdfPage = pdfPage,
            confidenceThreshold = preferencesManager.confidenceThreshold,
            thumbnailUri = thumbnailUri
        )
    }

    /**
     * Clean up resources
     */
    fun onDestroy() {
        classifierService.close()
    }
}