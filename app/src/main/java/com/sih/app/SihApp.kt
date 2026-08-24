package com.sih.app

import android.app.Application
import android.content.Context
import com.sih.app.core.locale.LanguageStore
import com.sih.app.core.locale.LocaleHelper
import com.sih.app.di.AppContainer

class SihApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base, LanguageStore.languageTag(base)))
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
