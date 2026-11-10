// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.ui.transition

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DocumentTransitionPolicyTest {
    @Test
    fun `animations run at any positive scale`() {
        assertThat(DocumentTransitionPolicy.animationsEnabled(1f)).isTrue()
        assertThat(DocumentTransitionPolicy.animationsEnabled(0.5f)).isTrue()
        assertThat(DocumentTransitionPolicy.animationsEnabled(10f)).isTrue()
    }

    @Test
    fun `a scale of zero means animations removed`() {
        assertThat(DocumentTransitionPolicy.animationsEnabled(0f)).isFalse()
    }

    @Test
    fun `the tapped document is linked while animations run`() {
        assertThat(DocumentTransitionPolicy.linkedDocument("a", animationsEnabled = true)).isEqualTo("a")
    }

    @Test
    fun `no document is linked when animations are removed`() {
        assertThat(DocumentTransitionPolicy.linkedDocument("a", animationsEnabled = false)).isNull()
    }

    @Test
    fun `no document is linked when the reader was opened another way`() {
        assertThat(DocumentTransitionPolicy.linkedDocument(null, animationsEnabled = true)).isNull()
    }

    @Test
    fun `only the linked document takes part`() {
        assertThat(DocumentTransitionPolicy.isLinked("a", "a")).isTrue()
        assertThat(DocumentTransitionPolicy.isLinked("a", "b")).isFalse()
        assertThat(DocumentTransitionPolicy.isLinked(null, "a")).isFalse()
    }

    @Test
    fun `an element without document never takes part`() {
        assertThat(DocumentTransitionPolicy.isLinked("a", null)).isFalse()
        assertThat(DocumentTransitionPolicy.isLinked(null, null)).isFalse()
    }

    @Test
    fun `both sides build the same key for one document and different keys for two`() {
        assertThat(DocumentPageKey("a")).isEqualTo(DocumentPageKey("a"))
        assertThat(DocumentPageKey("a").hashCode()).isEqualTo(DocumentPageKey("a").hashCode())
        assertThat(DocumentPageKey("a")).isNotEqualTo(DocumentPageKey("b"))
    }
}
