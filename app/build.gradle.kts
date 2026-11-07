import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.paparazzi")
}

android {
    namespace = "dev.pridgey.gates"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.pridgey.gates"
        minSdk = 30
        targetSdk = 35
        // CI passes the run number so every build installs as an update.
        val build = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "0.1.$build"
    }

    // Gates' signing key never lives in the repo. CI gets it from secrets (SIGNING_* env vars);
    // locally it's read from ~/.android/gates-signing/gates-signing.properties if present.
    // Without either, builds use Android's standard debug key and can't update CI installs.
    val signing: Map<String, String>? = run {
        val env = System.getenv()
        if (!env["SIGNING_STORE_FILE"].isNullOrBlank()) {
            mapOf(
                "storeFile" to env.getValue("SIGNING_STORE_FILE"),
                "storePassword" to env["SIGNING_STORE_PASSWORD"].orEmpty(),
                "keyAlias" to env["SIGNING_KEY_ALIAS"].orEmpty(),
                "keyPassword" to env["SIGNING_KEY_PASSWORD"].orEmpty(),
            )
        } else {
            val f = file("${System.getProperty("user.home")}/.android/gates-signing/gates-signing.properties")
            if (f.exists()) Properties().apply { f.inputStream().use { load(it) } }
                .entries.associate { it.key.toString() to it.value.toString() } else null
        }
    }
    signingConfigs {
        if (signing != null) {
            create("gates") {
                storeFile = file(signing.getValue("storeFile"))
                storePassword = signing["storePassword"]
                keyAlias = signing["keyAlias"]
                keyPassword = signing["keyPassword"]
            }
        }
    }

    buildTypes {
        debug {
            if (signing != null) signingConfig = signingConfigs.getByName("gates")
        }
        release {
            isMinifyEnabled = false
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
}

dependencies {
    implementation(project(":island-sdk"))
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    testImplementation("junit:junit:4.13.2")
}

// Screenshot tests (Paparazzi) only run when asked: ./gradlew recordPaparazziDebug -Pscreens
tasks.withType<Test>().configureEach {
    if (!project.hasProperty("screens")) exclude("**/screens/**")
}
