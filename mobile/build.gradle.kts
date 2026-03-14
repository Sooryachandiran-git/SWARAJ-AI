plugins {
    id("com.android.application")
    // NOTE: AGP 9.x has built-in Kotlin support — do NOT add kotlin("android") here.
    // Adding it causes: "Cannot add extension with name 'kotlin', as there is an extension already registered"
}

android {
    namespace = "com.swarajai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.swarajai"
        minSdk = 33 // Android 13+ required for native modern Java API support (Files.readString)
        targetSdk = 34
        versionCode = 1
        versionName = "1.0-Swaraj-Intelligence"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        viewBinding = true
    }
    
    compileOptions {
        // Upgraded minSdk to 33, so native modern Java APIs are available
        isCoreLibraryDesugaringEnabled = false
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            // Fixes the "16 KB Alignment" error seen in your screenshot
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Crucial: Skip compression for model files. 
    // This makes the 'packaging' task 10x faster and allows the app to memory-map the models.
    androidResources {
        noCompress += listOf("gguf", "onnx", "bin")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    

    // llama.cpp for GGUF Inference (Verified Maven Central Version)
    implementation("io.github.ljcamargo:llamacpp-kotlin:0.2.0")

    // Vosk STT (replaced by Android native on-device STT — no JNI crashes)
    // implementation("com.alphacephei:vosk-android:0.3.75")

    // ONNX Runtime (REMOVED - unused component, was wasting 60MB of JNI RAM)
    // implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")


    // JSON Parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Coroutines for Serial Loading Management
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

configurations.all {
    resolutionStrategy {
        force("androidx.core:core:1.12.0")
        force("androidx.core:core-ktx:1.12.0")
    }
}
