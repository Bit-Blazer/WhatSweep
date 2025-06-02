// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
}

allprojects {
    repositories {
        mavenCentral()
        mavenLocal()
        google()
    }
}

tasks.register("generateVersionProperties") {
    val outputFile = rootProject.file("version.properties")

    doLast {
        val versionCode = "git rev-list --count HEAD".runCommand()?.toIntOrNull() ?: 1
        val versionName = "git describe --tags --abbrev=0".runCommand() ?: "1.0.0"

        outputFile.writeText("""
            VERSION_CODE=$versionCode
            VERSION_NAME=$versionName
        """.trimIndent())
    }
}

// Helper function
fun String.runCommand(): String? = try {
    ProcessBuilder(*split(" ").toTypedArray())
        .redirectErrorStream(true)
        .start().inputStream.bufferedReader().readText().trim()
} catch (_: Exception) {
    null
}
