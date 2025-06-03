import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ---- Dynamic Versioning Properties ----
val versionProps = Properties()
val versionFile = rootProject.file("version.properties")
if (versionFile.exists()) {
    versionFile.inputStream().use { versionProps.load(it) }
}

val verCode = versionProps["VERSION_CODE"]?.toString()?.toIntOrNull() ?: 1
val verName = versionProps["VERSION_NAME"]?.toString() ?: "1.0.0"

// ---- Signing Properties ----
val keystoreProperties = Properties()
val keystoreFile = rootProject.file("keystore/keystore.properties")
if (keystoreFile.exists() && keystoreFile.readText().isNotBlank()) {
    keystoreFile.inputStream().use { keystoreProperties.load(it) }
}

android {
    namespace = "com.bitblazer.whatsweep"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bitblazer.whatsweep"
        minSdk = 21
        targetSdk = 35
        this.versionCode = verCode
        this.versionName = verName
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            if (keystoreProperties.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        mlModelBinding = true
        buildConfig = true
    }
}

// Ensure version file is generated before the app is built
tasks.named("preBuild").configure {
    dependsOn(":generateVersionProperties")
}

dependencies {
    implementation(libs.material)

    // Jetpack Compose
    implementation(libs.compose.materialIconsExtended)
    implementation(libs.navigation.compose)
    implementation(libs.compose.material3)

    // ML Kit - Custom Model
    implementation(libs.mlkit.common)
    implementation(libs.mlkit.imageLabelingCustom)

    // Image loading
    implementation(libs.coil.compose)

    // Runtime permissions
    implementation(libs.accompanist.permissions)

    // JSON handling
    implementation(libs.gson)
}
