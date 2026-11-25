// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.myxomatom.lutrin.core.domain.dispatchers.IoDispatcher
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The page of E4 on screen. */
sealed interface AboutPage {
    /** The main page: version, no data collected, licence, source code, list of licenses. */
    data object Main : AboutPage

    /** The privacy policy, as local text. */
    data object Privacy : AboutPage

    /** The detail and license text of one component.
     *
     * @property componentId [LicensedComponent.id] of the component.
     */
    data class Component(val componentId: String) : AboutPage
}

/** State of the list of third-party licenses. */
sealed interface LicensesState {
    /** The generated file is being read. */
    data object Loading : LicensesState

    /** The file could not be read: the list is replaced by a message. */
    data object Unavailable : LicensesState

    /**
     * The list is ready.
     *
     * @property components the third-party components, sorted by name.
     */
    data class Loaded(val components: List<LicensedComponent>) : LicensesState
}

/**
 * What E4 shows.
 *
 * @property versionName version of the app, or `null` when unknown.
 * @property licenses the list of third-party licenses.
 * @property page the page on screen.
 * @property confirmingSourceLink whether the confirmation before opening [SOURCE_CODE_URL] is shown.
 */
data class AboutUiState(
    val versionName: String? = null,
    val licenses: LicensesState = LicensesState.Loading,
    val page: AboutPage = AboutPage.Main,
    val confirmingSourceLink: Boolean = false,
)

/** One-off effects of E4. */
sealed interface AboutEffect {
    /**
     * Hand the source code address to another app (`ACTION_VIEW`), the user having confirmed.
     *
     * @property url the address to open.
     */
    data class OpenSourceCode(val url: String) : AboutEffect
}

/**
 * State holder of E4 « À propos et licences ». It never reaches the network: the licenses come
 * from a file generated at build time, and the source code link is only handed to another app
 * after the user confirmed, as for the links of a document (ADR 0022, ADR 0030).
 *
 * @param appVersion gives the version of the app.
 * @param licensesSource gives the generated license data.
 * @param ioDispatcher where the file is read and parsed.
 */
@HiltViewModel
class AboutViewModel @Inject constructor(
    appVersion: AppVersionProvider,
    private val licensesSource: LicensesSource,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AboutUiState(versionName = appVersion.versionName()))
    private val effectChannel = Channel<AboutEffect>(Channel.BUFFERED)

    /** Current state of the screen. */
    val uiState: StateFlow<AboutUiState> = mutableState.asStateFlow()

    /** One-off effects, to be collected by the route. */
    val effects: Flow<AboutEffect> = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch { loadLicenses() }
    }

    /** Shows the privacy policy. */
    fun onOpenPrivacy() = showPage(AboutPage.Privacy)

    /** Shows the detail and license text of the component [componentId]. */
    fun onOpenComponent(componentId: String) = showPage(AboutPage.Component(componentId))

    /**
     * Goes back from a sub-page to the main page.
     *
     * @return `false` when the main page was already on screen, so that the caller leaves E4.
     */
    fun onBackFromPage(): Boolean {
        if (mutableState.value.page == AboutPage.Main) return false
        showPage(AboutPage.Main)
        return true
    }

    /** « Code source » was tapped: asks for confirmation. */
    fun onSourceCodeClick() = mutableState.update { it.copy(confirmingSourceLink = true) }

    /** The confirmation was cancelled or dismissed. */
    fun onSourceCodeDismiss() = mutableState.update { it.copy(confirmingSourceLink = false) }

    /** The user confirmed: asks the route to hand [SOURCE_CODE_URL] to another app. */
    fun onSourceCodeConfirm() {
        mutableState.update { it.copy(confirmingSourceLink = false) }
        effectChannel.trySend(AboutEffect.OpenSourceCode(SOURCE_CODE_URL))
    }

    private fun showPage(page: AboutPage) = mutableState.update { it.copy(page = page) }

    private suspend fun loadLicenses() {
        val loaded =
            withContext(ioDispatcher) {
                try {
                    // The list of the app is never empty (MuPDF is always in it): an empty result
                    // is what the parser gives for a file it could not read.
                    parseComponents(licensesSource.readJson())
                        .takeIf { it.isNotEmpty() }
                        ?.let(LicensesState::Loaded)
                        ?: LicensesState.Unavailable
                } catch (expected: IOException) {
                    // Shown as « La liste des licences n'a pas pu être lue. », never the cause.
                    LicensesState.Unavailable
                }
            }
        mutableState.update { it.copy(licenses = loaded) }
    }
}
