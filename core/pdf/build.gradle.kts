// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    alias(libs.plugins.lutrin.android.library)
    alias(libs.plugins.lutrin.hilt)
}

android {
    namespace = "io.github.myxomatom.lutrin.core.pdf"

    sourceSets {
        // Engine checks run both on the JVM and on a device (CJK fallback font, ADR 0025).
        named("test") { kotlin.directories.add("src/sharedTest/kotlin") }
        named("androidTest") { kotlin.directories.add("src/sharedTest/kotlin") }
    }

    testOptions.unitTests.all { test ->
        // JVM tests drive the real engine through MuPDF's JNI library built for this machine.
        val mupdf = project(":third-party:mupdf")

        @Suppress("UNCHECKED_CAST")
        val hostJniDir = mupdf.extra["hostJniDir"] as Provider<Directory>
        test.dependsOn(mupdf.extra["buildMupdfHostJni"] as TaskProvider<*>)
        test.systemProperty("java.library.path", hostJniDir.get().asFile.absolutePath)
        // Outside Linux the library is not built: the engine tests skip themselves (ADR 0013).
        test.systemProperty("lutrin.hostEngine", mupdf.extra["isHostJniSupported"].toString())
        test.systemProperty("lutrin.testAssets", rootProject.file("core/testing/src/main/assets").absolutePath)
    }
}

dependencies {
    api(projects.core.domain)
    implementation(projects.thirdParty.mupdf)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(projects.core.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
