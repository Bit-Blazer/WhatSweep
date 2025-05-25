import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Create a variable called keystorePropertiesFile, and initialize it to your
// keystore.properties file, in the rootProject folder.
val keystorePropertiesFile = rootProject.file("keystore/keystore.properties")

// Initialize a new Properties() object called keystoreProperties.
val keystoreProperties = Properties()

// Load your keystore.properties file into the keystoreProperties object.
keystoreProperties.load(FileInputStream(keystorePropertiesFile))

android {
    namespace = "com.bitblazer.whatsweep"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bitblazer.whatsweep"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

    }
    splits{
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk  = false
        }
    }

    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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
    }
}

dependencies {
    implementation(libs.material)

    // Compose
    implementation(libs.compose.materialIconsExtended)
    implementation(libs.navigation.compose)
    implementation(libs.compose.material3)

    // ML Kit Custom Model
    implementation(libs.mlkit.common)
    implementation(libs.mlkit.imageLabelingCustom)

    // Coil for image loading
    implementation(libs.coil.compose)

    // EXIF data handling for image orientation
    implementation(libs.exifinterface)

    // Permissions handling
    implementation(libs.accompanist.permissions)

    // Gson for JSON serialization/deserialization
    implementation(libs.gson)
}
