plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

import java.util.Properties

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseKeystore = keystorePropertiesFile.exists() &&
    keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "no.skiltvarsler"
    compileSdk = 35

    defaultConfig {
        applicationId = "no.skiltvarsler"
        minSdk = 29
        targetSdk = 35
        versionCode = 23
        versionName = "0.1.22"
        val tileBaseUrl = (project.findProperty("tileBaseUrl") as String?)
            ?: "https://github.com/OlekOlaisen/skilt-varsler/releases/latest/download"
        buildConfigField("String", "TILE_BASE_URL", "\"$tileBaseUrl\"")
        buildConfigField(
            "String",
            "PRIVACY_POLICY_URL",
            "\"https://github.com/OlekOlaisen/skilt-varsler/blob/main/docs/privacy-policy.md\"",
        )
        // Override release with -PrevenueCatApiKey=goog_… before Play upload.
        // A test_ key must never ship in a Play store build.
        val revenueCatApiKey = (project.findProperty("revenueCatApiKey") as String?)
            ?: "test_sYGPTYlfHUQcBegZvWkQastMrmF"
        buildConfigField("String", "REVENUECAT_API_KEY", "\"$revenueCatApiKey\"")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                // Dev fallback only — Play uploads require keystore.properties.
                signingConfigs.getByName("debug")
            }
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

    lint {
        disable += "InvalidFragmentVersionForActivityResult"
    }
}

dependencies {
    implementation(project(":matcher")) {
        exclude(group = "org.xerial", module = "sqlite-jdbc")
    }
    implementation(project(":tiles")) {
        exclude(group = "org.xerial", module = "sqlite-jdbc")
    }
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.location)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.car.app.projected)
    implementation(libs.androidsvg)
    implementation(libs.revenuecat.purchases)
    implementation(libs.revenuecat.purchases.ui)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
