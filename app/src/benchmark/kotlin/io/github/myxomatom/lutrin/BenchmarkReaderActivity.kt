// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import io.github.myxomatom.lutrin.core.domain.dispatchers.IoDispatcher
import io.github.myxomatom.lutrin.core.domain.settings.AppSettings
import io.github.myxomatom.lutrin.core.domain.settings.ObserveSettingsUseCase
import io.github.myxomatom.lutrin.core.domain.settings.ScrollMode
import io.github.myxomatom.lutrin.core.domain.settings.SetScrollModeUseCase
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import io.github.myxomatom.lutrin.core.ui.transition.DocumentTransitionLayout
import io.github.myxomatom.lutrin.feature.reader.navigateToReader
import io.github.myxomatom.lutrin.feature.reader.readerScreen
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Entry point of the benchmark builds only (ADR 0012): opens the large test document in the
 * reader, without going through the system file picker, which a benchmark cannot drive.
 *
 * It never reads a URI or a path from its caller: the document is bundled in the benchmark
 * APK's assets and copied once into [getCacheDir]. The only input is the optional
 * [EXTRA_SCROLL_MODE], checked against [ScrollMode]. This file is compiled into the
 * `benchmarkRelease` and `nonMinifiedRelease` builds, never into `release`.
 */
@AndroidEntryPoint
class BenchmarkReaderActivity : ComponentActivity() {
    /** The app-wide preferences, for the theme, as in [MainActivity]. */
    @Inject
    lateinit var observeSettings: ObserveSettingsUseCase

    /** Stores the scroll mode asked by the benchmark before the reader reads it. */
    @Inject
    lateinit var setScrollMode: SetScrollModeUseCase

    /** Where the bundled document is copied. */
    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            requestedScrollMode()?.let { setScrollMode(it) }
            val document = withContext(ioDispatcher) { bundledDocument() }
            setContent {
                val settings by observeSettings().collectAsStateWithLifecycle(initialValue = AppSettings())
                LutrinTheme(dynamicColor = settings.dynamicColor) {
                    BenchmarkReader(Uri.fromFile(document), onExit = ::finish)
                }
            }
        }
    }

    private fun requestedScrollMode(): ScrollMode? =
        intent.getStringExtra(EXTRA_SCROLL_MODE)?.let { name -> ScrollMode.entries.firstOrNull { it.name == name } }

    /** The bundled test document, copied into the cache on the first launch only. */
    private fun bundledDocument(): File {
        val file = File(cacheDir, DOCUMENT_ASSET)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            val partial = File(file.path + PARTIAL_SUFFIX)
            assets.open(DOCUMENT_ASSET).use { input -> partial.outputStream().use { input.copyTo(it) } }
            check(partial.renameTo(file)) { "Cannot move the benchmark document into place" }
        }
        return file
    }

    /** Names shared with the `:benchmark` module, which cannot depend on this one. */
    companion object {
        /** Optional extra: name of the [ScrollMode] to store before opening the document. */
        const val EXTRA_SCROLL_MODE = "scrollMode"

        /** Asset path of the large test document (`TestFiles.Pdf.LARGE` in `core:testing`). */
        const val DOCUMENT_ASSET = "pdfs/large_600pages.pdf"

        private const val PARTIAL_SUFFIX = ".part"
    }
}

/** Route of the empty first destination, which only forwards to the reader. */
private const val START_ROUTE = "start"

/**
 * Navigation graph of the benchmark entry point: the reader of the public `feature:reader`
 * API, opened at once on [document]. Leaving the reader closes the activity. Laid out in the
 * same `DocumentTransitionLayout` as the app's navigation (ADR 0019), without linked document,
 * so that the measures include its cost.
 */
@Composable
private fun BenchmarkReader(document: Uri, onExit: () -> Unit) {
    val navController = rememberNavController()
    val exit by rememberUpdatedState(onExit)
    var opened by rememberSaveable { mutableStateOf(false) }
    DocumentTransitionLayout(linkedDocumentId = null) {
        NavHost(navController, startDestination = START_ROUTE) {
            composable(START_ROUTE) {
                LaunchedEffect(Unit) {
                    // Back from the reader returns here: close instead of opening the document again.
                    if (opened) {
                        exit()
                    } else {
                        opened = true
                        // Always the first page: resuming the page saved by the previous iteration
                        // would make every measurement start elsewhere (ADR 0015).
                        navController.navigateToReader(document, page = 0)
                    }
                }
            }
            readerScreen(onBack = onExit)
        }
    }
}
