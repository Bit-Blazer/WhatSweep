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
import androidx.core.net.toUri
import com.bitblazer.whatsweep.ml.ClassifierService
import com.bitblazer.whatsweep.model.Classification
import com.bitblazer.whatsweep.model.MediaFile
import com.bitblazer.whatsweep.util.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Service responsible for scanning WhatsApp media directories and classifying images.
 *
 * Handles:
 * - Multi-directory scanning across different Android versions
 * - ML-based image classification with incremental caching
 * - PDF thumbnail generation and classification
 * - Memory-efficient processing with cancellation support
 * - Proper resource management for PDF rendering
 *
 * Architecture: Repository pattern with reactive Flow-based API
 */
class MediaScanner(private val context: Context) {

    companion object {
        private const val TAG = "MediaScanner"
        private const val WHATSAPP_IMAGES_DIR = "WhatsApp Images"
        private const val WHATSAPP_DOCUMENTS_DIR = "WhatsApp Documents"
        private const val MAX_PDF_SAMPLE_PAGES = 5 // Maximum pages to sample for classification
        private const val THUMBNAIL_SIZE = 512 // PDF thumbnail dimensions

        // Supported image formats for classification
        private val SUPPORTED_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp")
    }

    // Dependencies with proper lifecycle management
    private val classifierService = ClassifierService(context)
    private val preferencesManager = PreferencesManager(context)

    // Session cache to prevent duplicate processing within single scan
    private val processedFilesInSession = mutableSetOf<String>()

