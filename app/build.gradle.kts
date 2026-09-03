plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "no.skiltvarsler"
    compileSdk = 35

    defaultConfig {
        applicationId = "no.skiltvarsler"
        minSdk = 29
        targetSdk = 35
        versionCode = 15
        versionName = "0.1.14"
        val tileBaseUrl = (project.findProperty("tileBaseUrl") as String?)
            ?: "https://github.com/OlekOlaisen/skilt-varsler/releases/latest/download"
        buildConfigField("String", "TILE_BASE_URL", "\"$tileBaseUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
    debugImplementation(libs.androidx.compose.ui.tooling)
}
