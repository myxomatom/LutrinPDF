// SPDX-License-Identifier: GPL-3.0-or-later
import lutrin.convention.mupdf.GenerateMupdfResourcesTask
import lutrin.convention.mupdf.MUPDF_EMBEDDED_RESOURCES
import lutrin.convention.mupdf.MUPDF_GENERATED_DIR
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

/**
 * Convention for `:third-party:mupdf`: registers `generateMupdfResources`, which writes the
 * C sources of MuPDF's embedded resources into `libmupdf/generated/` (ADR 0013). The module
 * wires it before its native build.
 */
class MupdfConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val mupdfDir = target.layout.projectDirectory.dir(MUPDF_SOURCE_DIR)
        target.tasks.register<GenerateMupdfResourcesTask>(GENERATE_TASK) {
            description = "Generates the C sources of the resources embedded in MuPDF."
            sourceDirectory.set(mupdfDir)
            resources.from(target.fileTree(mupdfDir) { include(MUPDF_EMBEDDED_RESOURCES) })
            outputDirectory.set(mupdfDir.dir(MUPDF_GENERATED_DIR))
        }
    }

    private companion object {
        /** MuPDF submodule, relative to the module directory. */
        const val MUPDF_SOURCE_DIR = "libmupdf"
        const val GENERATE_TASK = "generateMupdfResources"
    }
}
