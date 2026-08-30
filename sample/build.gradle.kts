plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

// Single KMP home for the sample: shared UI in commonMain, desktop entry in jvmMain.
// AGP 9's KMP plugin cannot produce APKs, so the installable Android shell (MainActivity +
// app manifest) lives in :sample-android and consumes this module as a library.
kotlin {
    jvmToolchain(25)

    android {
        namespace = "com.viel.compose.popupselector.sample"
        compileSdk = 37
        minSdk = 24
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":popupselector"))
            implementation(libs.compose.material3)
            implementation(libs.haze)
            implementation(libs.haze.materials)
            implementation(libs.miuix.blur)
        }

        // Platform Skia runtime — required for the desktop app window to start at all.
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.viel.compose.popupselector.sample.MainKt"
    }
}