    /**
     * Main entry point for scanning WhatsApp media folders.
     *
     * Returns a Flow of MediaFile objects as they are discovered and classified.
     * Supports cancellation and provides efficient memory usage through streaming.
     *
     * @return Flow<MediaFile> Stream of classified media files
     */
    fun scanWhatsAppFolder(): Flow<MediaFile> = flow {
        Log.d(TAG, "Starting WhatsApp folder scan")

        try {
            // Clear session cache for fresh scan
            processedFilesInSession.clear()

            // Load cached classifications to avoid re-processing
            val cachedNotes = preferencesManager.getClassifiedNotesFiles()
            val cachedOthers = preferencesManager.getClassifiedOtherFiles()

            // Discover all available WhatsApp media directories
            val whatsAppMediaDirs = getWhatsAppMediaDirectories()

            if (whatsAppMediaDirs.isEmpty()) {
                Log.w(TAG, "No WhatsApp media directories found")
                return@flow
            }

            var foundFiles = false
            val newClassifications = NewClassificationCache()

            // Process each discovered directory
            for (mediaDir in whatsAppMediaDirs) {
                // Check for cancellation
                if (!coroutineContext.isActive) {
                    Log.d(TAG, "Scan cancelled")
                    break
                }

                Log.d(TAG, "Scanning WhatsApp media directory: ${mediaDir.absolutePath}")

                // Scan Images subdirectory
                val imagesDir = File(mediaDir, WHATSAPP_IMAGES_DIR)
                if (imagesDir.exists() && imagesDir.isDirectory) {
                    foundFiles = true
                    Log.d(TAG, "Processing images in: ${imagesDir.absolutePath}")

                    // Process new images
                    scanDirectoryRecursively(
                        directory = imagesDir,
                        isImagesDir = true,
                        cachedNotes = cachedNotes,
                        cachedOthers = cachedOthers,
                        newClassifications = newClassifications
                    ).collect { mediaFile ->
                        emit(mediaFile)
                        yield() // Allow cancellation
                    }

                    // Emit cached images that still exist
                    emitCachedFiles(
                        directory = imagesDir,
                        isImagesDir = true,
                        cachedNotes = cachedNotes,
                        cachedOthers = cachedOthers
                    ).collect { mediaFile ->
                        emit(mediaFile)
                        yield() // Allow cancellation
                    }
                }

                // Scan Documents subdirectory (PDFs) if enabled
                if (preferencesManager.includePdfScanning) {
                    val documentsDir = File(mediaDir, WHATSAPP_DOCUMENTS_DIR)
                    if (documentsDir.exists() && documentsDir.isDirectory) {
                        foundFiles = true
                        Log.d(TAG, "Processing documents in: ${documentsDir.absolutePath}")

                        // Process new PDFs
                        scanDirectoryRecursively(
                            directory = documentsDir,
                            isImagesDir = false,
                            cachedNotes = cachedNotes,
                            cachedOthers = cachedOthers,
                            newClassifications = newClassifications
                        ).collect { mediaFile ->
                            emit(mediaFile)
                            yield() // Allow cancellation
                        }

                        // Emit cached PDFs that still exist
                        emitCachedFiles(
                            directory = documentsDir,
                            isImagesDir = false,
                            cachedNotes = cachedNotes,
                            cachedOthers = cachedOthers
                        ).collect { mediaFile ->
                            emit(mediaFile)
                            yield() // Allow cancellation
                        }
                    }
                }
            }

            if (!foundFiles) {
                Log.w(TAG, "No WhatsApp media files found in any location")
            }

            // Save any remaining classifications to cache
            newClassifications.saveToCache(preferencesManager)

        } catch (e: Exception) {
            Log.e(TAG, "Error during WhatsApp folder scan", e)
            throw e
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Helper class to track new classifications during a scan session.
     */
    private class NewClassificationCache {
        val notes = mutableMapOf<String, Float>()
        val others = mutableMapOf<String, Float>()
        private var processedCount = 0

        fun addClassification(
            filePath: String, classification: Classification, preferencesManager: PreferencesManager
        ) {
            if (classification.label == "notes") {
                notes[filePath] = classification.confidence
            } else {
                others[filePath] = classification.confidence
            }

            // Save progress every 10 files to preserve work during long scans
            processedCount++
            if (processedCount % 10 == 0) {
                saveToCache(preferencesManager)
            }
        }

        fun saveToCache(preferencesManager: PreferencesManager) {
            try {
                if (notes.isNotEmpty()) {
                    val cachedNotes = preferencesManager.getClassifiedNotesFiles().toMutableMap()
                    cachedNotes.putAll(notes)
                    preferencesManager.saveClassifiedNotesFiles(cachedNotes)
                    notes.clear()
                }

                if (others.isNotEmpty()) {
                    val cachedOthers = preferencesManager.getClassifiedOtherFiles().toMutableMap()
                    cachedOthers.putAll(others)
                    preferencesManager.saveClassifiedOtherFiles(cachedOthers)
                    others.clear()
                }
            } catch (e: Exception) {
                Log.w("MediaScanner", "Failed to save progress at $processedCount files", e)
            }
        }
    }

    /**
     * Gets all possible WhatsApp Media directory locations based on Android version.
     * Handles the various storage locations across different Android versions and OEM customizations.
     */
    private fun getWhatsAppMediaDirectories(): List<File> {
        val dirs = mutableListOf<File>()
        val externalStorageDir = Environment.getExternalStorageDirectory()

        when {
            // Android 11+ (API 30+) - Enhanced Scoped Storage
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // Primary: storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media
                dirs.add(File(externalStorageDir, "Android/media/com.whatsapp/WhatsApp/Media"))
                // Fallback: storage/emulated/0/WhatsApp/Media (for legacy apps still using old structure)
                dirs.add(File(externalStorageDir, "WhatsApp/Media"))
            }

            // Android 10 (API 29) - Initial Scoped Storage
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // Primary: storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media
                dirs.add(File(externalStorageDir, "Android/media/com.whatsapp/WhatsApp/Media"))
                // Fallback: storage/emulated/0/WhatsApp/Media (for requestLegacyExternalStorage)
                dirs.add(File(externalStorageDir, "WhatsApp/Media"))
            }

            // Android Nougat to Pie (API 24-28) - Legacy External Storage
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> {
                // Primary: storage/emulated/0/WhatsApp/Media
                dirs.add(File(externalStorageDir, "WhatsApp/Media"))
                // Some OEMs moved to scoped storage early
                // Secondary: storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media
                dirs.add(File(externalStorageDir, "Android/media/com.whatsapp/WhatsApp/Media"))
            }

            // Android Marshmallow and below (API 23 and lower)
            else -> {
                // storage/emulated/0/WhatsApp/Media
                dirs.add(File(externalStorageDir, "WhatsApp/Media"))
            }
        }

        // Filter out directories that don't exist and log found directories
        val existingDirs = dirs.filter { it.exists() && it.isDirectory }
        existingDirs.forEach { dir ->
            Log.d(TAG, "Found WhatsApp media directory: ${dir.absolutePath}")
        }

        if (existingDirs.isEmpty()) {
            Log.w(
                TAG,
                "No WhatsApp media directories found. Checked locations: ${dirs.map { it.absolutePath }}"
            )
        }

        return existingDirs
    }

