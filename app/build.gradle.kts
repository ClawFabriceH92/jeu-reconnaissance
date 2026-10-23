import java.io.File
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Keystore de release : secret CI (RECONNAISSANCE_KEYSTORE_B64) ou sauvegarde locale.
// Sans keystore, la release est signée avec la clé de debug (installable, mais pas pour le Play Store).
fun releaseKeystore(): File? {
    System.getenv("RECONNAISSANCE_KEYSTORE_B64")?.takeIf { it.isNotBlank() }?.let { b64 ->
        val tmp = File(System.getenv("RUNNER_TEMP") ?: "/tmp", "reconnaissance-release.keystore")
        tmp.writeBytes(Base64.getDecoder().decode(b64))
        return tmp
    }
    val candidates = listOf(
        File(System.getProperty("user.home"), ".secrets/keystores-android/reconnaissance-release.keystore"),
        File("/root/.secrets/keystores-android/reconnaissance-release.keystore"),
    )
    return candidates.firstOrNull { it.exists() }
}

android {
    namespace = "fr.jeu.reconnaissance.visages"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.jeu.reconnaissance.visages"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
    }

    val ks = releaseKeystore()
    signingConfigs {
        if (ks != null) {
            create("release") {
                storeFile = ks
                storePassword = System.getenv("RECONNAISSANCE_KEYSTORE_PASSWORD") ?: "CHANGE_ME"
                keyAlias = System.getenv("RECONNAISSANCE_KEY_ALIAS") ?: "reconnaissance"
                keyPassword = System.getenv("RECONNAISSANCE_KEY_PASSWORD")
                    ?: System.getenv("RECONNAISSANCE_KEYSTORE_PASSWORD")
                    ?: "CHANGE_ME"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.all { it.environment("LC_ALL", "C.UTF-8") }
    }

    lint {
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Tests (org.json n'est qu'un stub dans les tests JVM d'Android)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}
