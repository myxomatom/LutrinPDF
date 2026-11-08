// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.myxomatom.lutrin.core.domain.document.IncomingDocumentResult
import io.github.myxomatom.lutrin.core.domain.document.IncomingIntent
import io.github.myxomatom.lutrin.core.domain.document.ParseIncomingDocumentUseCase
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State of [MainActivity]: the document request received from another app (F01) that the
 * navigation has not handled yet (ADR 0014). It survives configuration changes, so that a
 * request is handled exactly once even if the activity is recreated before handling it.
 *
 * @param parseIncomingDocument checks the intent and extracts the document to open.
 */
@HiltViewModel
class MainViewModel @Inject constructor(private val parseIncomingDocument: ParseIncomingDocumentUseCase) :
    ViewModel() {
    private val pending = MutableStateFlow<IncomingDocumentResult?>(null)
    private var parsing: Job? = null

    /**
     * The request to handle: [IncomingDocumentResult.Accepted] (open the reader) or
     * [IncomingDocumentResult.Rejected] (tell the user); `null` when there is none.
     */
    val incomingRequest: StateFlow<IncomingDocumentResult?> = pending.asStateFlow()

    /**
     * Checks an intent received by the activity. An intent that does not ask for a document (the
     * launcher) changes nothing; a newer intent replaces a request still being checked.
     *
     * @param intent what the other app sent.
     */
    fun onIntent(intent: IncomingIntent) {
        parsing?.cancel()
        parsing =
            viewModelScope.launch {
                val result = parseIncomingDocument(intent)
                if (result != IncomingDocumentResult.NoDocument) pending.value = result
            }
    }

    /**
     * Marks [request] as handled by the navigation. A newer request is kept.
     *
     * @param request the value of [incomingRequest] that was handled.
     */
    fun onIncomingRequestHandled(request: IncomingDocumentResult) {
        pending.compareAndSet(request, null)
    }
}
