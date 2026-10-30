plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "de.memet.nckreis"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.memet.nckreis"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    // Fester Schlüssel im Repo, damit jede neue APK über die alte installiert werden kann.
    signingConfigs {
        create("gemeinsam") {
            storeFile = file("nckreis.keystore")
            storePassword = "nckreis"
            keyAlias = "nckreis"
            keyPassword = "nckreis"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("gemeinsam") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("gemeinsam")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")

    testImplementation("junit:junit:4.13.2")
}
