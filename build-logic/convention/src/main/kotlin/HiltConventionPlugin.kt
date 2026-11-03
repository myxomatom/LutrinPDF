// SPDX-License-Identifier: GPL-3.0-or-later
import lutrin.convention.library
import lutrin.convention.libs
import lutrin.convention.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Adds Hilt dependency injection (ADR 0002) with its annotation processor run by KSP. */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("ksp"))
            pluginManager.apply(libs.pluginId("hilt"))
            dependencies {
                "implementation"(libs.library("hilt-android"))
                "ksp"(libs.library("hilt-compiler"))
            }
        }
    }
}
