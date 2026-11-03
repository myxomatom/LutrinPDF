// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Application entry point; hosts the Hilt dependency graph (ADR 0002). */
@HiltAndroidApp
class LutrinApplication : Application()
