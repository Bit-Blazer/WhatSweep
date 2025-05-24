package com.bitblazer.whatsweep.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import com.bitblazer.whatsweep.model.Classification
import com.bitblazer.whatsweep.util.PreferencesManager
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Service responsible for ML Kit custom model image classification.
 *
 * Handles TensorFlow Lite model loading, image preprocessing with EXIF rotation,
 * and provides thread-safe inference operations with proper resource management.
 *
 * Features:
 * - Automatic EXIF orientation correction
 * - Memory-efficient image scaling to prevent OOM
 * - Configurable confidence thresholds
 * - Proper model lifecycle management
 */
class ClassifierService(private val context: Context) {

    companion object {
        const val MODEL_NAME = "notes_classifier_quantized.tflite"
        const val TAG = "ClassifierService"
        private const val TARGET_IMAGE_SIZE = 224 // Standard input size for most models
        private const val MAX_BITMAP_SIZE = 2048 // Prevent OOM on large images
    }

    private val preferencesManager = PreferencesManager(context)

    // Lazy initialization of ML model to improve app startup time
    private val localModel by lazy {
        LocalModel.Builder().setAssetFilePath(MODEL_NAME).build()
    }

    // Create labeler options with current preferences
    private fun createLabelerOptions(): CustomImageLabelerOptions {
        return CustomImageLabelerOptions.Builder(localModel)
            .setConfidenceThreshold(preferencesManager.confidenceThreshold)
            .setMaxResultCount(2) // We only need top 2 results for binary classification
            .build()
    }

    // Thread-safe labeler instance creation
    @Volatile
    private var imageLabeler: ImageLabeler? = null

    private fun getOrCreateLabeler(): ImageLabeler {
        return imageLabeler ?: synchronized(this) {
            imageLabeler ?: ImageLabeling.getClient(createLabelerOptions()).also {
                imageLabeler = it
            }
        }
    }

    /**
     * Classifies an image from a URI with proper EXIF handling and memory management.
     *
     * @param imageUri URI of the image to classify
     * @return Classification result with label and confidence
     * @throws IOException if image cannot be loaded
     * @throws IllegalArgumentException if URI is invalid
     */
    suspend fun classifyImageFromUri(imageUri: Uri): Classification =
        withContext(Dispatchers.Default) {
            try {
                val bitmap = loadAndPreprocessImage(imageUri)
                    ?: throw IOException("Failed to load image from URI: $imageUri")

                return@withContext classifyBitmap(bitmap)
            } catch (e: Exception) {
                Log.e(TAG, "Error classifying image from URI: $imageUri", e)
                throw e
            }
        }

    /**
     * Classifies a bitmap image with preprocessing.
     *
     * @param bitmap Input bitmap to classify
     * @return Classification result
     */
    suspend fun classifyImage(bitmap: Bitmap): Classification = withContext(Dispatchers.Default) {
        try {
            // Preprocess the bitmap - resize and normalize manually
            val processedBitmap = preprocessBitmap(bitmap)
            return@withContext classifyBitmap(processedBitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Error in bitmap classification", e)
            throw e
        }
    }

    /**
     * Core classification method using ML Kit ImageLabeler.
     */
    private suspend fun classifyBitmap(bitmap: Bitmap): Classification {
        val image = InputImage.fromBitmap(bitmap, 0)
        val labeler = getOrCreateLabeler()

        return suspendCancellableCoroutine { continuation ->
            labeler.process(image).addOnSuccessListener { labels ->
                if (labels.isNotEmpty()) {
                    val topLabel = labels[0]
                    Log.d(
                        TAG, "Classification successful: ${topLabel.text} (${topLabel.confidence})"
                    )

                    continuation.resume(
                        Classification(
                            label = topLabel.text, confidence = topLabel.confidence
                        )
                    )
                } else {
                    Log.d(TAG, "No labels returned by classifier")
                    continuation.resume(Classification("unknown", 0.0f))
                }
            }.addOnFailureListener { exception ->
                Log.e(TAG, "ML Kit classification failed", exception)
                continuation.resumeWithException(exception)
            }

            // Handle cancellation gracefully
            continuation.invokeOnCancellation {
                Log.d(TAG, "Classification cancelled")
            }
        }
    }

    /**
     * Loads image from URI with proper EXIF orientation handling and memory management.
     */
    private fun loadAndPreprocessImage(imageUri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                // First, get image dimensions without loading full bitmap
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)

                // Calculate appropriate sample size to prevent OOM
                val sampleSize = calculateInSampleSize(options, MAX_BITMAP_SIZE, MAX_BITMAP_SIZE)

                // Reset stream and load the actual bitmap
                context.contentResolver.openInputStream(imageUri)?.use { secondStream ->
                    val loadOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.RGB_565 // Use less memory
                    }

                    val bitmap = BitmapFactory.decodeStream(secondStream, null, loadOptions)
                    bitmap?.let {
                        val correctedBitmap = correctImageOrientation(it, imageUri)
                        preprocessBitmap(correctedBitmap)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load image from URI: $imageUri", e)
            null
        }
    }

    /**
     * Corrects image orientation based on EXIF data.
     */
    private fun correctImageOrientation(bitmap: Bitmap, imageUri: Uri): Bitmap {
        return try {
            context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED
                )

                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipBitmap(
                        bitmap, horizontal = true
                    )

                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> flipBitmap(
                        bitmap, horizontal = false
                    )

                    else -> bitmap
                }
            } ?: bitmap
        } catch (e: Exception) {
            Log.w(TAG, "Could not read EXIF data, using original orientation", e)
            bitmap
        }
    }

    /**
     * Rotates bitmap by specified degrees.
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Flips bitmap horizontally or vertically.
     */
    private fun flipBitmap(bitmap: Bitmap, horizontal: Boolean): Bitmap {
        val matrix = Matrix().apply {
            if (horizontal) {
                preScale(-1f, 1f)
            } else {
                preScale(1f, -1f)
            }
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Calculates appropriate sample size to reduce memory usage.
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Preprocesses bitmap for optimal model inference.
     * Scales to target size and ensures proper format.
     */
    private fun preprocessBitmap(bitmap: Bitmap): Bitmap {
        // Scale to model's expected input size while maintaining aspect ratio
        val scaledBitmap =
            if (bitmap.width != TARGET_IMAGE_SIZE || bitmap.height != TARGET_IMAGE_SIZE) {
                bitmap.scale(TARGET_IMAGE_SIZE, TARGET_IMAGE_SIZE, filter = true)
            } else {
                bitmap
            }

        // Ensure bitmap is in ARGB_8888 format for ML Kit compatibility
        return if (scaledBitmap.config != Bitmap.Config.ARGB_8888) {
            scaledBitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            scaledBitmap
        }
    }

    /**
     * Releases ML Kit resources. Call this when the service is no longer needed.
     * This is critical for preventing memory leaks.
     */
    fun release() {
        try {
            imageLabeler?.close()
            imageLabeler = null
            Log.d(TAG, "ClassifierService resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing ClassifierService resources", e)
        }
    }
}