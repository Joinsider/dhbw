import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kover)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // No iOS targets since P7: the iOS app is SwiftUI and links `Shared.framework`. Compose
    // here would only be dead weight in a binary nothing imports — and it would quietly re-open
    // the door for a Compose dependency to reach `:presentation`.

    macosArm64()
    macosX64()

    jvm("desktop")

    // Configure source set hierarchy
    applyDefaultHierarchyTemplate()

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.security.crypto)
            implementation(libs.androidx.work.runtime.ktx)
            implementation(libs.glance.appwidget)
            implementation(libs.glance.material3)
            implementation(libs.ktor.client.okhttp)
            // Ensure OkHttp core is present for DoH
            implementation(libs.okhttp)
            // Use OkHttp DNS-over-HTTPS for fallback
            implementation(libs.okhttp.dnsoverhttps)
            // Foldable device support via WindowInfoTracker and FoldingFeature
            implementation(libs.koin.android)
            implementation(libs.androidx.window)
        }
        commonMain.dependencies {
            api(projects.core.common)
            api(projects.domain)
            api(projects.data)
            api(projects.services)
            api(projects.presentation)
            api(projects.shared)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(compose.runtime)
            implementation(compose.foundation)
            // Pinned to an alpha on purpose, re-checked in P9.
            //
            // The UI is built on Material 3 Expressive: `MaterialExpressiveTheme` in Theme.kt, the
            // expressive type scale in Type.kt, `LoadingIndicator` on the login and documents
            // screens, `ButtonGroupDefaults` in the design settings. On the newest stable release
            // (1.9.0) `ExperimentalMaterial3ExpressiveApi` is internal and `LoadingIndicator` does
            // not exist — tried in P9, 45 compile errors.
            //
            // The old comment here warned against "upgrading to stable (1.10.0)". There is no such
            // release: the artefact goes 1.9.0 → 1.10.0-alphaNN → 1.11.0-alphaNN. alpha05 is the
            // last of the 1.10 line and matches the Compose Multiplatform runtime; the 1.11 and
            // 1.12 alphas run ahead of it, so moving there is a runtime bump, not a version bump.
            //
            // Since P7 this only ships to Android, Desktop and macOS — the iOS app is SwiftUI and
            // never sees Compose.
            implementation(libs.compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.kotlinx.datetime)
            implementation(libs.material.icons.extended)
            implementation(libs.napier)
            implementation(libs.kotlinx.datetime.v040)
            implementation(libs.kotlinx.serialization.json)
            implementation("com.materialkolor:material-kolor:4.0.5") {
                exclude(group = "org.jetbrains.compose.material3", module = "material3")
            }
        }

        commonTest.dependencies {
            // Only the tests that belong to this module are left: the Compose UI, navigation and
            // the Koin graph as the app assembles it. Everything below the UI moved into its own
            // module's commonTest in P9.
            implementation(projects.core.testing)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlin.test)
            @OptIn(ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }

        androidUnitTest.dependencies {
            implementation(libs.robolectric)
        }

        // The instrumented suite had no dependencies at all, so it had not compiled since the
        // module split — which is how eight tests that assert nothing survived unnoticed. It runs
        // on a device (`:composeApp:connectedDebugAndroidTest`) and is not part of the gate.
        androidInstrumentedTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.junit)
            implementation(libs.androidx.testExt.junit)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.work.testing)
            implementation(libs.androidx.work.runtime.ktx)
            implementation(libs.glance.appwidget)
        }

        macosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        val desktopTest by getting {
            dependencies {
                implementation(libs.koin.test)
                // MigrationTestHelper reads the schema exports in data/schemas/ and opens real
                // database files — the migration guard cannot run against an in-memory database.
                implementation(libs.androidx.room.testing)
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutinesSwing)
                implementation(libs.java.keyring)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.okhttp)
            }
        }
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

    defaultConfig {
        applicationId = "de.fampopprol.dhbwhorb"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 26
        versionName = "v3.0.0"

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
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                // Exclude Compose UI tests from Android unit tests
                // These tests work on iOS and JVM but require instrumented tests on Android
                it.exclude(
                    "**/AppTest.class",
                    "**/AppRoutingTest.class",
                    "**/LoginFormTest.class",
                    "**/ui/**/*Test.class",
                    // Renders Compose UI, needs an Android runtime (Build.FINGERPRINT); covered by desktopTest.
                    "**/Phase8StabilityTest.class"
                )
            }
        }
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    debugImplementation(compose.uiTooling)

    // Aggregate the library modules into this project's coverage report — after the module split
    // the report would otherwise only cover the UI layer.
    kover(projects.core.common)
    kover(projects.domain)
    kover(projects.data)
    kover(projects.services)
    kover(projects.presentation)
}

