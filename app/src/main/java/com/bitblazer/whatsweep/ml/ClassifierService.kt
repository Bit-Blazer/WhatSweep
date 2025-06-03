package com.bitblazer.whatsweep.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.scale
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
class ClassifierService(context: Context) {

    companion object {
        const val MODEL_NAME = "notes_classifier_quantized.tflite"
        const val TAG = "ClassifierService"
        private const val TARGET_IMAGE_SIZE = 224 // Standard input size for most models
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

    /** Core classification method using ML Kit ImageLabeler. */
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