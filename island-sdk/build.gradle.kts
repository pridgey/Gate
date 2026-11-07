plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.sameerasw.island.sdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 30
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        aidl = true
        compose = true
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-runtime:2.8.7")
    implementation("androidx.savedstate:savedstate:1.2.1")
}
