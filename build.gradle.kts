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

        property(
            "sonar.sources", listOf(
                "core/common/src/commonMain",
                "domain/src/commonMain",
                "data/src/commonMain", "data/src/androidMain", "data/src/desktopMain", "data/src/iosMain", "data/src/macosMain",
                "services/src/commonMain", "services/src/androidMain", "services/src/desktopMain", "services/src/iosMain", "services/src/macosMain",
                "presentation/src/commonMain",
                "shared/src/commonMain", "shared/src/iosMain",
                "composeApp/src/commonMain", "composeApp/src/androidMain", "composeApp/src/desktopMain"
            ).joinToString(",")
        )

        property(
            "sonar.tests", listOf(
                "core/testing/src/commonMain",
                "domain/src/commonTest",
                "data/src/commonTest", "data/src/androidUnitTest", "data/src/desktopTest",
                "services/src/commonTest",
                "presentation/src/commonTest",
                "composeApp/src/commonTest", "composeApp/src/androidUnitTest", "composeApp/src/desktopTest", "composeApp/src/androidTest"
            ).joinToString(",")
        )

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
