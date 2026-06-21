package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.FitPlanAppContent
import com.example.ui.theme.FitPlanTheme
import com.example.ui.viewmodel.FitPlanViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()
        setContent {
            val fitPlanViewModel: FitPlanViewModel = viewModel()
            val navController = rememberNavController()
            val themeSelector by fitPlanViewModel.themeSelector.collectAsStateWithLifecycle()
            val visualPerformanceProfile by fitPlanViewModel.visualPerformanceProfile.collectAsStateWithLifecycle()
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val useDarkTheme = remember(themeSelector, systemDark) {
                when (themeSelector) {
                    "dark" -> true
                    "light" -> false
                    else -> systemDark
                }
            }

            FitPlanTheme(darkTheme = useDarkTheme) {
                val isLowEnd = visualPerformanceProfile.useLightweightCards

                androidx.compose.runtime.CompositionLocalProvider(
                    com.example.ui.screens.LocalIsLowEnd provides isLowEnd,
                    com.example.ui.screens.LocalThemeIsDark provides useDarkTheme
                ) {
                    com.example.ui.screens.ResponsiveBuilder { screenInfo ->
                        Surface(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            FitPlanAppContent(
                                viewModel = fitPlanViewModel,
                                navController = navController
                            )
                        }
                    }
                }
            }
        }
    }
}
