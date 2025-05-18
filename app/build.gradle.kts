plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

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

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
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
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.uiGraphics)
    implementation(libs.compose.uiToolingPreview)
    implementation(libs.compose.materialIconsExtended)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtimeCompose)
    implementation(libs.compose.material3)

    // ML Kit Custom Model
    implementation(libs.mlkit.common)
    implementation(libs.mlkit.imageLabelingCustom)

    // Coil for image loading
    implementation(libs.coil.compose)

    // ViewModels for Compose
    implementation(libs.lifecycle.viewmodelCompose)    // Permissions handling
    implementation(libs.accompanist.permissions)

    // Activity
    implementation(libs.activity.ktx)
    implementation(libs.activity.compose)

    // Gson for JSON serialization/deserialization
    implementation(libs.gson)
    implementation(libs.tensorflow.lite.support)
    implementation(libs.tensorflow.lite.metadata)
    implementation(libs.tensorflow.lite.gpu)
}
