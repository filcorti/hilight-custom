import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * Release signing material, from `key.properties` at the repo root or the matching env vars.
 *
 * Both are outside version control (see .gitignore), so the keystore and its passwords never land in
 * the repository. A clone without them still builds — the release APK just comes out unsigned.
 */
val releaseKeystore = Properties().apply {
    val f = rootProject.file("key.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(key: String, env: String): String? =
    releaseKeystore.getProperty(key) ?: System.getenv(env)

android {
    namespace = "com.hilight.studio"
    // Match the F-Droid build recipe even when a newer Android 37 minor SDK is installed locally.
    compileSdk { version = release(37) { minorApiLevel = 0 } }

    defaultConfig {
        applicationId = "com.hilight.studio"
        // HiLight is a Pixel 11 / Android 17 feature. Keeping this floor aligned with the
        // supported hardware prevents installation on devices the renderer cannot support.
        minSdk = 37
        targetSdk = 37
        versionCode = 19
        versionName = "1.0.17"
    }

    signingConfigs {
        create("shared") {
            storeFile = file("shared.keystore")
            storePassword = "androiddebugkey"
            keyAlias = "androiddebugkey"
            keyPassword = "androiddebugkey"
        }
        create("release") {
            val store = signingValue("storeFile", "HILIGHT_STORE_FILE")
            if (store != null) {
                storeFile = file(store)
                storePassword = signingValue("storePassword", "HILIGHT_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "HILIGHT_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "HILIGHT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }

    sourceSets {
        // The renderer core is shared with the adb host, which is compiled separately into a dex by
        // scripts/build-helper.sh. Including it here also means AdbHelper ships inside the APK, so
        // the adb command can run straight out of the installed app with nothing to push.
        getByName("main").java.srcDir("../core/src")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    testImplementation("junit:junit:4.13.2")
    // Android's org.json methods are framework stubs in local JVM tests; this supplies the real
    // implementation for preference/state round-trip tests and is not packaged in the APK.
    testImplementation("org.json:json:20240303")
}