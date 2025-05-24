package com.bitblazer.whatsweep.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken

/**
 * Manager for app preferences and caching.
 * Handles storage and retrieval of user settings and file classification cache.
 */
class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME, Context.MODE_PRIVATE
    )

    private val gson = Gson()

    var includePdfScanning: Boolean
        get() = prefs.getBoolean(KEY_INCLUDE_PDF_SCANNING, false)
        set(value) = prefs.edit { putBoolean(KEY_INCLUDE_PDF_SCANNING, value) }

    var showConfidenceScores: Boolean
        get() = prefs.getBoolean(KEY_SHOW_CONFIDENCE_SCORES, false)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_CONFIDENCE_SCORES, value) }

    var confidenceThreshold: Float
        get() = prefs.getFloat(KEY_CONFIDENCE_THRESHOLD, DEFAULT_CONFIDENCE_THRESHOLD)
        set(value) = prefs.edit {
            putFloat(KEY_CONFIDENCE_THRESHOLD, value.coerceIn(MIN_CONFIDENCE, MAX_CONFIDENCE))
        }

    /**
     * Cache data class for storing file classification with confidence.
     */
    data class CachedClassification(val path: String, val confidence: Float)

    /**
     * Retrieves cached classification data for notes files.
     * @return Map of file paths to confidence scores, empty map if no cache or error
     */
    fun getClassifiedNotesFiles(): Map<String, Float> {
        return getCachedClassifications(KEY_NOTES_CACHE, "notes")
    }

    /**
     * Retrieves cached classification data for other files.
     * @return Map of file paths to confidence scores, empty map if no cache or error
     */
    fun getClassifiedOtherFiles(): Map<String, Float> {
        return getCachedClassifications(KEY_OTHER_CACHE, "other")
    }

    /**
     * Common method to retrieve cached classifications with error handling.
     */
    private fun getCachedClassifications(key: String, type: String): Map<String, Float> {
        return try {
            val json = prefs.getString(key, null)
            if (json != null) {
                val listType = object : TypeToken<List<CachedClassification>>() {}.type
                val list: List<CachedClassification> = gson.fromJson(json, listType) ?: emptyList()

                // Validate and filter out invalid entries
                val validClassifications = list.filter { entry ->
                    entry.path.isNotEmpty() && entry.confidence in 0.0f..1.0f
                }

                Log.d(TAG, "Loaded ${validClassifications.size} cached $type classifications")
                validClassifications.associate { it.path to it.confidence }
            } else {
                Log.d(TAG, "No cached $type classifications found")
                emptyMap()
            }
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse cached $type classifications JSON", e)
            // Clear corrupted cache
            prefs.edit { remove(key) }
            emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cached $type classifications", e)
            emptyMap()
        }
    }

    /**
     * Saves classified notes files to cache.
     * @param filePathsWithConfidence Map of file paths to confidence scores
     */
    fun saveClassifiedNotesFiles(filePathsWithConfidence: Map<String, Float>) {
        saveClassifications(filePathsWithConfidence, KEY_NOTES_CACHE, "notes")
    }

    /**
     * Saves classified other files to cache.
     * @param filePathsWithConfidence Map of file paths to confidence scores
     */
    fun saveClassifiedOtherFiles(filePathsWithConfidence: Map<String, Float>) {
        saveClassifications(filePathsWithConfidence, KEY_OTHER_CACHE, "other")
    }

    /**
     * Common method to save classifications with error handling.
     */
    private fun saveClassifications(
        filePathsWithConfidence: Map<String, Float>,
        key: String,
        type: String
    ) {
        try {
            // Validate data before saving
            val validClassifications = filePathsWithConfidence
                .filter { (path, confidence) ->
                    path.isNotEmpty() && confidence in 0.0f..1.0f
                }
                .map { (path, confidence) -> CachedClassification(path, confidence) }

            val json = gson.toJson(validClassifications)
            prefs.edit { putString(key, json) }

            Log.d(TAG, "Saved ${validClassifications.size} $type classifications to cache")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save $type classifications to cache", e)
        }
    }

    /**
     * Clears all cached classification data.
     * @throws Exception if clearing fails
     */
    fun clearClassificationCache() {
        try {
            val notesCount = getClassifiedNotesFiles().size
            val otherCount = getClassifiedOtherFiles().size

            prefs.edit {
                remove(KEY_NOTES_CACHE)
                remove(KEY_OTHER_CACHE)
            }

            Log.d(TAG, "Cleared classification cache: $notesCount notes, $otherCount other files")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear classification cache", e)
            throw e
        }
    }

    /**
     * Gets cache statistics for debugging and user information.
     */
    fun getCacheStatistics(): CacheStatistics {
        return CacheStatistics(
            notesCount = getClassifiedNotesFiles().size,
            otherCount = getClassifiedOtherFiles().size
        )
    }

    /**
     * Data class for cache statistics.
     */
    data class CacheStatistics(
        val notesCount: Int,
        val otherCount: Int
    ) {
        val totalCount: Int get() = notesCount + otherCount
    }

    companion object {
        private const val TAG = "PreferencesManager"
        private const val PREFERENCES_NAME = "whatsweep_preferences"
        private const val KEY_INCLUDE_PDF_SCANNING = "include_pdf_scanning"
        private const val KEY_SHOW_CONFIDENCE_SCORES = "show_confidence_scores"
        private const val KEY_CONFIDENCE_THRESHOLD = "confidence_threshold"
        private const val KEY_NOTES_CACHE = "notes_cache"
        private const val KEY_OTHER_CACHE = "other_cache"

        // Confidence threshold constraints
        private const val DEFAULT_CONFIDENCE_THRESHOLD = 0.7f
        private const val MIN_CONFIDENCE = 0.5f
        private const val MAX_CONFIDENCE = 0.95f
    }
}