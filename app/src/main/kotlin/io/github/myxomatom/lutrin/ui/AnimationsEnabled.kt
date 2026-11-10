// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.ui

import android.content.ContentResolver
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.myxomatom.lutrin.core.ui.transition.DocumentTransitionPolicy

/**
 * Whether the system plays animations ([DocumentTransitionPolicy.animationsEnabled]): read when
 * the screen is first shown and every time the app comes back to the foreground, so that turning
 * « Supprimer les animations » on or off in the system settings applies on return.
 */
@Composable
internal fun rememberAnimationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var enabled by remember(resolver) { mutableStateOf(resolver.animationsEnabled()) }
    LifecycleResumeEffect(resolver) {
        enabled = resolver.animationsEnabled()
        onPauseOrDispose {}
    }
    return enabled
}

/** Reads `ANIMATOR_DURATION_SCALE`, 1 when the setting was never changed. */
private fun ContentResolver.animationsEnabled(): Boolean = DocumentTransitionPolicy.animationsEnabled(
    Settings.Global.getFloat(this, Settings.Global.ANIMATOR_DURATION_SCALE, DEFAULT_ANIMATOR_DURATION_SCALE),
)

/** Animator duration scale of a device whose setting was never changed. */
private const val DEFAULT_ANIMATOR_DURATION_SCALE = 1f
