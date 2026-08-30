plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.sonarqube)
}

// Replaces sonar-project.properties: the Gradle plugin reads source sets and compiled-class
// locations straight from the Gradle model instead of hand-maintained paths that silently drift
// out of sync with the real Kotlin/AGP output layout (see CI history around 2026-08-29).
sonar {
    properties {
        property("sonar.projectKey", "Joinsider_dhbw")
        property("sonar.organization", "joinsider")
        property("sonar.projectName", "DHBW Horb Student App")

        // No manual sonar.sources/sonar.tests here: the Gradle plugin auto-detects each module's
        // main/test source sets from the real Kotlin Multiplatform + Android model. Listing them
        // by hand collided with that auto-detection (same file registered as both main and test —
        // "can't be indexed twice", see SCANGRADLE-429 and the wider class of Sonar/KMP duplicate-
        // registration bugs hit while wiring this up on 2026-08-29/30) and defeats the whole point
        // of moving off sonar-project.properties in the first place.

        property("sonar.sourceEncoding", "UTF-8")

        property(
            "sonar.exclusions", listOf(
                "**/build/**",
                "**/*.generated/**",
                "**/generated/**",
                "composeApp/build/generated/**",
                "**/resources/**",
                "**/*_Impl.kt",
                "**/*_Impl.java",
                "**/BuildConfig.kt",
                "**/*.pb.kt"
            ).joinToString(",")
        )

        property(
            "sonar.coverage.exclusions", listOf(
                "**/test/**",
                "**/*Test*.kt",
                "**/*Test*.java",
                "**/build/**",
                "**/generated/**",
                // composeApp's own testOptions.unitTests.all{} excludes "**/ui/**/*Test.class" from
                // the Android unit test run ("these tests work on iOS and JVM but require
                // instrumented tests on Android") — so a Compose composable whose only platform
                // branch is Android-specific, like the Activity Result / permission flow here, has
                // no test task that can ever reach it: not desktopTest (wrong target), not
                // testDebugUnitTest (Compose UI tests excluded there by policy), and instrumented
                // tests aren't part of the CI gate. Not a gap to close with more tests — it would
                // mean reversing that policy.
                "composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/ui/settings/NotificationPermission.android.kt",
                // macOS (Kotlin/Native) isn't one of the targets addWithDependencies() adds to the
                // kmpCoverage Kover variant in any module's build.gradle.kts (only "debug" and
                // "desktop" are) — no coverage run ever instruments macosMain, regardless of tests.
                "composeApp/src/macosMain/kotlin/de/fampopprol/dhbwhorb/ui/theme/Theme.macos.kt"
            ).joinToString(",")
        )

        // LectureNotificationTexts.kt is a parallel DE/EN translation table: the two objects mirror
        // each other's shape by necessity (one entry per LectureChange case), but the strings
        // themselves differ — including grammatical case in German that English has no equivalent
        // for — so templating them into one shared implementation would make the translations more
        // fragile, not less duplicated in any way that matters. This is content, not copy-pasted
        // logic, so it's excluded from duplication detection rather than "fixed".
        property(
            "sonar.cpd.exclusions", listOf(
                "**/services/src/commonMain/kotlin/de/fampopprol/dhbwhorb/services/notifications/LectureNotificationTexts.kt"
            ).joinToString(",")
        )
    }
}

// sonar.coverage.jacoco.xmlReportPaths must be set per module, relative to that module's own
// directory — Sonar resolves it that way, not relative to the root. Setting one root-relative
// list (as before) made Sonar look for e.g. "domain/build/..." *inside* domain/, i.e.
// "domain/domain/build/...", which never exists: every module silently got 0% coverage.
// Every module defines its own kmpCoverage Kover variant (see each module's build.gradle.kts);
// composeApp renames its XML output to report.xml, the rest use Kover's default name for a named
// variant, reportKmpCoverage.xml.
subprojects {
    sonar {
        properties {
            property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/kover/reportKmpCoverage.xml")
        }
    }
}

project(":composeApp") {
    sonar {
        properties {
            property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/kover/report.xml")
        }
    }
}
