// SPDX-License-Identifier: GPL-3.0-or-later
import lutrin.convention.library
import lutrin.convention.libs
import lutrin.convention.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

/**
 * Robolectric for an Android module's unit tests, running offline: the Android framework jars
 * Robolectric needs are resolved by Gradle (cached, retried) instead of being downloaded by
 * Robolectric itself in the middle of a test run.
 */
class AndroidRobolectricConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            dependencies { "testImplementation"(libs.library("robolectric")) }
            // One configuration per jar: in a single one, Gradle would keep only the newest version.
            val androidAll =
                ANDROID_ALL_VERSIONS.map { alias ->
                    configurations.create(alias) {
                        isCanBeConsumed = false
                        isTransitive = false
                    }.also {
                        dependencies.add(it.name, "org.robolectric:android-all-instrumented:${libs.version(alias)}")
                    }
                }
            val jarsDir = layout.buildDirectory.dir("robolectric-android-all")
            val prepare =
                tasks.register<Sync>("prepareRobolectricAndroidAll") {
                    from(androidAll)
                    into(jarsDir)
                }
            tasks.withType<Test>().configureEach {
                dependsOn(prepare)
                systemProperty("robolectric.offline", "true")
                systemProperty("robolectric.dependency.dir", jarsDir.get().asFile.absolutePath)
            }
        }
    }

    private companion object {
        /** SDK levels used by the tests: the default (robolectric.properties) and those of @Config. */
        val ANDROID_ALL_VERSIONS =
            listOf("robolectricAndroidAll30", "robolectricAndroidAll31", "robolectricAndroidAll36")
    }
}
