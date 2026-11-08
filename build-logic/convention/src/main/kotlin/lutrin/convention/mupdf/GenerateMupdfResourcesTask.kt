// SPDX-License-Identifier: GPL-3.0-or-later
package lutrin.convention.mupdf

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileType
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.work.ChangeType
import org.gradle.work.DisableCachingByDefault
import org.gradle.work.InputChanges

/**
 * Generates the C sources of the resources embedded in MuPDF (fonts, hyphenation patterns),
 * like `make generate` but without `make`, `bash`, `od` or `sed` (ADR 0013).
 *
 * Each file of [resources] gives `<outputDirectory>/<path relative to sourceDirectory>.c`,
 * written by [HexDump]. The task is incremental: only added or modified resources are
 * dumped again, and the source of a removed resource is deleted.
 */
@DisableCachingByDefault(because = "About 200 MB of C sources, faster to regenerate than to store in the build cache")
abstract class GenerateMupdfResourcesTask : DefaultTask() {
    /** Root of the MuPDF source tree; the generated paths are relative to it. */
    @get:Internal
    abstract val sourceDirectory: DirectoryProperty

    /**
     * Binary resources to embed, all inside [sourceDirectory]. Tracked incrementally:
     * Gradle treats @SkipWhenEmpty inputs as @Incremental ones.
     */
    @get:SkipWhenEmpty
    @get:IgnoreEmptyDirectories
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resources: ConfigurableFileCollection

    /** Directory receiving the C sources, `generated/` of the MuPDF source tree. */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    /** Dumps the added and modified resources, deletes the sources of removed ones. */
    @TaskAction
    fun generate(changes: InputChanges) {
        val root = sourceDirectory.get().asFile
        val output = outputDirectory.get().asFile
        changes.getFileChanges(resources).forEach { change ->
            if (change.fileType == FileType.DIRECTORY) return@forEach
            val target = output.resolve(generatedSourcePath(relativePath(change.file, root)))
            if (change.changeType == ChangeType.REMOVED) {
                target.delete()
            } else {
                dump(change.file, target)
            }
        }
    }

    private fun relativePath(file: File, root: File): String {
        val path = file.relativeTo(root).invariantSeparatorsPath
        require(!path.startsWith("..")) { "Resource $file is outside $root" }
        return path
    }

    private fun dump(resource: File, target: File) {
        target.parentFile.mkdirs()
        resource.inputStream().buffered().use { input ->
            target.outputStream().buffered().use { output ->
                HexDump.write(input, HexDump.symbolName(resource.name), output)
            }
        }
    }
}
