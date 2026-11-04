// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.myxomatom.lutrin.core.domain.settings.AppSettings
import io.github.myxomatom.lutrin.core.domain.settings.ObserveSettingsUseCase
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import io.github.myxomatom.lutrin.ui.LutrinApp
import javax.inject.Inject

/** Single activity of the application; every screen is a Compose destination inside it. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** The app-wide preferences; the theme follows them at once. */
    @Inject
    lateinit var observeSettings: ObserveSettingsUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // The defaults match what is stored on a fresh install: no colour change at start-up.
            val settings by observeSettings().collectAsStateWithLifecycle(initialValue = AppSettings())
            LutrinTheme(dynamicColor = settings.dynamicColor) {
                LutrinApp()
            }
        }
    }
}
