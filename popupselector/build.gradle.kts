plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    `maven-publish`
}

kotlin {
    jvmToolchain(25)

    android {
        namespace = "com.viel.compose.popupselector"
        compileSdk = 37
        minSdk = 24
        withHostTest {
            isIncludeAndroidResources = true
        }
    }
    
    jvm("jvm")
    
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            implementation(libs.compose.material3)
        }
        
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.compose.ui.test)
        }

        getByName("androidHostTest").dependencies {
            implementation(libs.robolectric)
            implementation(libs.androidx.activity.compose)
        }

        // Desktop UI tests need the platform Skia runtime; without it Skiko fails to initialise.
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}
