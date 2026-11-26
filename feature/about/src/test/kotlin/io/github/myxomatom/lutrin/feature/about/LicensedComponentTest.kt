// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.about

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** The AboutLibraries parser logs through `android.util.Log`: Robolectric provides it. */
@RunWith(AndroidJUnit4::class)
class LicensedComponentTest {
    @Test
    fun `components are sorted by name regardless of case and of the order of the file`() {
        val names = parseComponents(SAMPLE_JSON).map { it.name }

        assertThat(names).containsExactly("alpha", "MuPDF", "Zeta").inOrder()
    }

    @Test
    fun `a duplicate identifier keeps the first component only`() {
        val zeta = parseComponents(SAMPLE_JSON).filter { it.id == "org.example:zeta" }

        assertThat(zeta).hasSize(1)
        assertThat(zeta.single().name).isEqualTo("Zeta")
    }

    @Test
    fun `details are mapped, with duplicate authors merged`() {
        val zeta = parseComponents(SAMPLE_JSON).single { it.name == "Zeta" }

        assertThat(zeta.version).isEqualTo("2.0")
        assertThat(zeta.description).isEqualTo("The last library.")
        assertThat(zeta.website).isEqualTo("https://example.org/zeta")
        assertThat(zeta.authors).containsExactly("Ada", "Grace").inOrder()
    }

    @Test
    fun `missing details are null and a blank license text is no text`() {
        val alpha = parseComponents(SAMPLE_JSON).single { it.name == "alpha" }

        assertThat(alpha.version).isNull()
        assertThat(alpha.licenses.map { it.name }).containsExactly("Apache-2.0", "MIT")
        assertThat(alpha.licenses.single { it.name == "MIT" }.text).isNull()
        assertThat(alpha.licenses.single { it.name == "Apache-2.0" }.text).isEqualTo("Apache licence text")
    }

    @Test
    fun `license names are summarised on one line`() {
        val alpha = parseComponents(SAMPLE_JSON).single { it.name == "alpha" }

        assertThat(alpha.licenseSummary).contains("Apache-2.0")
        assertThat(alpha.licenseSummary).contains("MIT")
    }

    @Test
    fun `an empty document gives an empty list`() {
        assertThat(parseComponents("""{"libraries": [], "licenses": {}}""")).isEmpty()
    }

    @Test
    fun `a document that is not json gives no component`() {
        assertThat(parseComponents("not json")).isEmpty()
    }

    @Test
    fun `a json document of the wrong shape gives no component`() {
        assertThat(parseComponents("""{"libraries": 3}""")).isEmpty()
    }
}
