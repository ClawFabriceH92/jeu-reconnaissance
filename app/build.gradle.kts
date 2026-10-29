import java.io.File
import java.util.Base64
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * La clé de signature doit être la MÊME à chaque build, sinon l'installation par-dessus
 * échoue et la mise à jour intégrée ne sert à rien (même principe que notes-de-frais).
 * CI : secrets RECONNAISSANCE_KEYSTORE_B64 / _KEYSTORE_PASSWORD / _KEY_ALIAS / _KEY_PASSWORD
 * (publiés par scripts/push-secrets.sh). En local : ~/.secrets/keystores-android/.
 * Sans clé, la release est signée avec la clé de debug du poste (installable, pas pour le Play Store).
 */
val dossiersSecrets = listOf(
    File(System.getProperty("user.home"), ".secrets/keystores-android"),
    File("/root/.secrets/keystores-android"),
)

fun secretFromFiles(key: String): String? {
    for (dossier in dossiersSecrets) {
        val file = File(dossier, "reconnaissance-keystore.env")
        if (!file.exists()) continue
        val props = Properties()
        file.inputStream().use { props.load(it) }
        val value = props.getProperty(key) ?: props.getProperty(key.replaceFirst("RECONNAISSANCE_", ""))
        if (!value.isNullOrBlank()) return value
    }
    return null
}

val storePasswordValue: String? = System.getenv("RECONNAISSANCE_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
    ?: secretFromFiles("RECONNAISSANCE_KEYSTORE_PASSWORD") ?: secretFromFiles("STORE_PASSWORD")
val keyAliasValue: String = System.getenv("RECONNAISSANCE_KEY_ALIAS")?.takeIf { it.isNotBlank() }
    ?: secretFromFiles("KEY_ALIAS") ?: "reconnaissance"
val keyPasswordValue: String? = System.getenv("RECONNAISSANCE_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
    ?: storePasswordValue

fun releaseKeystore(): File? {
    System.getenv("RECONNAISSANCE_KEYSTORE_B64")?.takeIf { it.isNotBlank() }?.let { b64 ->
        val tmp = File(System.getenv("RUNNER_TEMP") ?: System.getProperty("java.io.tmpdir"), "reconnaissance-release.keystore")
        tmp.writeBytes(Base64.getDecoder().decode(b64))
        return tmp
    }
    return dossiersSecrets.map { File(it, "reconnaissance-release.keystore") }.firstOrNull { it.exists() }
}

android {
    namespace = "fr.jeu.reconnaissance.visages"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.jeu.reconnaissance.visages"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "1.4.1"
    }

    val ks = releaseKeystore()
    signingConfigs {
        if (ks != null && storePasswordValue != null) {
            create("release") {
                storeFile = ks
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
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
        buildConfig = true
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
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Tests (org.json n'est qu'un stub dans les tests JVM d'Android)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}
