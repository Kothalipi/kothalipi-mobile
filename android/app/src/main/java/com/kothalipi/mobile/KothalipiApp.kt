package com.kothalipi.mobile

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import uniffi.kothalipi_core.KothalipiMobileEngine

class KothalipiApp : Application() {

    companion object {
        lateinit var instance: KothalipiApp
            private set

        const val PREFS_NAME = "kothalipi_mobile_prefs"
        const val KEY_GEMINI_API_KEY = "gemini_api_key"
        const val KEY_DICTATION_MODE = "dictation_mode" // "bn", "en", "bn_to_en", "auto"
        const val KEY_WRITING_STYLE = "writing_style"   // "normal", "formal", "casual", "bullets"
        const val KEY_SMART_POLISH = "smart_polish"
        const val KEY_HISTORY = "dictation_history_v1"

        private const val MAX_HISTORY_ITEMS = 50

        init {
            try {
                System.loadLibrary("kothalipi_core")
            } catch (e: UnsatisfiedLinkError) {
                e.printStackTrace()
            }
        }
    }

    val prefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Shared Rust engine instance (reuses single Tokio runtime, avoids leaks)
    val engine: KothalipiMobileEngine by lazy {
        KothalipiMobileEngine(geminiApiKey)
    }

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_API_KEY, "")?.trim() ?: ""
        set(value) {
            val cleaned = value.trim()
            prefs.edit().putString(KEY_GEMINI_API_KEY, cleaned).apply()
            try {
                engine.setApiKey(cleaned)
            } catch (_: Throwable) {
            }
        }

    var dictationMode: String
        get() = prefs.getString(KEY_DICTATION_MODE, "bn") ?: "bn"
        set(value) = prefs.edit().putString(KEY_DICTATION_MODE, value).apply()

    var writingStyle: String
        get() = prefs.getString(KEY_WRITING_STYLE, "normal") ?: "normal"
        set(value) = prefs.edit().putString(KEY_WRITING_STYLE, value).apply()

    var translateToEnglish: Boolean
        get() = dictationMode == "bn_to_en"
        set(value) {
            dictationMode = if (value) "bn_to_en" else "bn"
        }

    var smartPolish: Boolean
        get() = prefs.getBoolean(KEY_SMART_POLISH, true)
        set(value) = prefs.edit().putBoolean(KEY_SMART_POLISH, value).apply()

    private val _history = MutableStateFlow<List<DictationHistoryItem>>(emptyList())
    val history: StateFlow<List<DictationHistoryItem>> = _history.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        instance = this
        _history.value = loadHistory()
    }

    fun addHistoryItem(text: String, mode: String, style: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val newItem = DictationHistoryItem(
            id = System.currentTimeMillis(),
            text = trimmed,
            mode = mode,
            style = style,
            timestamp = System.currentTimeMillis()
        )
        val updated = (listOf(newItem) + _history.value).take(MAX_HISTORY_ITEMS)
        _history.value = updated
        saveHistory(updated)
    }

    fun deleteHistoryItem(id: Long) {
        val updated = _history.value.filterNot { it.id == id }
        _history.value = updated
        saveHistory(updated)
    }

    fun clearHistory() {
        _history.value = emptyList()
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun loadHistory(): List<DictationHistoryItem> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        DictationHistoryItem(
                            id = obj.optLong("id", i.toLong()),
                            text = obj.optString("text", ""),
                            mode = obj.optString("mode", "bn"),
                            style = obj.optString("style", "normal"),
                            timestamp = obj.optLong("timestamp", 0L)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveHistory(items: List<DictationHistoryItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("text", item.text)
                    put("mode", item.mode)
                    put("style", item.style)
                    put("timestamp", item.timestamp)
                }
            )
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }
}
