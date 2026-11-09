// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.myxomatom.lutrin.core.domain.settings.AppSettings
import io.github.myxomatom.lutrin.core.domain.settings.ObserveSettingsUseCase
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import io.github.myxomatom.lutrin.feature.library.LibraryViewModel
import io.github.myxomatom.lutrin.ui.LutrinApp
import javax.inject.Inject

/**
 * Single activity of the application; every screen is a Compose destination inside it.
 *
 * Besides the launcher, it receives the documents of other apps (F01, ADR 0014): `ACTION_VIEW`
 * (« Ouvrir avec ») and `ACTION_SEND` (« Partager ») with a PDF. It keeps the standard launch
 * mode, so that a document opened from another app shows in that app's task and back returns
 * to it; an intent delivered to an existing instance ([onNewIntent]) is handled the same way.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** The app-wide preferences; the theme follows them at once. */
    @Inject
    lateinit var observeSettings: ObserveSettingsUseCase

    private val viewModel: MainViewModel by viewModels()

    // Scoped to the activity rather than to the library destination, so that LutrinApp stays a
    // plain composable that tests can show without Hilt (ADR 0015).
    private val libraryViewModel: LibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A recreated activity (rotation, process death) already handled its intent: the restored
        // navigation shows its document again, without opening it a second time.
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            // The defaults match what is stored on a fresh install: no colour change at start-up.
            val settings by observeSettings().collectAsStateWithLifecycle(initialValue = AppSettings())
            val incomingRequest by viewModel.incomingRequest.collectAsStateWithLifecycle()
            LutrinTheme(dynamicColor = settings.dynamicColor) {
                LutrinApp(
                    libraryState = libraryViewModel.uiState,
                    loadThumbnail = libraryViewModel::loadThumbnail,
                    onRefreshThumbnail = libraryViewModel::onThumbnailOutdated,
                    pendingRemoval = libraryViewModel.pendingRemoval,
                    onRemoveRecent = libraryViewModel::onRemove,
                    onUndoRemoval = libraryViewModel::onUndoRemoval,
                    onConfirmRemoval = libraryViewModel::onConfirmRemoval,
                    incomingRequest = incomingRequest,
                    onConsumeIncomingRequest = viewModel::onIncomingRequestHandled,
                    onExit = ::finish,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        // Reopened from Recents: the user did not ask again, and the temporary grant of the
        // original intent may be gone. Start on the library instead.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        viewModel.onIntent(intent.toIncomingIntent(packageName))
    }
}
