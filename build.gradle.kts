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
        property("sonar.coverage.jacoco.xmlReportPaths", "composeApp/build/reports/kover/report.xml")

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
                "**/generated/**"
            ).joinToString(",")
        )
    }
}
