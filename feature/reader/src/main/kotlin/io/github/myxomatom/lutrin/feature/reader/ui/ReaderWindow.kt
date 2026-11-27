// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

// What the reader asks of its window while it is shown: the orientation (F17) and the screen kept
// on (ADR 0029). Both are applied by the screen, which knows the activity; the ViewModel only
// holds the settings. Each one is undone when the reader leaves the composition, so that the
// library and the settings behave as before.

/**
 * Orientation the activity is asked for: the current one, kept ([ActivityInfo.SCREEN_ORIENTATION_LOCKED]),
 * rather than a fixed portrait or landscape, so that the user locks what they hold the phone in.
 *
 * @param lock whether the orientation is locked.
 * @param free what to ask for when it is not: the request the activity had before.
 */
internal fun requestedOrientation(lock: Boolean, free: Int): Int =
    if (lock) ActivityInfo.SCREEN_ORIENTATION_LOCKED else free

/**
 * Keeps the orientation the device is in while [enabled] (F17, ADR 0029), instead of following its
 * rotation; the previous request of the activity comes back when the reader leaves the screen or
 * the lock is turned off.
 */
@Composable
internal fun OrientationLock(enabled: Boolean) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(enabled, activity) {
        val previous = activity?.requestedOrientation
        if (activity != null && previous != null && enabled) {
            activity.requestedOrientation = requestedOrientation(lock = true, free = previous)
        }
        onDispose {
            if (activity != null && previous != null && enabled) activity.requestedOrientation = previous
        }
    }
}

/**
 * Keeps the screen on while [enabled] and the reader is shown (ADR 0029): the flag of the view,
 * which the window follows only while the view is attached, and which is cleared when the reader
 * leaves the screen.
 */
@Composable
internal fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        val previous = view.keepScreenOn
        if (enabled) view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
}

/** The activity this context belongs to, through the wrappers of themes and previews; `null` outside one. */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
