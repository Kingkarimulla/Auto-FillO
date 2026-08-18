package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.MainContainerScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.FormFillProTheme
import com.example.ui.viewmodel.FormFillViewModel

enum class AppStep {
    SPLASH,
    ONBOARDING,
    AUTH,
    MAIN
}

class MainActivity : ComponentActivity() {

    private val viewModel: FormFillViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FormFillProTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentStep by remember { mutableStateOf(AppStep.SPLASH) }
                    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

                    when (currentStep) {
                        AppStep.SPLASH -> {
                            SplashScreen(
                                onNavigateNext = {
                                    currentStep = AppStep.ONBOARDING
                                }
                            )
                        }
                        AppStep.ONBOARDING -> {
                            OnboardingScreen(
                                onFinishOnboarding = {
                                    currentStep = if (isLoggedIn) AppStep.MAIN else AppStep.AUTH
                                }
                            )
                        }
                        AppStep.AUTH -> {
                            AuthScreen(
                                viewModel = viewModel,
                                onAuthSuccess = {
                                    currentStep = AppStep.MAIN
                                }
                            )
                        }
                        AppStep.MAIN -> {
                            MainContainerScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

