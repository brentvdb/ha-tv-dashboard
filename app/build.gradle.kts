plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.brentvdb.hatv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.brentvdb.hatv"
        minSdk = 23
        targetSdk = 35
        versionCode = 4
        versionName = "1.2.0"
    }

    // A fixed signing key kept in the repo, so every CI build can be installed
    // over the previous one without uninstalling (a fresh debug key each run
    // would cause "App not installed" on update). Personal-use app only.
    signingConfigs {
        create("tv") {
            storeFile = file("tv-signing.keystore")
            storePassword = "hatvdashboard"
            keyAlias = "hatv"
            keyPassword = "hatvdashboard"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("tv")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("tv")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.webkit:webkit:1.12.1")
}
