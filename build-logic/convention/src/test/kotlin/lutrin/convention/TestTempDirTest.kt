// SPDX-License-Identifier: GPL-3.0-or-later
package lutrin.convention

import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test as TestTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Checks that [configureTestTempDir] keeps the test JVMs' temporary files in the build directory. */
class TestTempDirTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun projectWithTestTask(): Pair<Project, TestTask> {
        val project = ProjectBuilder.builder().withProjectDir(folder.newFolder("module")).build()
        project.pluginManager.apply("java")
        project.configureTestTempDir()
        return project to project.tasks.getByName("test") as TestTask
    }

    private fun expectedTempDir(project: Project) = project.layout.buildDirectory.dir("tmp/robolectric").get().asFile

    @Test
    fun `test JVM temporary directory is under the module build directory`() {
        val (project, task) = projectWithTestTask()

        val arguments = task.jvmArgumentProviders.flatMap { it.asArguments() }

        assertThat(arguments).contains("-Djava.io.tmpdir=${expectedTempDir(project).absolutePath}")
    }

    @Test
    fun `test task empties and recreates the temporary directory before running`() {
        val (project, task) = projectWithTestTask()
        val tempDir = expectedTempDir(project)
        val leftover = tempDir.resolve("robolectric-nativeruntime123/lib.dll")
        leftover.parentFile.mkdirs()
        leftover.writeText("stale")

        task.actions.first().execute(task)

        assertThat(tempDir.isDirectory).isTrue()
        assertThat(tempDir.list()).isEmpty()
    }
}
