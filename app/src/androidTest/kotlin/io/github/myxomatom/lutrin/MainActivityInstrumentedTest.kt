// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end smoke test on a device or emulator, with the Accessibility Test Framework checks. */
@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun enableChecks() {
        composeRule.enableAccessibilityChecks()
    }

    @Test
    fun welcomeScreenIsDisplayedAndAccessible() {
        composeRule.onNodeWithText("Lutrin").assertIsDisplayed()
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }
}
