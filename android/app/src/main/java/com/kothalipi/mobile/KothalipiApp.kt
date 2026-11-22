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
import uniffi.kothalipi_core.VoiceSnippet

data class SnippetItem(
    val id: Long,
    val trigger: String,
    val content: String
)

class KothalipiApp : Application() {

    companion object {
        lateinit var instance: KothalipiApp
            private set

        const val PREFS_NAME = "kothalipi_mobile_prefs"
        const val KEY_GEMINI_API_KEY = "gemini_api_key"
        const val KEY_GROQ_API_KEY = "groq_api_key"
        const val KEY_OPENAI_API_KEY = "openai_api_key"
        const val KEY_PRIMARY_PROVIDER = "primary_provider" // "auto", "gemini", "groq", "openai"
        const val KEY_DICTATION_MODE = "dictation_mode"     // "bn", "en", "bn_to_en", "auto"
        const val KEY_WRITING_STYLE = "writing_style"       // "normal", "formal", "casual", "bullets"
        const val KEY_SMART_POLISH = "smart_polish"
        const val KEY_AUTO_SILENCE_STOP = "auto_silence_stop"
        const val KEY_OFFLINE_MODE = "offline_mode"
        const val KEY_HISTORY = "dictation_history_v1"
        const val KEY_SNIPPETS = "voice_snippets_v1"

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

    // Shared Rust engine instance (reuses single Tokio runtime, synced with all keys & snippets)
    val engine: KothalipiMobileEngine by lazy {
        KothalipiMobileEngine(geminiApiKey).apply {
            syncEngineConfig(this)
        }
    }

    private fun syncEngineConfig(eng: KothalipiMobileEngine) {
        try {
            eng.setApiKey(geminiApiKey)
            eng.setGroqApiKey(groqApiKey)
            eng.setOpenaiApiKey(openaiApiKey)
            eng.setPrimaryProvider(primaryProvider)
            eng.setSnippets(_snippets.value.map { VoiceSnippet(it.trigger, it.content) })
        } catch (_: Throwable) {
        }
    }

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_API_KEY, "")?.trim() ?: ""
        set(value) {
            val cleaned = value.trim()
            prefs.edit().putString(KEY_GEMINI_API_KEY, cleaned).apply()
            try { engine.setApiKey(cleaned) } catch (_: Throwable) {}
        }

    var groqApiKey: String
        get() = prefs.getString(KEY_GROQ_API_KEY, "")?.trim() ?: ""
        set(value) {
            val cleaned = value.trim()
            prefs.edit().putString(KEY_GROQ_API_KEY, cleaned).apply()
            try { engine.setGroqApiKey(cleaned) } catch (_: Throwable) {}
        }

    var openaiApiKey: String
        get() = prefs.getString(KEY_OPENAI_API_KEY, "")?.trim() ?: ""
        set(value) {
            val cleaned = value.trim()
            prefs.edit().putString(KEY_OPENAI_API_KEY, cleaned).apply()
            try { engine.setOpenaiApiKey(cleaned) } catch (_: Throwable) {}
        }

    var primaryProvider: String
        get() = prefs.getString(KEY_PRIMARY_PROVIDER, "auto") ?: "auto"
        set(value) {
            prefs.edit().putString(KEY_PRIMARY_PROVIDER, value).apply()
            try { engine.setPrimaryProvider(value) } catch (_: Throwable) {}
        }

    val hasAnyApiKey: Boolean
        get() = geminiApiKey.isNotBlank() || groqApiKey.isNotBlank() || openaiApiKey.isNotBlank()

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

    var autoSilenceStop: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SILENCE_STOP, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SILENCE_STOP, value).apply()

    var offlineMode: Boolean
        get() = prefs.getBoolean(KEY_OFFLINE_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_OFFLINE_MODE, value).apply()

    private val _history = MutableStateFlow<List<DictationHistoryItem>>(emptyList())
    val history: StateFlow<List<DictationHistoryItem>> = _history.asStateFlow()

    private val _snippets = MutableStateFlow<List<SnippetItem>>(emptyList())
    val snippets: StateFlow<List<SnippetItem>> = _snippets.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        instance = this
        _history.value = loadHistory()
        _snippets.value = loadSnippets()
    }

    fun addSnippet(trigger: String, content: String) {
        val t = trigger.trim()
        val c = content.trim()
        if (t.isEmpty() || c.isEmpty()) return
        val updated = listOf(SnippetItem(System.currentTimeMillis(), t, c)) + _snippets.value
        _snippets.value = updated
        saveSnippets(updated)
        try { engine.setSnippets(updated.map { VoiceSnippet(it.trigger, it.content) }) } catch (_: Throwable) {}
    }

    fun deleteSnippet(id: Long) {
        val updated = _snippets.value.filterNot { it.id == id }
        _snippets.value = updated
        saveSnippets(updated)
        try { engine.setSnippets(updated.map { VoiceSnippet(it.trigger, it.content) }) } catch (_: Throwable) {}
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

    private fun loadSnippets(): List<SnippetItem> {
        val raw = prefs.getString(KEY_SNIPPETS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        SnippetItem(
                            id = obj.optLong("id", i.toLong()),
                            trigger = obj.optString("trigger", ""),
                            content = obj.optString("content", "")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveSnippets(items: List<SnippetItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("trigger", item.trigger)
                    put("content", item.content)
                }
            )
        }
        prefs.edit().putString(KEY_SNIPPETS, arr.toString()).apply()
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
