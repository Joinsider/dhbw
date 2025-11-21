import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

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
            implementation(libs.ktor.client.okhttp)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(libs.compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.kotlinx.datetime)
            implementation(libs.material.icons.extended)
            implementation(libs.napier)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.datetime.v040)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            @OptIn(ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }

        androidUnitTest.dependencies {
            implementation(libs.robolectric)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        macosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutinesSwing)
                implementation(libs.java.keyring)
                implementation(libs.ktor.client.cio)
            }
        }
    }
}

android {
    namespace = "de.joinside.dhbw"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "de.joinside.dhbw"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 13
        versionName = "v1.0.10"

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
                it.exclude("**/AppTest.class", "**/LoginFormTest.class", "**/ui/**/*Test.class")
            }
        }
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspMacosArm64", libs.androidx.room.compiler)
    add("kspMacosX64", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
}

compose.desktop {
    application {
        mainClass = "de.joinside.dhbw.MainKt"

        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "dhbw-next"
            packageVersion = "1.0.10"
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
                bundleID = "de.joinside.dhbw"
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
    packageOfResClass = "de.joinside.dhbw.resources"
    publicResClass = true
    generateResClass = always
}

room {
    schemaDirectory("$projectDir/schemas")
}

// Custom fat JAR task - simple and reliable
val packageFatJar by tasks.registering(Jar::class) {
    archiveBaseName.set("dhbw-next")
    archiveVersion.set("1.0.10")
    archiveClassifier.set("all")

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "de.joinside.dhbw.MainKt"
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
