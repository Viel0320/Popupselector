plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

android {
    namespace = "com.viel.compose.popupselector.sample"
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
        manifest.srcFile("src/androidMain/AndroidManifest.xml")
        kotlin.directories.add("src/commonMain/kotlin")
        kotlin.directories.add("src/androidMain/kotlin")
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(project(":popupselector"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.haze)
    implementation(libs.haze.materials)
}
