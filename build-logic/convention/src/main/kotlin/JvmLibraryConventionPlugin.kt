// SPDX-License-Identifier: GPL-3.0-or-later
import lutrin.build.JAVA_VERSION
import lutrin.build.configureKotlinCompiler
import lutrin.build.configureQuality
import lutrin.build.library
import lutrin.build.libs
import lutrin.build.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Convention for pure Kotlin/JVM modules without any Android dependency, such as `core:domain`. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("kotlin-jvm"))
            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JAVA_VERSION
                targetCompatibility = JAVA_VERSION
            }
            configureKotlinCompiler()
            configureQuality()
            dependencies {
                "testImplementation"(libs.library("junit"))
                "testImplementation"(libs.library("truth"))
                "testImplementation"(libs.library("kotlinx-coroutines-test"))
            }
        }
    }
}
