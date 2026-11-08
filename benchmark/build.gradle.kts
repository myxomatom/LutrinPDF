// SPDX-License-Identifier: GPL-3.0-or-later
// Macrobenchmarks and Baseline Profile generator, run on a real device against :app
// (ADR 0012). Never run in CI: an emulator gives meaningless timings.
plugins {
    alias(libs.plugins.lutrin.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "io.github.myxomatom.lutrin.benchmark"
    targetProjectPath = ":app"
    // The tests and the app under test run in separate processes: the test APK instruments itself.
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

baselineProfile {
    // The maintainer's connected device; no Gradle Managed Device (they need KVM and give no
    // meaningful timings).
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
