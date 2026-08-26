package com.sih.app.core.data

import android.content.Context

class AppPreferencesStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isOnboardingCompleted(): Boolean =
        prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    fun isFarmSetupCompleted(): Boolean =
        prefs.getBoolean(KEY_FARM_SETUP_COMPLETED, false)

    fun setFarmSetupCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_FARM_SETUP_COMPLETED, completed).apply()
    }

    companion object {
        private const val PREFS_NAME = "sih_app_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_FARM_SETUP_COMPLETED = "farm_setup_completed"
    }
}
