// SPDX-License-Identifier: GPL-3.0-or-later
package lutrin.convention

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

/** Java bytecode level used by every module. */
internal val JAVA_VERSION = JavaVersion.VERSION_17

/**
 * Applies the settings shared by every Android module (application or library):
 * SDK levels, Java level, unit-test resources for Robolectric, Android Lint rules and the
 * managed emulators of instrumented tests.
 */
internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    extension.apply {
        compileSdk = libs.version("compileSdk").toInt()
        defaultConfig.minSdk = libs.version("minSdk").toInt()
        defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        compileOptions.sourceCompatibility = JAVA_VERSION
        compileOptions.targetCompatibility = JAVA_VERSION
        testOptions.unitTests.isIncludeAndroidResources = true
        testOptions.unitTests.all {
            // Robolectric reads FileDescriptor internals through jdk.internal.access, which
            // the JDK no longer exports by default.
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
        }
        lint.apply {
            warningsAsErrors = true
            abortOnError = true
            checkReleaseBuilds = true
            lintConfig = rootProject.layout.projectDirectory.file("config/lint/lint.xml").asFile
        }
    }
    configureManagedDevices(extension)
    configureKotlinCompiler()
}

/** Zero warnings policy (AGENTS.md §4) and JVM target for every Kotlin compilation of the module. */
internal fun Project.configureKotlinCompiler() {
    tasks.withType<KotlinCompilationTask<*>>().configureEach {
        compilerOptions {
            allWarningsAsErrors.set(true)
            if (this is KotlinJvmCompilerOptions) {
                jvmTarget.set(JvmTarget.fromTarget(JAVA_VERSION.toString()))
            }
        }
    }
}
