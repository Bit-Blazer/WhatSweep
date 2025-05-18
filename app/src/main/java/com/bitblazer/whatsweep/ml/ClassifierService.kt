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

class ClassifierService(private val context: Context) {

    private val TAG = "ClassifierService"
    private val preferencesManager = PreferencesManager(context)

    private val localModel =
        LocalModel.Builder().setAssetFilePath("notes_classifier_quantized.tflite").build()

    private val options: CustomImageLabelerOptions
        get() = CustomImageLabelerOptions.Builder(localModel)
            .setConfidenceThreshold(preferencesManager.confidenceThreshold).setMaxResultCount(2)
            .build()

    private var imageLabeler: ImageLabeler = ImageLabeling.getClient(options)

    suspend fun classifyImage(bitmap: Bitmap): Classification = withContext(Dispatchers.IO) {
        try {
            // Preprocess the bitmap - resize and normalize manually
            val processedBitmap = preprocessBitmap(bitmap)
            val image = InputImage.fromBitmap(processedBitmap, 0)
            //  Log.d(TAG, "Processing image of size ${processedBitmap.width}x${processedBitmap.height}")

            return@withContext suspendCancellableCoroutine { continuation ->
                imageLabeler.process(image).addOnSuccessListener { labels ->
                    if (labels.isNotEmpty()) {
                        val topLabel = labels[0]
                        Log.d(
                            TAG,
                            "Classification successful: ${topLabel.text} (${topLabel.confidence})"
                        )
                        continuation.resume(
                            Classification(
                                label = topLabel.text, confidence = topLabel.confidence
                            )
                        )
                    } else {
                        //    Log.d(TAG, "No labels returned by classifier")
                        continuation.resume(Classification("unknown", 0.0f))
                    }
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Classification error", e)
                    continuation.resumeWithException(e)
                }

                continuation.invokeOnCancellation {
                    // No need to cancel ML Kit operations
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in image classification", e)
            return@withContext Classification("error", 0.0f)
        }
    }

    private fun preprocessBitmap(bitmap: Bitmap): Bitmap {
        // 1. Resize to target size
        val targetSize = 224 // Common size for image classification models
        val resizedBitmap = bitmap.scale(targetSize, targetSize, false)

        // 2. Create a copy of the bitmap to ensure it's in the right format (ARGB_8888)
        val processedBitmap = resizedBitmap.copy(Bitmap.Config.ARGB_8888, true)

        // Most TensorFlow models require images to be between [0,1] or [-1,1]
        // Unfortunately, we can't easily preprocess/normalize pixels here without a custom model
        // We rely on the model to have proper metadata for preprocessing

        return processedBitmap
    }

    fun close() {
        imageLabeler.close()
    }

    fun updateOptions() {
        // Close the existing labeler and create a new one with updated options
        imageLabeler.close()
        imageLabeler = ImageLabeling.getClient(options)
    }
}