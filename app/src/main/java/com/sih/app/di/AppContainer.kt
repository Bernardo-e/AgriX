package com.sih.app.di

import android.content.Context
import androidx.room.Room
import com.sih.app.core.ai.AdvisoryRepository
import com.sih.app.core.ai.AiEngineRouter
import com.sih.app.core.ai.cloud.CloudAiEngine
import com.sih.app.core.ai.local.LocalAiEngine
import com.sih.app.core.data.AppPreferencesStore
import com.sih.app.core.data.DiagnosisRepository
import com.sih.app.core.data.FarmRepository
import com.sih.app.core.data.api.DiagnosisApiClient
import com.sih.app.core.data.api.HttpDiagnosisApiClient
import com.sih.app.core.database.AgriXDatabase
import com.sih.app.core.locale.LanguageStore
import com.sih.app.core.location.LocationRepository
import com.sih.app.core.sensor.BleSensorRepository

class AppContainer(context: Context) {

    val languageStore = LanguageStore(context)

    val preferencesStore by lazy {
        AppPreferencesStore(context)
    }

    val database: AgriXDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            AgriXDatabase::class.java,
            "agrix_database",
        ).addMigrations(
            AgriXDatabase.MIGRATION_1_2,
            AgriXDatabase.MIGRATION_2_3,
        ).build()
    }

    val farmRepository: FarmRepository by lazy {
        FarmRepository(database.farmDao())
    }

    val diagnosisApiClient: DiagnosisApiClient by lazy {
        HttpDiagnosisApiClient(baseUrl = "http://10.0.2.2:8000")
    }

    val diagnosisRepository: DiagnosisRepository by lazy {
        DiagnosisRepository(
            diagnosisDao = database.diagnosisDao(),
            apiClient = diagnosisApiClient,
        )
    }

    val locationRepository: LocationRepository by lazy {
        LocationRepository(context.applicationContext)
    }

    val bleSensorRepository: BleSensorRepository by lazy {
        BleSensorRepository(context.applicationContext)
    }

    val localAiEngine: LocalAiEngine by lazy {
        LocalAiEngine(context.applicationContext)
    }

    val advisoryRepository: AdvisoryRepository by lazy {
        AdvisoryRepository(context.applicationContext)
    }

    val cloudAiEngine: CloudAiEngine by lazy {
        CloudAiEngine(context = context.applicationContext)
    }

    val aiEngineRouter: AiEngineRouter by lazy {
        AiEngineRouter(
            localAiEngine = localAiEngine,
            cloudAiEngine = cloudAiEngine,
        )
    }

    var splashCompleted: Boolean = false

    var languageSelectionCompleted: Boolean
        get() = languageStore.getLanguageTag().isNotBlank()
        set(_) {}

    var onboardingCompleted: Boolean
        get() = preferencesStore.isOnboardingCompleted()
        set(value) = preferencesStore.setOnboardingCompleted(value)

    var farmSetupCompleted: Boolean
        get() = preferencesStore.isFarmSetupCompleted()
        set(value) = preferencesStore.setFarmSetupCompleted(value)
}
