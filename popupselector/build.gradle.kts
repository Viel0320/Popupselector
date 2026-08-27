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
        withHostTest {}
    }
    
    jvm("jvm")
    
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.material3)
        }
        
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
