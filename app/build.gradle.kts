plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.smartexpense.tracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.smartexpense.tracker"
        minSdk = 31   // Android 12 – minimum for Google AICore (Gemini Nano)
        targetSdk = 36 // Android 16 (S24 Ultra target)
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
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
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.02.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // CameraX for OCR scanning
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    // ML Kit for OCR — all script recognizers for multi-language support
    // Updated to 16.0.1 for 16 KB page-size alignment (Android 15+ requirement)
    implementation("com.google.mlkit:text-recognition:16.0.1")              // Latin
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")      // Chinese
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.1")   // Devanagari (Hindi etc)
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")     // Japanese
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")       // Korean

    // ML Kit Barcode scanning — for QR codes on receipts
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Gson for JSON storage
    implementation("com.google.code.gson:gson:2.10.1")

    // Accompanist permissions
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // Charts (Vico)
    implementation("com.patrykandpatrick.vico:compose-m3:1.13.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // WorkManager for salary scheduling
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
