package com.sih.app.di

import android.content.Context
import androidx.room.Room
import com.sih.app.core.ai.AiEngineRouter
import com.sih.app.core.ai.cloud.CloudAiEngine
import com.sih.app.core.ai.local.LocalAiEngine
import com.sih.app.core.data.FarmRepository
import com.sih.app.core.database.AgriXDatabase
import com.sih.app.core.locale.LanguageStore
import com.sih.app.core.sensor.BleSensorRepository

class AppContainer(context: Context) {

    val languageStore = LanguageStore(context)

    val database: AgriXDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            AgriXDatabase::class.java,
            "agrix_database",
        ).build()
    }

    val farmRepository: FarmRepository by lazy {
        FarmRepository(database.farmDao())
    }

    val bleSensorRepository: BleSensorRepository by lazy {
        BleSensorRepository(context.applicationContext)
    }

    val localAiEngine: LocalAiEngine by lazy {
        LocalAiEngine(context.applicationContext)
    }

    val cloudAiEngine: CloudAiEngine by lazy {
        CloudAiEngine()
    }

    val aiEngineRouter: AiEngineRouter by lazy {
        AiEngineRouter(
            localAiEngine = localAiEngine,
            cloudAiEngine = cloudAiEngine,
        )
    }

    var splashCompleted: Boolean = false

    var languageSelectionCompleted: Boolean = false

    var onboardingCompleted: Boolean = false

    var farmSetupCompleted: Boolean = false
}
