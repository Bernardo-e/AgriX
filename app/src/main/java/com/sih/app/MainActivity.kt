package com.sih.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import android.util.Log
import com.sih.app.core.locale.LanguageStore
import com.sih.app.core.locale.LocaleHelper
import com.sih.app.core.navigation.SihNavHost
import com.sih.app.ui.theme.SIHTheme

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase, LanguageStore.languageTag(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContainer = (application as SihApp).container
        enableEdgeToEdge()
        setContent {
            SIHTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing,
                ) { innerPadding ->
                    SihNavHost(
                        appContainer = appContainer,
                        onLanguageContinue = {
                            recreate()
                        },
                        onFarmSetupFinished = {
                            Log.d("AgriX_Debug", "10. [MainActivity] onFarmSetupFinished invoked. Setting appContainer.farmSetupCompleted = true. Execution stops here because no further navigation destination is registered.")
                            appContainer.farmSetupCompleted = true
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
