// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.document.IncomingIntent
import org.junit.Test
import org.junit.runner.RunWith

/** Copy of the Android intent fields checked by the domain (Robolectric for `Intent` and `Uri`). */
@RunWith(AndroidJUnit4::class)
class IncomingIntentsTest {
    private val uri = Uri.parse("content://com.example.files/document/1")

    @Test
    fun `copies a view intent`() {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").putExtra(EXTRA_PAGE, 12)

        assertThat(intent.toIncomingIntent(PACKAGE))
            .isEqualTo(
                IncomingIntent(
                    action = IncomingIntent.ACTION_VIEW,
                    data = uri.toString(),
                    type = "application/pdf",
                    page = 12,
                    receiverPackage = PACKAGE,
                ),
            )
    }

    @Test
    fun `copies the stream of a send intent`() {
        val intent = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)

        val incoming = intent.toIncomingIntent(PACKAGE)

        assertThat(incoming.action).isEqualTo(IncomingIntent.ACTION_SEND)
        assertThat(incoming.stream).isEqualTo(uri.toString())
        assertThat(incoming.data).isNull()
    }

    @Test
    fun `a stream that is not a URI is ignored`() {
        val intent = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri.toString())

        assertThat(intent.toIncomingIntent(PACKAGE).stream).isNull()
    }

    @Test
    fun `missing extras and data are null`() {
        val incoming = Intent(Intent.ACTION_VIEW).toIncomingIntent(PACKAGE)

        assertThat(incoming).isEqualTo(IncomingIntent(IncomingIntent.ACTION_VIEW, receiverPackage = PACKAGE))
    }

    @Test
    fun `a page of another type than Int is ignored`() {
        listOf(
            Intent(Intent.ACTION_VIEW, uri).putExtra(EXTRA_PAGE, "12"),
            Intent(Intent.ACTION_VIEW, uri).putExtra(EXTRA_PAGE, 12L),
            Intent(Intent.ACTION_VIEW, uri).putExtra(EXTRA_PAGE, 12.0),
        ).forEach { intent ->
            assertThat(intent.toIncomingIntent(PACKAGE).page).isNull()
        }
    }

    @Test
    fun `zero and negative pages are passed on for the domain to ignore`() {
        assertThat(Intent(Intent.ACTION_VIEW, uri).putExtra(EXTRA_PAGE, 0).toIncomingIntent(PACKAGE).page).isEqualTo(0)
        assertThat(
            Intent(Intent.ACTION_VIEW, uri).putExtra(EXTRA_PAGE, -2).toIncomingIntent(PACKAGE).page,
        ).isEqualTo(-2)
    }

    private companion object {
        const val PACKAGE = "io.github.myxomatom.lutrin"
    }
}
