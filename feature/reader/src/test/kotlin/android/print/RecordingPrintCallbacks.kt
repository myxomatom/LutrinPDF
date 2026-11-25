// SPDX-License-Identifier: GPL-3.0-or-later
// In the package of the framework: the constructors of the print callbacks are hidden from apps.
package android.print

/**
 * Records the answers of a [PrintDocumentAdapter] to a layout request, in order.
 */
class RecordingLayoutCallback : PrintDocumentAdapter.LayoutResultCallback() {
    /** Every answer: « finished », « failed » or « cancelled ». */
    val answers: MutableList<String> = mutableListOf()

    /** The document described by the last finished layout. */
    var info: PrintDocumentInfo? = null
        private set

    /** Whether the last finished layout changed the content. */
    var changed: Boolean? = null
        private set

    override fun onLayoutFinished(info: PrintDocumentInfo, changed: Boolean) {
        answers += "finished"
        this.info = info
        this.changed = changed
    }

    override fun onLayoutFailed(error: CharSequence?) {
        answers += "failed"
    }

    override fun onLayoutCancelled() {
        answers += "cancelled"
    }
}

/**
 * Records the answers of a [PrintDocumentAdapter] to a write request, in order.
 */
class RecordingWriteCallback : PrintDocumentAdapter.WriteResultCallback() {
    /** Every answer: « finished », « failed » or « cancelled ». */
    val answers: MutableList<String> = mutableListOf()

    /** The pages reported by the last finished write. */
    var pages: List<PageRange>? = null
        private set

    /** The message of the last failed write. */
    var error: CharSequence? = null
        private set

    override fun onWriteFinished(pages: Array<out PageRange>) {
        answers += "finished"
        this.pages = pages.toList()
    }

    override fun onWriteFailed(error: CharSequence?) {
        answers += "failed"
        this.error = error
    }

    override fun onWriteCancelled() {
        answers += "cancelled"
    }
}
