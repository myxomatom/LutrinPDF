// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** The intent filters of the merged manifest: precise, PDF only (ADR 0014). */
@RunWith(AndroidJUnit4::class)
class IntentFiltersTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun handlers(intent: Intent): List<String> = context.packageManager
        .queryIntentActivities(intent.setPackage(context.packageName), 0)
        .map { it.activityInfo.name }

    private fun view(uri: String, type: String) = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), type)

    private fun send(action: String = Intent.ACTION_SEND, type: String) = Intent(action).setType(type)

    @Test
    fun `open with a PDF content URI reaches the main activity`() {
        assertThat(handlers(view(CONTENT_URI, PDF))).containsExactly(MainActivity::class.java.name)
    }

    @Test
    fun `open with a file URI is not offered`() {
        assertThat(handlers(view("file:///sdcard/Download/a.pdf", PDF))).isEmpty()
    }

    @Test
    fun `open with a web link is not offered`() {
        assertThat(handlers(view("https://example.org/a.pdf", PDF))).isEmpty()
    }

    @Test
    fun `open with another type is not offered`() {
        assertThat(handlers(view(CONTENT_URI, "image/png"))).isEmpty()
        assertThat(handlers(view(CONTENT_URI, "application/octet-stream"))).isEmpty()
    }

    @Test
    fun `sharing a PDF reaches the main activity`() {
        assertThat(handlers(send(type = PDF))).containsExactly(MainActivity::class.java.name)
    }

    @Test
    fun `sharing another type is not offered`() {
        assertThat(handlers(send(type = "text/plain"))).isEmpty()
        assertThat(handlers(send(type = "image/jpeg"))).isEmpty()
    }

    @Test
    fun `sharing several PDF is not offered`() {
        assertThat(handlers(send(action = Intent.ACTION_SEND_MULTIPLE, type = PDF))).isEmpty()
    }

    private companion object {
        const val PDF = "application/pdf"
        const val CONTENT_URI = "content://com.example.files/document/1"
    }
}
