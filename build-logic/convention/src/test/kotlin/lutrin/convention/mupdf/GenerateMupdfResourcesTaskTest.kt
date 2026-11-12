// SPDX-License-Identifier: GPL-3.0-or-later
package lutrin.convention.mupdf

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.io.File
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Runs `generateMupdfResources` of the `lutrin.mupdf` plugin on a small fake MuPDF tree (Gradle TestKit). */
class GenerateMupdfResourcesTaskTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var project: File
    private val mupdf get() = project.resolve("libmupdf")

    @Before
    fun createProject() {
        project = folder.newFolder("fixture")
        project.resolve("settings.gradle.kts").writeText("rootProject.name = \"fixture\"\n")
        project.resolve("build.gradle.kts").writeText("plugins { id(\"lutrin.mupdf\") }\n")
    }

    private fun resource(path: String, vararg bytes: Int) = mupdf.resolve(path).apply {
        parentFile.mkdirs()
        writeBytes(ByteArray(bytes.size) { bytes[it].toByte() })
    }

    private fun generated(path: String) = mupdf.resolve("generated/$path.c")

    private fun expectedDump(file: File): ByteArray {
        val output = ByteArrayOutputStream()
        HexDump.write(file.inputStream(), HexDump.symbolName(file.name), output)
        return output.toByteArray()
    }

    private fun generate(): BuildResult = GradleRunner.create()
        .withProjectDir(project)
        .withPluginClasspath()
        .withArguments("generateMupdfResources", "--configuration-cache", "--stacktrace")
        .build()

    @Test
    fun `generates a C source for each embedded resource only`() {
        val font = resource("resources/fonts/urw/Dingbats.cff", 1, 2, 3)
        val patterns = resource("resources/hyphen/hyph-all.zip", 0xff)
        resource("resources/fonts/urw/input", 9)
        resource("resources/hyphen/hyph-std.zip", 9)

        val result = generate()

        assertThat(result.task(":generateMupdfResources")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(generated("resources/fonts/urw/Dingbats.cff").readBytes()).isEqualTo(expectedDump(font))
        assertThat(generated("resources/hyphen/hyph-all.zip").readBytes()).isEqualTo(expectedDump(patterns))
        assertThat(generated("resources/fonts/urw/input").exists()).isFalse()
        assertThat(generated("resources/hyphen/hyph-std.zip").exists()).isFalse()
    }

    @Test
    fun `is up to date when nothing changed`() {
        resource("resources/fonts/urw/Dingbats.cff", 1)
        generate()

        val result = generate()

        assertThat(result.task(":generateMupdfResources")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
    }

    @Test
    fun `does not generate the fallback fonts left out of the Android build`() {
        val kept = resource("resources/fonts/urw/Dingbats.cff", 7)
        resource("resources/fonts/droid/DroidSansFallback.ttf", 8)
        resource("resources/fonts/han/SourceHanSerif-Regular.ttc", 9)
        resource("resources/fonts/noto/NotoSans-Regular.otf", 10)
        resource("resources/fonts/sil/CharisSIL.cff", 11)

        generate()

        assertThat(generated("resources/fonts/urw/Dingbats.cff").readBytes()).isEqualTo(expectedDump(kept))
        listOf(
            "resources/fonts/droid/DroidSansFallback.ttf",
            "resources/fonts/han/SourceHanSerif-Regular.ttc",
            "resources/fonts/noto/NotoSans-Regular.otf",
            "resources/fonts/sil/CharisSIL.cff",
        ).forEach { assertThat(generated(it).exists()).isFalse() }
    }

    @Test
    fun `regenerates changed resources and deletes the sources of removed ones`() {
        val kept = resource("resources/fonts/urw/Dingbats.cff", 1)
        resource("resources/fonts/urw/Old.cff", 2)
        generate()

        kept.writeBytes(byteArrayOf(4, 5))
        mupdf.resolve("resources/fonts/urw/Old.cff").delete()
        val added = resource("resources/fonts/urw/New-Regular.cff", 6)
        generate()

        assertThat(generated("resources/fonts/urw/Dingbats.cff").readBytes()).isEqualTo(expectedDump(kept))
        assertThat(generated("resources/fonts/urw/New-Regular.cff").readBytes()).isEqualTo(expectedDump(added))
        assertThat(generated("resources/fonts/urw/Old.cff").exists()).isFalse()
    }

    @Test
    fun `is skipped when no resource exists`() {
        mupdf.mkdirs()

        val result = generate()

        assertThat(result.task(":generateMupdfResources")?.outcome).isEqualTo(TaskOutcome.NO_SOURCE)
    }
}
