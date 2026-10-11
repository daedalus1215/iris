plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

fun serverUrl(name: String) = "\"${providers.gradleProperty(name).get()}\""

android {
    namespace = "io.github.daedalus1215.iris"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.daedalus1215.iris"
        minSdk = 26
        targetSdk = 37
        // CI numbers each build, so a newer APK always installs over an older one.
        versionCode = providers.environmentVariable("GITHUB_RUN_NUMBER").map(String::toInt).getOrElse(1)
        versionName = "0.1.0"
    }

    signingConfigs {
        getByName("debug") {
            // One shared debug key, so every CI build can install over the previous one.
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            buildConfigField("String", "DEFAULT_SERVER_URL", serverUrl("iris.devServerUrl"))
        }
        create("prod") {
            dimension = "env"
            buildConfigField("String", "DEFAULT_SERVER_URL", serverUrl("iris.prodServerUrl"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("io.github.daedalus1215.iris:core")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
}
