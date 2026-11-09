// SPDX-License-Identifier: GPL-3.0-or-later
package lutrin.convention

import java.io.File
import org.gradle.api.Project
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.gradle.process.CommandLineArgumentProvider

/** Directory, under the module's build directory, that holds the unit-test JVMs' temporary files. */
private const val TEST_TEMP_DIR = "tmp/robolectric"

/**
 * Points `java.io.tmpdir` of every unit-test JVM to `build/tmp/robolectric` of the module.
 *
 * Why: Robolectric extracts its native runtime (~200 MB, `robolectric-nativeruntime*`) and its
 * sandbox files (`robolectric-*`) into the system temporary directory for each test JVM. On
 * Windows the extracted DLL is still locked when Robolectric's shutdown hook tries to delete it,
 * so every run left them in `%TEMP%` and two runs filled drive C:. Robolectric has no property
 * of its own for this directory: it calls `Files.createTempDirectory`, which honours
 * `java.io.tmpdir`. Kept in the build directory, these files go away with `clean`, and each test
 * task empties its directory before running so it does not grow from one run to the next.
 *
 * The path is passed through a [CommandLineArgumentProvider] rather than `systemProperty`, so the
 * absolute path is not a task input (the build cache stays relocatable), and the `doFirst` action
 * only captures a [File]: no `Project` access at execution time (configuration cache).
 */
internal fun Project.configureTestTempDir() {
    val tempDir = layout.buildDirectory.dir(TEST_TEMP_DIR).get().asFile
    tasks.withType<Test>().configureEach {
        jvmArgumentProviders.add(TempDirArgumentProvider(tempDir))
        doFirst {
            tempDir.deleteRecursively()
            tempDir.mkdirs()
        }
    }
}

/**
 * Supplies `-Djava.io.tmpdir=<[tempDir]>` to a test JVM.
 *
 * @property tempDir the directory the test JVM uses for its temporary files.
 */
internal class TempDirArgumentProvider(@get:Internal val tempDir: File) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> = listOf("-Djava.io.tmpdir=${tempDir.absolutePath}")
}
