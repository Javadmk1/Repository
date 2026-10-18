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
        versionCode = 3
        versionName = "0.3.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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
    testImplementation("junit:junit:4.13.2")
}
