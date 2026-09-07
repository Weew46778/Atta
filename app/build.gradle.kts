plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.arena.mineva"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.arena.mineva"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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

    ndk {
        // Ship only phone ABIs; keeps the embedded speech/ASR runtime small.
        abiFilters += listOf("arm64-v8a", "armeabi-v7a")
    }

    androidResources {
        noCompress += "json"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // SSH client for real VPS provisioning (JSch maintained fork).
    implementation("com.github.mwiede:jsch:0.2.19")
    // Offline speech-to-text (Vosk) bundled with the app; no Google speech service needed.
    implementation("com.alphacephei:vosk-android:0.3.38")
    // Offline neural text-to-speech runtime (Sherpa-ONNX / Piper compatible).
    // The AAR is fetched into app/libs by CI before Gradle runs.
    implementation(files("libs/sherpa-onnx-1.13.5.aar"))
    // Model archive extraction (tar.bz2 for the bundled Piper voice).
    implementation("org.apache.commons:commons-compress:1.26.2")
    implementation("org.tukaani:xz:1.9")
}
