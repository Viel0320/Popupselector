plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

// Installable Android shell for the :sample KMP module: MainActivity and the app manifest only.
// AGP 9 forbids combining the application plugin with the Kotlin multiplatform plugin, so this
// thin module exists solely to package :sample into an APK.
android {
    // Distinct from :sample's namespace — the manifest merger rejects duplicates.
    namespace = "com.viel.compose.popupselector.sample.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.viel.compose.popupselector.sample"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(25)
        targetCompatibility = JavaVersion.toVersion(25)
    }

    buildFeatures {
        compose = true
    }

    sourceSets.named("main") {
        kotlin.directories.add("src/main/kotlin")
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(project(":sample"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
}
