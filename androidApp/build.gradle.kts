// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The Android app. Since AGP 9 `com.android.application` may not share a project with the Kotlin
// Multiplatform plugin, so this module holds only what an APK needs and a library may not have:
// the activity and application class, the manifest (deep links, widget receiver, backup rules),
// launcher resources, the Glance widget, signing, the version and the R8 rules. The UI itself is
// :composeApp.
//
// applicationId, versionCode/versionName and the signing config are the ones :composeApp carried,
// so a build from here installs over every earlier release. The release workflow and F-Droid
// read the version from this file.
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kover)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

android {
    namespace = "de.fampopprol.dhbwhorb"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    buildToolsVersion = "36.0.0"

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    // app_name comes from resValue below; resValues are off by default since AGP 9.
    buildFeatures {
        resValues = true
    }

    defaultConfig {
        applicationId = "de.fampopprol.dhbwhorb"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 
        versionName = "v"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        resValue("string", "app_name", "DHBW Horb Studenten App")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        create("release") {
            storeFile = System.getenv("SIGNING_KEYSTORE_PATH")?.let { file(it) }
            storePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // Brings the UI and, through its api() dependencies, every shared module.
    implementation(projects.composeApp)

    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
    implementation(libs.napier)
    implementation(libs.kotlinx.datetime)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)

    // The instrumented suite runs on a device (`:androidApp:connectedDebugAndroidTest`) and is not
    // part of the gate.
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.testExt.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.work.testing)
}

// Same variant name as every other module, so `./gradlew koverXmlReportKmpCoverage` covers the
// widget code here too, and Sonar finds the report at the default path (see build.gradle.kts).
kover {
    currentProject {
        createVariant("kmpCoverage") {
            add("debug")
        }
    }
}
