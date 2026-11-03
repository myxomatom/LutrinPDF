// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshot references of the theme, in light and dark, at normal and large font sizes. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class ThemeScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, darkTheme: Boolean, fontScale: Float = 1f) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                LutrinTheme(darkTheme = darkTheme, dynamicColor = false) { ThemeSpecimen() }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun light() = capture("theme_light", darkTheme = false)

    @Test
    fun dark() = capture("theme_dark", darkTheme = true)

    @Test
    fun largeFont() = capture("theme_light_font200", darkTheme = false, fontScale = 2f)
}

@Composable
private fun ThemeSpecimen() {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Lutrin", style = MaterialTheme.typography.titleLarge)
            Text("Titre de carte", style = MaterialTheme.typography.titleMedium)
            Text("Corps de texte — éàçù", style = MaterialTheme.typography.bodyLarge)
            Text("Aide, mention", style = MaterialTheme.typography.bodySmall)
            Card { Text("Carte", Modifier.padding(16.dp)) }
            Button(onClick = {}) { Text("Ouvrir un fichier") }
            OutlinedButton(onClick = {}) { Text("Annuler") }
            Switch(checked = true, onCheckedChange = {})
        }
    }
}
