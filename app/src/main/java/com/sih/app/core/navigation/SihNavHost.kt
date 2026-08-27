package com.sih.app.core.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sih.app.core.locale.AppLanguage
import com.sih.app.di.AppContainer
import com.sih.app.ui.ai.AiScreen
import com.sih.app.ui.ai.AiViewModel
import com.sih.app.ui.ai.CropDiseaseScanScreen
import com.sih.app.ui.ai.CropDiseaseScanViewModel
import com.sih.app.ui.ai.DiagnosisHistoryScreen
import com.sih.app.ui.ai.DiagnosisHistoryViewModel
import com.sih.app.ui.farmsetup.FarmSavedScreen
import com.sih.app.ui.farmsetup.FarmSetupScreen
import com.sih.app.ui.farmsetup.FarmSetupViewModel
import com.sih.app.ui.home.HomeScreen
import com.sih.app.ui.home.HomeViewModel
import com.sih.app.ui.language.LanguageSelectionScreen
import com.sih.app.ui.onboarding.OnboardingScreen
import com.sih.app.ui.sensor.SensorConnectionScreen
import com.sih.app.ui.sensor.SensorConnectionViewModel
import com.sih.app.ui.splash.SplashScreen

@Composable
fun SihNavHost(
    appContainer: AppContainer,
    onLanguageContinue: (AppLanguage) -> Unit,
    onFarmSetupFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val startDestination = when {
        appContainer.farmSetupCompleted -> SihRoute.Home
        appContainer.onboardingCompleted -> SihRoute.FarmSetup
        appContainer.languageSelectionCompleted -> SihRoute.Onboarding
        appContainer.splashCompleted -> SihRoute.Language
        else -> SihRoute.Splash
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(route = SihRoute.Splash) {
            SplashScreen(
                onFinished = {
                    appContainer.splashCompleted = true
                    val nextRoute = when {
                        appContainer.farmSetupCompleted -> SihRoute.Home
                        appContainer.onboardingCompleted -> SihRoute.FarmSetup
                        appContainer.languageSelectionCompleted -> SihRoute.Onboarding
                        else -> SihRoute.Language
                    }
                    navController.navigate(nextRoute) {
                        popUpTo(SihRoute.Splash) { inclusive = true }
                    }
                },
            )
        }
        composable(route = SihRoute.Language) {
            var selectedLanguage by rememberSaveable {
                mutableStateOf(AppLanguage.fromTag(appContainer.languageStore.getLanguageTag()))
            }
            LanguageSelectionScreen(
                selectedLanguage = selectedLanguage,
                onLanguageSelected = { selectedLanguage = it },
                onContinue = {
                    val previousTag = appContainer.languageStore.getLanguageTag()
                    val newTag = selectedLanguage.tag
                    appContainer.languageStore.setLanguageTag(newTag)
                    appContainer.splashCompleted = true
                    appContainer.languageSelectionCompleted = true

                    val nextRoute = when {
                        appContainer.farmSetupCompleted -> SihRoute.Home
                        appContainer.onboardingCompleted -> SihRoute.FarmSetup
                        else -> SihRoute.Onboarding
                    }

                    navController.navigate(nextRoute) {
                        popUpTo(SihRoute.Language) { inclusive = true }
                    }

                    if (previousTag != newTag) {
                        onLanguageContinue(selectedLanguage)
                    }
                },
            )
        }
        composable(route = SihRoute.Onboarding) {
            OnboardingScreen(
                onGetStarted = {
                    appContainer.onboardingCompleted = true
                    navController.navigate(SihRoute.FarmSetup) {
                        popUpTo(SihRoute.Onboarding) { inclusive = true }
                    }
                },
            )
        }
        composable(route = SihRoute.FarmSetup) {
            val viewModel: FarmSetupViewModel = viewModel(
                factory = FarmSetupViewModel.provideFactory(
                    farmRepository = appContainer.farmRepository,
                    locationRepository = appContainer.locationRepository,
                ),
            )
            FarmSetupScreen(
                viewModel = viewModel,
                onSetupComplete = {
                    Log.d("AgriX_Debug", "9. [SihNavHost] onSetupComplete received. Navigating to FarmSaved route.")
                    appContainer.farmSetupCompleted = true
                    onFarmSetupFinished()
                    navController.navigate(SihRoute.FarmSaved) {
                        popUpTo(SihRoute.FarmSetup) { inclusive = true }
                    }
                },
            )
        }
        composable(route = SihRoute.FarmSaved) {
            FarmSavedScreen(
                onContinue = {
                    Log.d("AgriX_Debug", "11. [FarmSavedScreen] Continue tapped. Navigating to Home.")
                    navController.navigate(SihRoute.Home) {
                        popUpTo(SihRoute.Language) { inclusive = false }
                    }
                },
            )
        }
        composable(route = SihRoute.Home) {
            val viewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.provideFactory(
                    farmRepository = appContainer.farmRepository,
                    bleSensorRepository = appContainer.bleSensorRepository,
                ),
            )
            HomeScreen(
                viewModel = viewModel,
                onConnectSensor = {
                    navController.navigate(SihRoute.SensorConnection)
                },
                onNavigateToAi = {
                    navController.navigate(SihRoute.Ai)
                },
                onEditFarm = {
                    navController.navigate(SihRoute.FarmSetup)
                },
            )
        }
        composable(route = SihRoute.SensorConnection) {
            val viewModel: SensorConnectionViewModel = viewModel(
                factory = SensorConnectionViewModel.provideFactory(
                    bleSensorRepository = appContainer.bleSensorRepository,
                    localSensorEngine = appContainer.localSensorEngine,
                    cloudAiClient = appContainer.cloudAiClient,
                    farmRepository = appContainer.farmRepository,
                    languageStore = appContainer.languageStore,
                ),
            )
            SensorConnectionScreen(
                viewModel = viewModel,
                onBack = {
                    navController.popBackStack()
                },
            )
        }
        composable(route = SihRoute.Ai) {
            val viewModel: AiViewModel = viewModel(
                factory = AiViewModel.provideFactory(
                    farmRepository = appContainer.farmRepository,
                    bleSensorRepository = appContainer.bleSensorRepository,
                    localSensorEngine = appContainer.localSensorEngine,
                ),
            )
            AiScreen(
                viewModel = viewModel,
                onNavigateToHome = {
                    navController.navigate(SihRoute.Home) {
                        popUpTo(SihRoute.Home) { inclusive = true }
                    }
                },
                onNavigateToSoil = {
                    navController.navigate(SihRoute.SensorConnection)
                },
                onNavigateToDiseaseScan = {
                    navController.navigate(SihRoute.DiseaseScan)
                },
                onNavigateToHistory = {
                    navController.navigate(SihRoute.DiagnosisHistory)
                },
                onNavigateToFarm = {
                    navController.navigate(SihRoute.FarmSetup)
                },
            )
        }
        composable(route = SihRoute.DiseaseScan) {
            val viewModel: CropDiseaseScanViewModel = viewModel(
                factory = CropDiseaseScanViewModel.provideFactory(
                    aiEngineRouter = appContainer.aiEngineRouter,
                    classifier = appContainer.localAiEngine.classifier,
                    farmRepository = appContainer.farmRepository,
                    diagnosisRepository = appContainer.diagnosisRepository,
                    advisoryRepository = appContainer.advisoryRepository,
                    languageStore = appContainer.languageStore,
                ),
            )
            CropDiseaseScanScreen(
                viewModel = viewModel,
                onBack = {
                    navController.popBackStack()
                },
            )
        }
        composable(route = SihRoute.DiagnosisHistory) {
            val viewModel: DiagnosisHistoryViewModel = viewModel(
                factory = DiagnosisHistoryViewModel.provideFactory(
                    diagnosisRepository = appContainer.diagnosisRepository,
                ),
            )
            DiagnosisHistoryScreen(
                viewModel = viewModel,
                onBack = {
                    navController.popBackStack()
                },
            )
        }
    }
}
