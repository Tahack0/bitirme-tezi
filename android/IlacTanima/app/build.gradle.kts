plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.ilactanima"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.ilactanima"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    // TFLite ve SQLite dosyaları sıkıştırılmamalı
    androidResources {
        noCompress += listOf("tflite", "db")
    }

    // Yeni dataBinding/composer'lar gerekmiyor — bilerek devre dışı
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)

    // TFLite
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.support)

    // SQLite
    implementation(libs.androidx.sqlite.ktx)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Lifecycle + Coroutines
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
