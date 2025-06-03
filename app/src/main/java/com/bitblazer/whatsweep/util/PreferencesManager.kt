package com.bitblazer.whatsweep.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.google.gson.Gson

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
        get() = prefs.getFloat(KEY_CONFIDENCE_THRESHOLD, 0.7f)
        set(value) = prefs.edit { putFloat(KEY_CONFIDENCE_THRESHOLD, value) }

    /**
     * Data class representing the classification cache.
     * Contains maps for notes and other classifications with their confidence scores.
     */
    data class ClassificationCache(
        val notes: Map<String, Float> = emptyMap(), val others: Map<String, Float> = emptyMap()
    )

    var cache: ClassificationCache
        get() = try {
            val json = prefs.getString(KEY_CLASSIFICATION_CACHE, null)
            if (json != null) {
                gson.fromJson(json, ClassificationCache::class.java)
            } else {
                Log.d(TAG, "No classification cache found")
                ClassificationCache()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse classification cache", e)
            ClassificationCache()
        }
        set(value) {
            val json = gson.toJson(value)
            prefs.edit { putString(KEY_CLASSIFICATION_CACHE, json) }
        }

    fun clearCache() {
        prefs.edit { remove(KEY_CLASSIFICATION_CACHE) }
    }

    companion object {
        private const val TAG = "PreferencesManager"
        private const val PREFERENCES_NAME = "whatsweep_preferences"
        private const val KEY_INCLUDE_PDF_SCANNING = "include_pdf_scanning"
        private const val KEY_SHOW_CONFIDENCE_SCORES = "show_confidence_scores"
        private const val KEY_CONFIDENCE_THRESHOLD = "confidence_threshold"
        private const val KEY_CLASSIFICATION_CACHE = "classification_cache"
    }
}