// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    alias(libs.plugins.lutrin.android.library)
}

android {
    namespace = "io.github.myxomatom.lutrin.core.data"
}

// Stricter than the global 70 % (AGENTS.md §5.2).
kover {
    reports {
        verify {
            rule {
                minBound(80)
            }
        }
    }
}
