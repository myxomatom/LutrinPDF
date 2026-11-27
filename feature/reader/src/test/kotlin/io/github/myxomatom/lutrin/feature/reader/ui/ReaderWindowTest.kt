// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import android.content.pm.ActivityInfo
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What the reader asks of its window: the orientation lock (F17) and the screen kept on (ADR 0029). */
@RunWith(AndroidJUnit4::class)
class ReaderWindowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var readerShown by mutableStateOf(true)
    private var lock by mutableStateOf(false)
    private var keepOn by mutableStateOf(false)
    private var contentView: View? = null

    private fun show() {
        composeRule.setContent {
            contentView = LocalView.current
            if (readerShown) {
                OrientationLock(lock)
                KeepScreenOn(keepOn)
            }
        }
        composeRule.waitForIdle()
    }

    private val requested: Int get() = composeRule.activity.requestedOrientation

    @Test
    fun `the orientation is requested as the current one only when locked`() {
        assertThat(requestedOrientation(lock = true, free = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED))
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_LOCKED)
        assertThat(requestedOrientation(lock = false, free = ActivityInfo.SCREEN_ORIENTATION_USER))
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_USER)
    }

    @Test
    fun `the orientation is free while the lock is off`() {
        show()

        assertThat(requested).isEqualTo(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
    }

    @Test
    fun `turning the lock on keeps the current orientation and turning it off frees it`() {
        show()

        lock = true
        composeRule.waitForIdle()
        assertThat(requested).isEqualTo(ActivityInfo.SCREEN_ORIENTATION_LOCKED)

        lock = false
        composeRule.waitForIdle()
        assertThat(requested).isEqualTo(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
    }

    @Test
    fun `leaving the reader gives the orientation back`() {
        lock = true
        show()
        assertThat(requested).isEqualTo(ActivityInfo.SCREEN_ORIENTATION_LOCKED)

        readerShown = false
        composeRule.waitForIdle()

        assertThat(requested).isEqualTo(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
    }

    @Test
    fun `the lock gives back the request the activity had before`() {
        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
        lock = true
        show()
        assertThat(requested).isEqualTo(ActivityInfo.SCREEN_ORIENTATION_LOCKED)

        readerShown = false
        composeRule.waitForIdle()

        assertThat(requested).isEqualTo(ActivityInfo.SCREEN_ORIENTATION_USER)
    }

    @Test
    fun `the screen may sleep by default`() {
        show()

        assertThat(contentView?.keepScreenOn).isFalse()
    }

    @Test
    fun `the screen stays on while the setting is on and the reader is shown`() {
        keepOn = true
        show()
        assertThat(contentView?.keepScreenOn).isTrue()

        keepOn = false
        composeRule.waitForIdle()
        assertThat(contentView?.keepScreenOn).isFalse()
    }

    @Test
    fun `leaving the reader lets the screen sleep again`() {
        keepOn = true
        show()
        assertThat(contentView?.keepScreenOn).isTrue()

        readerShown = false
        composeRule.waitForIdle()

        assertThat(contentView?.keepScreenOn).isFalse()
    }
}
