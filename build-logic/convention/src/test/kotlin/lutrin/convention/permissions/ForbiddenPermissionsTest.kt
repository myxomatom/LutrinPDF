// SPDX-License-Identifier: GPL-3.0-or-later
package lutrin.convention.permissions

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.xml.sax.SAXException

class ForbiddenPermissionsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun manifest(body: String) = folder.newFile().apply {
        writeText(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test">
                $body
                </manifest>
            """.trimIndent(),
        )
    }

    @Test
    fun `returns empty list when manifest has no permission`() {
        assertThat(findForbiddenPermissions(manifest("<application />"))).isEmpty()
    }

    @Test
    fun `ignores allowed permissions`() {
        val file = manifest("""<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />""")

        assertThat(findForbiddenPermissions(file)).isEmpty()
    }

    @Test
    fun `finds INTERNET and network state permissions`() {
        val file =
            manifest(
                """
                <uses-permission android:name="android.permission.INTERNET" />
                <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
                """,
            )

        assertThat(findForbiddenPermissions(file))
            .containsExactly("android.permission.ACCESS_NETWORK_STATE", "android.permission.INTERNET")
            .inOrder()
    }

    @Test
    fun `finds permissions declared with uses-permission-sdk-23`() {
        val file = manifest("""<uses-permission-sdk-23 android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />""")

        assertThat(findForbiddenPermissions(file)).containsExactly("android.permission.MANAGE_EXTERNAL_STORAGE")
    }

    @Test
    fun `reports a permission declared twice only once`() {
        val permission = """<uses-permission android:name="android.permission.INTERNET" />"""

        assertThat(findForbiddenPermissions(manifest(permission + permission)))
            .containsExactly("android.permission.INTERNET")
    }

    @Test
    fun `ignores a name attribute outside the android namespace`() {
        val file = manifest("""<uses-permission name="android.permission.INTERNET" />""")

        assertThat(findForbiddenPermissions(file)).isEmpty()
    }

    @Test
    fun `throws on malformed manifest`() {
        val file = folder.newFile().apply { writeText("<manifest>") }

        assertThrows(SAXException::class.java) { findForbiddenPermissions(file) }
    }

    @Test
    fun `rejects manifests with a doctype`() {
        val file =
            folder.newFile().apply {
                writeText("""<?xml version="1.0"?><!DOCTYPE manifest [<!ENTITY x "y">]><manifest/>""")
            }

        assertThrows(SAXException::class.java) { findForbiddenPermissions(file) }
    }
}
