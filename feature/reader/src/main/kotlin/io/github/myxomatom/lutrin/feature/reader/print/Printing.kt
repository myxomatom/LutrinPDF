// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.print

import android.content.Context
import android.print.PrintManager

/**
 * Opens the print dialog of the system for [job] (F15, ADR 0026); the job is named after the
 * document. Called by the reader's route with the `Context` of its activity: the print dialog is
 * an activity started over it.
 *
 * @param context the activity showing the reader.
 * @return `false` when the device has no print service (printing is not part of every Android
 *   build), so that the reader can say so.
 */
internal fun startPrinting(context: Context, job: PrintJob): Boolean {
    val printManager = context.getSystemService(PrintManager::class.java) ?: return false
    val adapter = DocumentPrintAdapter(job, openInput = context.contentResolver::openInputStream)
    printManager.print(job.name, adapter, null)
    return true
}
