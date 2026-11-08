// SPDX-License-Identifier: GPL-3.0-or-later
package lutrin.convention.mupdf

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.io.OutputStream
import java.nio.file.FileSystems
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Checks that [HexDump] writes exactly what MuPDF's own `scripts/hexdump.sh` writes, on edge
 * cases and on every resource that `make generate` embeds.
 *
 * Needs `bash`, `od` and `sed`, and the MuPDF submodule: elsewhere (Windows without Git Bash,
 * submodule not fetched) the tests are skipped with a message, except on a CI machine
 * (`CI=true`), where they fail instead so that the comparison cannot silently stop running.
 */
class HexDumpScriptComparisonTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val mupdfDir = File(System.getProperty("lutrin.mupdfDir").orEmpty())
    private val script = mupdfDir.resolve("scripts/hexdump.sh")

    @Before
    fun requireScript() {
        val problem = when {
            !script.isFile -> "MuPDF submodule not found at $mupdfDir (run ./scripts/init-mupdf.sh)"
            !scriptRuns() -> "bash, od or sed cannot run scripts/hexdump.sh on this machine"
            else -> null
        }
        if (problem != null && System.getenv("CI") == "true") {
            throw AssertionError("hexdump.sh comparison must run in CI: $problem")
        }
        assumeTrue("Skipping the hexdump.sh comparison: $problem", problem == null)
    }

    @Test
    fun `matches hexdump sh on edge case inputs`() {
        listOf(0, 1, 15, 16, 17, 256, 1000).forEach { size ->
            val file = folder.newFile("edge-$size.bin").apply { writeBytes(ByteArray(size) { (it * 37).toByte() }) }

            assertWithMessage("size $size").that(kotlinDigest(file)).isEqualTo(scriptDigest(file))
        }
    }

    @Test
    fun `matches hexdump sh on every embedded MuPDF resource`() {
        val resources = embeddedResources()
        assertWithMessage("embedded resources found in $mupdfDir").that(resources).isNotEmpty()

        resources.forEach { file ->
            assertWithMessage(file.relativeTo(mupdfDir).path).that(kotlinDigest(file)).isEqualTo(scriptDigest(file))
        }
    }

    private fun embeddedResources(): List<File> {
        val matchers = MUPDF_EMBEDDED_RESOURCES.map { FileSystems.getDefault().getPathMatcher("glob:$it") }
        val root = mupdfDir.resolve("resources")
        return root.walkTopDown()
            .filter { it.isFile }
            .filter { file ->
                val path = FileSystems.getDefault().getPath(file.relativeTo(mupdfDir).invariantSeparatorsPath)
                matchers.any { it.matches(path) }
            }.sorted()
            .toList()
    }

    private fun kotlinDigest(file: File): String = sha256 { output ->
        file.inputStream().buffered().use { HexDump.write(it, HexDump.symbolName(file.name), output) }
    }

    private fun scriptDigest(file: File): String {
        val process = hexdumpProcess(file)
        val digest = sha256 { output -> process.inputStream.use { it.copyTo(output) } }
        check(process.waitFor(SCRIPT_TIMEOUT_MINUTES, TimeUnit.MINUTES) && process.exitValue() == 0) {
            "hexdump.sh failed on $file"
        }
        return digest
    }

    private fun scriptRuns(): Boolean = runCatching {
        val probe = folder.newFile("probe.bin").apply { writeBytes(byteArrayOf(1)) }
        val process = hexdumpProcess(probe)
        val text = process.inputStream.use { it.readBytes() }.toString(Charsets.US_ASCII)
        process.waitFor(SCRIPT_TIMEOUT_MINUTES, TimeUnit.MINUTES) &&
            process.exitValue() == 0 &&
            text.contains("\"\\x01\"")
    }.getOrDefault(false)

    private fun hexdumpProcess(file: File): Process =
        ProcessBuilder(bash, script.invariantSeparatorsPath, file.absoluteFile.invariantSeparatorsPath)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

    private fun sha256(write: (OutputStream) -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-256")
        DigestOutputStream(OutputStream.nullOutputStream(), digest).use { stream ->
            stream.buffered().use(write)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val SCRIPT_TIMEOUT_MINUTES = 5L

        /**
         * `bash` as found in `PATH`. Looked up by hand because on Windows, process creation
         * searches System32 (WSL's `bash.exe`) before `PATH` (Git Bash).
         */
        val bash: String =
            System.getenv("PATH").orEmpty()
                .split(File.pathSeparatorChar)
                .filter { it.isNotBlank() }
                .flatMap { dir -> listOf("bash", "bash.exe").map { File(dir, it) } }
                .firstOrNull { it.isFile && it.canExecute() }
                ?.absolutePath ?: "bash"
    }
}
