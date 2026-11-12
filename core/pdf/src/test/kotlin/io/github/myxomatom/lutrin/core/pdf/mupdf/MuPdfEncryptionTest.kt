// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Encrypted documents of the test set (F10, ADR 0017), through the real engine: each encryption
 * method asks for its user password, refuses a wrong one and opens with the right one; a document
 * restricted by its owner only opens without a password. The passwords are documented test data
 * (`core/testing/README.md`), not secrets.
 */
class MuPdfEncryptionTest {
    @Before
    fun requireHostEngine() = assumeHostEngine()

    /** Encrypted test document → its user password. */
    private val userPasswords =
        mapOf(
            "pdfs/encrypted_rc4_128.pdf" to "lutrin-rc4",
            "pdfs/encrypted_aes_128.pdf" to "lutrin-aes128",
            "pdfs/encrypted_password_lutrin.pdf" to "lutrin",
        )

    @Test
    fun `asks for the password of every encryption method`() = runTest {
        userPasswords.keys.forEach { path ->
            val result = engine().open(TestAssets.source(path))

            assertThat(result.exceptionOrNull()).isInstanceOf(PdfError.PasswordRequired::class.java)
        }
    }

    @Test
    fun `refuses a wrong password for every encryption method`() = runTest {
        userPasswords.forEach { (path, password) ->
            val result = engine().open(TestAssets.source(path), password = password.uppercase())

            assertThat(result.exceptionOrNull()).isInstanceOf(PdfError.IncorrectPassword::class.java)
        }
    }

    @Test
    fun `refuses an empty password`() = runTest {
        val result = engine().open(TestAssets.source("pdfs/encrypted_aes_128.pdf"), password = "")

        assertThat(result.exceptionOrNull()).isInstanceOf(PdfError.IncorrectPassword::class.java)
    }

    @Test
    fun `opens every encryption method with its password and reads the text`() = runTest {
        userPasswords.forEach { (path, password) ->
            openTestPdf(path, password).use { document ->
                assertThat(document.metadata.isEncrypted).isTrue()
                assertThat(document.allText()).contains(password)
            }
        }
    }

    @Test
    fun `opens with the owner password as well`() = runTest {
        // The owner password unlocks the document too, by the PDF standard; MuPDF accepts it.
        openTestPdf("pdfs/encrypted_aes_128.pdf", password = "lutrin-aes128-owner").use { document ->
            assertThat(document.pageCount).isEqualTo(1)
        }
    }

    @Test
    fun `opens a document restricted by its owner only without asking`() = runTest {
        openTestPdf("pdfs/encrypted_owner_password_only.pdf").use { document ->
            assertThat(document.metadata.isEncrypted).isTrue()
            assertThat(document.allText()).contains("Document restreint")
        }
    }

    @Test
    fun `ignores a password given for a document that needs none`() = runTest {
        openTestPdf("pdfs/encrypted_owner_password_only.pdf", password = "anything").use { document ->
            assertThat(document.pageCount).isEqualTo(1)
        }
    }

    @Test
    fun `reports that the author forbids copying the text`() = runTest {
        // Its permissions allow accessibility only (core/testing/README.md).
        openTestPdf("pdfs/encrypted_owner_password_only.pdf").use { document ->
            assertThat(document.canCopyText).isFalse()
            // The engine reports the permission and still extracts the text (ADR 0020).
            assertThat(document.allText()).contains("Document restreint")
        }
    }

    @Test
    fun `allows copying the text of documents without restriction`() = runTest {
        openTestPdf("pdfs/simple_1page.pdf").use { assertThat(it.canCopyText).isTrue() }
        userPasswords.forEach { (path, password) ->
            openTestPdf(path, password).use { assertThat(it.canCopyText).isTrue() }
        }
    }
}
