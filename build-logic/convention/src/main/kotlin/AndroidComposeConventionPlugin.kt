// SPDX-License-Identifier: GPL-3.0-or-later
import com.android.build.api.dsl.CommonExtension
import lutrin.convention.library
import lutrin.convention.libs
import lutrin.convention.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/** Enables Jetpack Compose in an Android module already configured by the application or library convention. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("kotlin-compose"))
            extensions.getByType<CommonExtension>().buildFeatures.compose = true
            dependencies {
                val bom = platform(libs.library("androidx-compose-bom"))
                "implementation"(bom)
                "androidTestImplementation"(bom)
                // Compose UI tests pull Espresso 3.5, whose input injection uses a hidden API removed
                // in Android 17 (API 37). Espresso 3.7 uses the public InputManager service instead.
                "androidTestImplementation"(libs.library("androidx-test-espresso-core"))
                "implementation"(libs.library("androidx-compose-ui-tooling-preview"))
                "debugImplementation"(libs.library("androidx-compose-ui-tooling"))
            }
        }
    }
}
