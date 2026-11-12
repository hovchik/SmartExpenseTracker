plugins {
    id("com.android.application")
    // Kotlin is built into AGP 9+, so the org.jetbrains.kotlin.android plugin is no
    // longer applied. Only the Compose compiler plugin is still needed.
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.flowsense.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.flowsense.app"
        minSdk = 31   // Android 12 – minimum for Google AICore (Gemini Nano)
        targetSdk = 37 // Android 17
        versionCode = 6
        versionName = "1.1.0"
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // Android 17 lint checks for SMS OTP handling, background activity launches etc.
        checkReleaseBuilds = true
        abortOnError = false
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

// Kotlin's JVM target follows android.compileOptions.targetCompatibility (17),
// so the old kotlinOptions { jvmTarget } block is no longer needed.

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.10.2")

    // CameraX for OCR scanning (16 KB page-aligned native libs)
    implementation("androidx.camera:camera-core:1.6.2")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")

    // ML Kit for OCR — all script recognizers for multi-language support
    // Updated to 16.0.1 for 16 KB page-size alignment (Android 15+ requirement)
    implementation("com.google.mlkit:text-recognition:16.0.1")              // Latin + Cyrillic (Russian)
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")      // Chinese
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.1")   // Devanagari (Hindi etc)
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")     // Japanese
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")       // Korean

    // Tesseract OCR for Armenian script (ML Kit has no Armenian model)
    // Tesseract4Android wraps Tesseract 5.x — 4.8.0 ships 16 KB page-aligned native libs
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.8.0")

    // ML Kit Barcode scanning — for QR codes on receipts
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // MediaPipe LLM Inference — on-device Gemma/LLM for AI categorization & insights (16 KB aligned)
    implementation("com.google.mediapipe:tasks-genai:0.10.29")

    // Gson for JSON storage
    implementation("com.google.code.gson:gson:2.13.2")

    // Accompanist permissions
    implementation("com.google.accompanist:accompanist-permissions:0.37.3")

    // osmdroid – OpenStreetMap for Store Map screen (open-source, no API key needed)
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // Charts (Vico)
    implementation("com.patrykandpatrick.vico:compose-m3:1.16.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Glance – Jetpack Compose for App Widgets
    implementation("androidx.glance:glance-appwidget:1.2.0")

    // WorkManager for salary scheduling
    implementation("androidx.work:work-runtime-ktx:2.12.0")

    // Google Play Billing for subscriptions & one-time purchases
    implementation("com.android.billingclient:billing-ktx:9.1.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
