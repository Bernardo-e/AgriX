package com.sih.app.core.locale

import android.content.Context

class LanguageStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLanguageTag(): String =
        prefs.getString(KEY_LANGUAGE_TAG, AppLanguage.Default.tag) ?: AppLanguage.Default.tag

    fun setLanguageTag(tag: String) {
        prefs.edit().putString(KEY_LANGUAGE_TAG, tag).apply()
    }

    companion object {
        private const val PREFS_NAME = "sih_prefs"
        private const val KEY_LANGUAGE_TAG = "language_tag"

        fun languageTag(context: Context): String =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE_TAG, AppLanguage.Default.tag)
                ?: AppLanguage.Default.tag
    }
}