compose.desktop {
    application {
        mainClass = "de.fampopprol.dhbwhorb.MainKt"

        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "dhbw-horb-student-app"
            packageVersion = "3.0.0"
            // Avoid SSL trust chain issues in packaged apps caused by missing JDK modules.
            includeAllModules = true
            modules(
                "java.base",
                "java.datatransfer",
                "java.desktop",
                "java.instrument",
                "java.logging",
                "java.management",
                "java.prefs",
                "java.xml",
                "jdk.crypto.ec",
                "jdk.security.auth",
                "jdk.unsupported"
            )

            windows {
                iconFile.set(project.file("icon.ico"))
            }
            macOS {
                iconFile.set(project.file("icon.icns"))
                bundleID = "de.fampopprol.dhbw"
                // For Mac App Store, you'll need to configure signing:
                // signing {
                //     sign.set(true)
                //     identity.set("3rd Party Mac Developer Application: Your Name (TEAM_ID)")
                // }
                // appStore.set(true)
            }
            linux {
                iconFile.set(project.file("icon.png"))
            }
        }
    }
}


compose.resources {
    packageOfResClass = "de.fampopprol.dhbwhorb.resources"
    publicResClass = true
    generateResClass = always
}

// ─── Kover — KMP-unified coverage ────────────────────────────────────────────
kover {
    currentProject {
        // Instrument both Android (debug) and Desktop (JVM) compilations.
        // iOS / macOS native targets are skipped because they require an Apple
        // runner and cannot produce JVM bytecode for instrumentation.
        createVariant("kmpCoverage") {
            addWithDependencies("debug")        // Android debug unit tests
            addWithDependencies("desktop")      // Desktop / JVM tests
        }
    }

    reports {
        variant("kmpCoverage") {
            // XML consumed by SonarCloud
            xml {
                onCheck = false
                xmlFile = layout.buildDirectory.file("reports/kover/report.xml")
            }
            // Human-readable HTML report uploaded as a CI artefact
            html {
                onCheck = false
                htmlDir = layout.buildDirectory.dir("reports/kover/html")
            }
        }

        filters {
            excludes {
                // Room-generated DAOs / databases
                packages(
                    "*.generated.*",
                    "*.BuildConfig",
                    "de.fampopprol.dhbwhorb.resources",           // Compose-generated resources
                    "de.fampopprol.dhbwhorb.*_Impl",              // Room _Impl classes
                )
                annotatedBy(
                    "androidx.room.Database",
                    "androidx.room.Dao",
                )
                // Build-config and generated source trees
                classes(
                    "*_Factory",
                    "*_MembersInjector",
                    "*.BuildConfig",
                    "Manifest*",
                    "*.ComposableSingletons*",
                )
            }
        }
    }
}

// Custom fat JAR task - simple and reliable
val packageFatJar by tasks.registering(Jar::class) {
    archiveBaseName.set("dhbw-horb-student-app")
    archiveVersion.set("3.0.0")
    archiveClassifier.set("all")

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "de.fampopprol.dhbwhorb.MainKt"
    }

    // Get desktop compilation
    val desktopCompilation = kotlin.targets["desktop"].compilations["main"]

    // Include compiled classes and resources
    from(desktopCompilation.output.classesDirs)
    from(desktopCompilation.output.resourcesDir)

    // Include all runtime dependencies
    dependsOn(desktopCompilation.compileAllTaskName)
    from({
        desktopCompilation.runtimeDependencyFiles?.files?.map {
            if (it.isDirectory) it else zipTree(it)
        }
    })
}
