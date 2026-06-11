plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

val appVersionCode = providers.gradleProperty("APP_VERSION_CODE").orElse("1").get().toInt()
val appVersionName = providers.gradleProperty("APP_VERSION_NAME").orElse("1.0").get()
val githubOwner = providers.gradleProperty("GITHUB_OWNER").orElse("REPLACE_ME").get()
val githubRepo = providers.gradleProperty("GITHUB_REPO").orElse("REPLACE_ME").get()
val keystoreFilePath = providers.environmentVariable("ANDROID_KEYSTORE_FILE").orNull
val keystorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
val keystoreAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
val keystoreKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
val hasReleaseSigning = !keystoreFilePath.isNullOrBlank() &&
    !keystorePassword.isNullOrBlank() &&
    !keystoreAlias.isNullOrBlank() &&
    !keystoreKeyPassword.isNullOrBlank()

android {
    namespace = "de.marvin.wannundwo"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "de.marvin.wannundwo"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        resValue("string", "github_owner", githubOwner)
        resValue("string", "github_repo", githubRepo)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(keystoreFilePath!!)
                storePassword = keystorePassword
                keyAlias = keystoreAlias
                keyPassword = keystoreKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
    lint {
        lintConfig = file("lint.xml")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.functions)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.play.services)

    // QR Code
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.zxing.core)

    // Camera (for QR Scanner)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // Permissions
    implementation(libs.accompanist.permissions)
    implementation(libs.material.icons.extended)
}