    /**
     * Recursively scans a directory for media files, processing new files through ML classification.
     *
     * @param directory Directory to scan
     * @param isImagesDir True if scanning images directory, false for documents
     * @param cachedNotes Previously classified notes files
     * @param cachedOthers Previously classified other files
     * @param newClassifications Cache for new classifications in this session
     */
    private fun scanDirectoryRecursively(
        directory: File,
        isImagesDir: Boolean,
        cachedNotes: Map<String, Float>,
        cachedOthers: Map<String, Float>,
        newClassifications: NewClassificationCache
    ): Flow<MediaFile> = flow {
        val files = directory.listFiles() ?: run {
            Log.w(TAG, "Cannot list files in directory: ${directory.absolutePath}")
            return@flow
        }

        for (file in files) {
            // Check for cancellation
            if (!coroutineContext.isActive) {
                Log.d(TAG, "Directory scan cancelled")
                break
            }

            if (file.isDirectory) {
                // Recursively scan subdirectories
                scanDirectoryRecursively(
                    directory = file,
                    isImagesDir = isImagesDir,
                    cachedNotes = cachedNotes,
                    cachedOthers = cachedOthers,
                    newClassifications = newClassifications
                ).collect { mediaFile ->
                    emit(mediaFile)
                }
            } else {
                // Process individual files
                val filePath = file.absolutePath

                // Skip already processed files in this session
                if (processedFilesInSession.contains(filePath)) {
                    continue
                }

                // Skip already cached files
                if (cachedNotes.containsKey(filePath) || cachedOthers.containsKey(filePath)) {
                    continue
                }

                // Process based on directory type and file extension
                try {
                    val mediaFile = when {
                        isImagesDir && isImageFile(file) -> {
                            processImageFile(file)
                        }

                        !isImagesDir && isPdfFile(file) -> {
                            processPdfFile(file, newClassifications)
                        }

                        else -> null
                    }

                    if (mediaFile != null) {
                        processedFilesInSession.add(filePath)
                        mediaFile.classification?.let { classification ->
                            newClassifications.addClassification(
                                filePath, classification, preferencesManager
                            )
                        }
                        emit(mediaFile)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing file: $filePath", e)
                    // Continue processing other files instead of failing completely
                }
            }
        }
    }

    /**
     * Emits cached files that still exist on the filesystem.
     */
    private fun emitCachedFiles(
        directory: File,
        isImagesDir: Boolean,
        cachedNotes: Map<String, Float>,
        cachedOthers: Map<String, Float>
    ): Flow<MediaFile> = flow {
        val files = directory.listFiles() ?: return@flow

        for (file in files) {
            // Check for cancellation
            if (!coroutineContext.isActive) break

            if (file.isDirectory) {
                // Recursively process subdirectories
                emitCachedFiles(file, isImagesDir, cachedNotes, cachedOthers).collect { mediaFile ->
                    emit(mediaFile)
                }
            } else {
                val filePath = file.absolutePath

                // Skip already processed files in this session
                if (processedFilesInSession.contains(filePath)) {
                    continue
                }

                // Check if file is in cache and create MediaFile
                try {
                    val mediaFile = when {
                        isImagesDir && isImageFile(file) -> {
                            createCachedImageFile(file, cachedNotes, cachedOthers)
                        }

                        !isImagesDir && isPdfFile(file) -> {
                            createCachedPdfFile(file, cachedNotes, cachedOthers)
                        }

                        else -> null
                    }

                    if (mediaFile != null) {
                        processedFilesInSession.add(filePath)
                        emit(mediaFile)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating cached file: $filePath", e)
                }
            }
        }
    }

    /**
     * Creates a MediaFile from cached image classification data.
     */
    private fun createCachedImageFile(
        file: File, cachedNotes: Map<String, Float>, cachedOthers: Map<String, Float>
    ): MediaFile? {
        val filePath = file.absolutePath

        return when {
            cachedNotes.containsKey(filePath) -> {
                createMediaFile(file, isImage = true).apply {
                    classification = Classification("notes", cachedNotes[filePath]!!)
                }
            }

            cachedOthers.containsKey(filePath) -> {
                createMediaFile(file, isImage = true).apply {
                    classification = Classification("not_notes", cachedOthers[filePath]!!)
                }
            }

            else -> null
        }
    }

    /**
     * Creates a MediaFile from cached PDF classification data.
     */
    private fun createCachedPdfFile(
        file: File, cachedNotes: Map<String, Float>, cachedOthers: Map<String, Float>
    ): MediaFile? {
        val filePath = file.absolutePath

        val classification = when {
            cachedNotes.containsKey(filePath) -> Classification("notes", cachedNotes[filePath]!!)
            cachedOthers.containsKey(filePath) -> Classification(
                "not_notes", cachedOthers[filePath]!!
            )

            else -> return null
        }

        // Generate thumbnail for PDF
        val thumbnailUri = generatePdfThumbnail(file)

        return createMediaFile(file, isImage = false, thumbnailUri = thumbnailUri).apply {
            this.classification = classification
        }
    }

    /**
     * Checks if a file is a supported image format.
     */
    private fun isImageFile(file: File): Boolean {
        return SUPPORTED_IMAGE_EXTENSIONS.contains(file.extension.lowercase())
    }

    /**
     * Checks if a file is a PDF document.
     */
    private fun isPdfFile(file: File): Boolean {
        return file.extension.equals("pdf", ignoreCase = true)
    }

    /**
     * Processes an image file through ML classification.
     */
    private suspend fun processImageFile(file: File): MediaFile? {
        return try {
            val mediaFile = createMediaFile(file, isImage = true)
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)

            if (bitmap != null) {
                val classification = classifierService.classifyImage(bitmap)
                mediaFile.apply { this.classification = classification }
            } else {
                Log.w(TAG, "Failed to decode image: ${file.name}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image file: ${file.name}", e)
            null
        }
    }

    /**
     * Processes a PDF file by sampling pages for classification.
     */
    private suspend fun processPdfFile(
        file: File, newClassifications: NewClassificationCache
    ): MediaFile? {
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                .use { fileDescriptor ->
                    PdfRenderer(fileDescriptor).use { renderer ->
                        if (renderer.pageCount == 0) {
                            Log.w(TAG, "PDF has no pages: ${file.name}")
                            return null
                        }

                        // Sample pages for classification (up to MAX_PDF_SAMPLE_PAGES)
                        val pagesToSample = minOf(renderer.pageCount, MAX_PDF_SAMPLE_PAGES)
                        val classifications = mutableListOf<Classification>()

                        // Generate thumbnail from first page
                        val thumbnailUri = generatePdfThumbnail(file)

                        for (pageIndex in 0 until pagesToSample) {
                            try {
                                renderer.openPage(pageIndex).use { page ->
                                    val bitmap = createBitmap(
                                        THUMBNAIL_SIZE, THUMBNAIL_SIZE, Bitmap.Config.ARGB_8888
                                    )
                                    page.render(
                                        bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                                    )

                                    val classification = classifierService.classifyImage(bitmap)
                                    classifications.add(classification)
                                    bitmap.recycle()
                                }
                            } catch (e: Exception) {
                                Log.w(
                                    TAG, "Error processing PDF page $pageIndex for ${file.name}", e
                                )
                            }
                        }

                        // Determine overall classification based on majority vote
                        val finalClassification = determineOverallClassification(classifications)
                        val mediaFile = createMediaFile(
                            file, isImage = false, thumbnailUri = thumbnailUri
                        ).apply {
                            classification = finalClassification
                        }

                        // Add to new classifications cache for batch saving
                        newClassifications.addClassification(
                            file.absolutePath, finalClassification, preferencesManager
                        )

                        mediaFile
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing PDF file: ${file.name}", e)
            null
        }
    }

    /**
     * Generates a thumbnail for a PDF file.
     */
    private fun generatePdfThumbnail(file: File): Uri? {
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                .use { fileDescriptor ->
                    PdfRenderer(fileDescriptor).use { renderer ->
                        if (renderer.pageCount > 0) {
                            renderer.openPage(0).use { page ->
                                val bitmap = createBitmap(
                                    THUMBNAIL_SIZE, THUMBNAIL_SIZE, Bitmap.Config.ARGB_8888
                                )
                                page.render(
                                    bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                                )

                                // Save thumbnail to cache directory
                                val thumbnailFile = File(
                                    context.cacheDir,
                                    "pdf_thumb_${file.nameWithoutExtension}_${System.currentTimeMillis()}.png"
                                )
                                thumbnailFile.outputStream().use { output ->
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, output)
                                }
                                bitmap.recycle()

                                thumbnailFile.toUri()
                            }
                        } else null
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to generate PDF thumbnail for ${file.name}", e)
            null
        }
    }

    /**
     * Determines overall classification from multiple page classifications using majority vote.
     */
    private fun determineOverallClassification(classifications: List<Classification>): Classification {
        if (classifications.isEmpty()) {
            return Classification("unknown", 0.0f)
        }

        if (classifications.size == 1) {
            return classifications.first()
        }

        // Group by label and calculate average confidence
        val grouped = classifications.groupBy { it.label }
        val averageConfidences = grouped.mapValues { (_, classificationList) ->
            classificationList.map { it.confidence }.average().toFloat()
        }

        // Return the label with highest average confidence
        val (label, confidence) = averageConfidences.maxByOrNull { it.value }
            ?: return Classification("unknown", 0.0f)

        return Classification(label, confidence)
    }

    /**
     * Creates a MediaFile instance from a File with proper metadata.
     */
    private fun createMediaFile(
        file: File, isImage: Boolean, thumbnailUri: Uri? = null
    ): MediaFile {
        return MediaFile(
            uri = file.toUri(),
            file = file,
            name = file.name,
            path = file.absolutePath,
            size = file.length(),
            isImage = isImage,
            isPdf = !isImage,
            thumbnailUri = thumbnailUri
        )
    }

    /**
     * Releases resources when MediaScanner is no longer needed.
     * Call this to prevent memory leaks.
     */
    fun onDestroy() {
        try {
            classifierService.release()
            processedFilesInSession.clear()
            Log.d(TAG, "MediaScanner resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaScanner resources", e)
        }
    }
}