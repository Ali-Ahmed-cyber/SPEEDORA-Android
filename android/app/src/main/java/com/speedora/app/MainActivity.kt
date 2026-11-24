package com.speedora.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.speedora.app.core.designsystem.SpeedoraTheme
import com.speedora.app.ui.SpeedoraNavHost
import com.speedora.app.ui.SpeedoraViewModel

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as SpeedoraApplication

        setContent {
            val viewModel: SpeedoraViewModel = viewModel(
                factory = SpeedoraViewModel.Factory(app)
            )
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val layoutDirection = if (settings.languageCode == "ar") {
                LayoutDirection.Rtl
            } else {
                LayoutDirection.Ltr
            }

            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                SpeedoraTheme(
                    darkTheme = settings.darkTheme,
                    reducedMotion = settings.reducedMotion || settings.batterySavingMode,
                    animationIntensity = settings.animationIntensity
                ) {
                    SpeedoraNavHost(viewModel = viewModel)
                }
            }
        }
    }
}
