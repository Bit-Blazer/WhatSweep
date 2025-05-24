package com.bitblazer.whatsweep.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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

    // Cache for file classification to avoid rescanning
    // Cache data class for storing file classification with confidence
    data class CachedClassification(val path: String, val confidence: Float)

    fun getClassifiedNotesFiles(): Map<String, Float> {
        val json = prefs.getString(KEY_NOTES_CACHE, null)
        return if (json != null) {
            val type = object : TypeToken<List<CachedClassification>>() {}.type
            val list: List<CachedClassification> = gson.fromJson(json, type) ?: emptyList()
            list.associate { it.path to it.confidence }
        } else {
            emptyMap()
        }
    }

    fun getClassifiedOtherFiles(): Map<String, Float> {
        val json = prefs.getString(KEY_OTHER_CACHE, null)
        return if (json != null) {
            val type = object : TypeToken<List<CachedClassification>>() {}.type
            val list: List<CachedClassification> = gson.fromJson(json, type) ?: emptyList()
            list.associate { it.path to it.confidence }
        } else {
            emptyMap()
        }
    }

    fun saveClassifiedNotesFiles(filePathsWithConfidence: Map<String, Float>) {
        val list = filePathsWithConfidence.map { CachedClassification(it.key, it.value) }
        val json = gson.toJson(list)
        prefs.edit { putString(KEY_NOTES_CACHE, json) }
    }

    fun saveClassifiedOtherFiles(filePathsWithConfidence: Map<String, Float>) {
        val list = filePathsWithConfidence.map { CachedClassification(it.key, it.value) }
        val json = gson.toJson(list)
        prefs.edit { putString(KEY_OTHER_CACHE, json) }
    }

    fun clearClassificationCache() {
        prefs.edit {
            remove(KEY_NOTES_CACHE)
            remove(KEY_OTHER_CACHE)
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "whatsweep_preferences"
        private const val KEY_INCLUDE_PDF_SCANNING = "include_pdf_scanning"
        private const val KEY_SHOW_CONFIDENCE_SCORES = "show_confidence_scores"
        private const val KEY_CONFIDENCE_THRESHOLD = "confidence_threshold"
        private const val KEY_NOTES_CACHE = "notes_cache"
        private const val KEY_OTHER_CACHE = "other_cache"
    }
}