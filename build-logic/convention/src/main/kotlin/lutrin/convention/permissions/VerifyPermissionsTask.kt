// SPDX-License-Identifier: GPL-3.0-or-later
package lutrin.convention.permissions

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Fails the build when the merged manifest of a variant requests a permission listed in
 * [FORBIDDEN_PERMISSIONS], whichever dependency added it. Wired into `check` for release builds.
 */
@CacheableTask
abstract class VerifyPermissionsTask : DefaultTask() {
    /** Merged manifest of the variant under verification. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    /** Report written on success, so that the task can be up to date and cached. */
    @get:OutputFile
    abstract val report: RegularFileProperty

    /** Checks the manifest and writes the report; throws [GradleException] on a forbidden permission. */
    @TaskAction
    fun verify() {
        val found = findForbiddenPermissions(mergedManifest.get().asFile)
        if (found.isNotEmpty()) {
            throw GradleException(
                "Forbidden permission(s) in the merged manifest: ${found.joinToString()}. " +
                    "Lutrin must never request network or broad storage access (SPEC.md §1). " +
                    "Find the dependency that adds it (see the manifest merger report) and remove it, " +
                    "or strip it with tools:node=\"remove\" only if the feature does not need it.",
            )
        }
        report.get().asFile.writeText("No forbidden permission.\n")
    }
}
