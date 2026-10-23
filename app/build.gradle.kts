plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.javadmk1.autosubtitle"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.javadmk1.autosubtitle"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "0.9.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("RELEASE_STORE_FILE")
            storeFile = storePath?.let { file(it) } ?: file("missing-release-key.jks")
            storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: ""
            keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: ""
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }

    lint {
        // Keep existing lint findings visible without blocking debug APK builds.
        abortOnError = false
    }
}

dependencies {
    val media3Version = "1.10.0"
    implementation("androidx.media3:media3-common:$media3Version")
    implementation("androidx.media3:media3-effect:$media3Version")
    implementation("androidx.media3:media3-transformer:$media3Version")

    // Local unit tests for subtitle timing and SRT formatting.
    implementation("dev.ffmpegkit-maintained:whisper-android:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
}
