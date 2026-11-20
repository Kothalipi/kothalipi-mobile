package com.kothalipi.mobile

import android.app.Application
import android.content.Context
import android.content.SharedPreferences

class KothalipiApp : Application() {

    companion object {
        lateinit var instance: KothalipiApp
            private set

        const val PREFS_NAME = "kothalipi_mobile_prefs"
        const val KEY_GEMINI_API_KEY = "gemini_api_key"
        const val KEY_DICTATION_MODE = "dictation_mode" // "bn", "en", "bn_to_en"
        const val KEY_TRANSLATE_TO_ENGLISH = "translate_to_english"
        const val KEY_SMART_POLISH = "smart_polish"

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

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEMINI_API_KEY, value).apply()

    var dictationMode: String
        get() = prefs.getString(KEY_DICTATION_MODE, "bn") ?: "bn"
        set(value) = prefs.edit().putString(KEY_DICTATION_MODE, value).apply()

    var translateToEnglish: Boolean
        get() = dictationMode == "bn_to_en"
        set(value) {
            dictationMode = if (value) "bn_to_en" else "bn"
        }

    var smartPolish: Boolean
        get() = prefs.getBoolean(KEY_SMART_POLISH, true)
        set(value) = prefs.edit().putBoolean(KEY_SMART_POLISH, value).apply()

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
