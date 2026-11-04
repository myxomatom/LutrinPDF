// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    alias(libs.plugins.lutrin.jvm.library)
}

dependencies {
    // JSR-330 annotations only (pure JVM): lets the domain declare injection qualifiers.
    api(libs.javax.inject)
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
