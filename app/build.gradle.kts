plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "com.alexhr90.muabet"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.alexhr90.muabet"
        minSdk = 26
        targetSdk = 36
        // Single source of truth: muabet.versionName in gradle.properties (MAJOR.MINOR.PATCH).
        val name = property("muabet.versionName") as String
        val (major, minor, patch) = name.split(".").map { it.toInt() }
        versionName = name
        versionCode = major * 10000 + minor * 100 + patch
        // The honest User-Agent (SPEC §3), in step with the version.
        buildConfigField("String", "USER_AGENT", "\"Muabet/$name (+https://github.com/alexhr90/KajganaAndroidClient)\"")
    }

    // Shared debug key (config/debug.keystore, not a secret) so every preview installs over the last.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("config/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    // The release workflow writes release.jks and exports SIGNING_* when the secrets exist.
    val keystoreFile = rootProject.file("release.jks")
    val isSigning = keystoreFile.exists() &&
        !System.getenv("SIGNING_KEY_ALIAS").isNullOrEmpty() &&
        !System.getenv("SIGNING_KEY_PASSWORD").isNullOrEmpty() &&
        !System.getenv("SIGNING_STORE_PASSWORD").isNullOrEmpty()

    if (isSigning) {
        signingConfigs {
            create("release") {
                storeFile = keystoreFile
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Lets preview APKs install alongside a release build.
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            signingConfig = if (isSigning) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += listOf("META-INF/NOTICE.md", "META-INF/LICENSE.md", "META-INF/DEPENDENCIES", "META-INF/*.kotlin_module")
        }
    }

    lint {
        // Release builds must not fail on lint (the cloud cannot run it first); CI keeps the report.
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.androidx.browser)
    implementation(libs.coil.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
}
