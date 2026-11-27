// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.share

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/**
 * Sharing a document with another app (F15, ADR 0027): the intent, the chooser around it and what
 * happens when nothing can take the file.
 */
@RunWith(AndroidJUnit4::class)
class SharingTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val uri: Uri = "content://com.example.provider/document/42".toUri()
    private val request = ShareRequest(uri, "Contrat")

    @Suppress("DEPRECATION") // EXTRA_STREAM is typed by the API level; the getter with a class needs API 33.
    private fun Intent.stream(): Uri? = getParcelableExtra(Intent.EXTRA_STREAM)

    @Test
    fun `the intent sends the PDF as it is, with a temporary read grant`() {
        val intent = shareIntent(request)

        assertThat(intent.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(intent.type).isEqualTo("application/pdf")
        assertThat(intent.stream()).isEqualTo(uri)
        assertThat(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .isEqualTo(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertThat(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION).isEqualTo(0)
        assertThat(intent.clipData?.getItemAt(0)?.uri).isEqualTo(uri)
    }

    @Test
    fun `the intent carries nothing but the document`() {
        val intent = shareIntent(request)

        assertThat(intent.extras?.keySet()).containsExactly(Intent.EXTRA_STREAM)
    }

    @Test
    fun `the chooser wraps the intent and has a title`() {
        val chooser = shareChooser(application, request)

        assertThat(chooser.action).isEqualTo(Intent.ACTION_CHOOSER)
        assertThat(chooser.getStringExtra(Intent.EXTRA_TITLE)).isEqualTo("Share document")
        @Suppress("DEPRECATION")
        val target = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertThat(target?.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(target?.stream()).isEqualTo(uri)
    }

    @Test
    fun `the chooser leaves out the receivers of Lutrin itself`() {
        val own = ComponentName(application.packageName, "io.github.myxomatom.lutrin.MainActivity")
        val other = ComponentName("com.example.mail", "com.example.mail.Send")
        val packageManager = shadowOf(application.packageManager)
        listOf(own, other).forEach { component ->
            packageManager.addActivityIfNotPresent(component)
            packageManager.addIntentFilterForActivity(
                component,
                IntentFilter(Intent.ACTION_SEND).apply { addDataType("application/pdf") },
            )
        }

        val chooser = shareChooser(application, request)

        @Suppress("DEPRECATION")
        val excluded = chooser.getParcelableArrayExtra(Intent.EXTRA_EXCLUDE_COMPONENTS)?.toList()
        assertThat(excluded).containsExactly(own)
    }

    @Test
    fun `starting the share opens the chooser`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).create().get()

        val outcome = startSharing(activity, request)

        assertThat(outcome).isEqualTo(ShareOutcome.STARTED)
        assertThat(shadowOf(activity).nextStartedActivity.action).isEqualTo(Intent.ACTION_CHOOSER)
    }

    private class Failing(base: Context, private val error: RuntimeException) : ContextWrapper(base) {
        override fun startActivity(intent: Intent) = throw error
    }

    @Test
    fun `a device without a chooser says that no app can receive the file`() {
        val outcome = startSharing(Failing(application, ActivityNotFoundException()), request)

        assertThat(outcome).isEqualTo(ShareOutcome.NO_APP)
    }

    @Test
    fun `a provider that refuses the grant makes the file not shareable, without a crash`() {
        val outcome = startSharing(Failing(application, SecurityException("not grantable")), request)

        assertThat(outcome).isEqualTo(ShareOutcome.NOT_SHAREABLE)
    }
}
