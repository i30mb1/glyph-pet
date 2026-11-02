import com.github.triplet.gradle.androidpublisher.ReleaseStatus

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.play.publisher)
}

// Google Play publishing: straight to production. Store listing graphics and release notes live in src/main/play/.
play {
    serviceAccountCredentials.set(rootProject.file("play-service-account.json"))
    defaultToAppBundles.set(true)
    track.set("production")
    releaseStatus.set(ReleaseStatus.COMPLETED)
}

android {
    namespace = "n7.glyphpet"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "n7.glyphpet"
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    // Upload key from ~/.gradle/gradle.properties (n7.upload.*, or the shared kcal.upload.* key).
    // Without it, release builds fall back to the debug key so a fresh clone still builds.
    fun uploadProp(name: String): String? =
        providers.gradleProperty("n7.upload.$name").orNull ?: providers.gradleProperty("kcal.upload.$name").orNull
    val uploadStore = uploadProp("storeFile")
    signingConfigs {
        if (uploadStore != null) {
            create("upload") {
                storeFile = file(uploadStore)
                storePassword = uploadProp("storePassword")
                keyAlias = uploadProp("keyAlias")
                keyPassword = uploadProp("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        compose = true
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    implementation(files("../libs/glyph-matrix-sdk-2.0.aar"))
    implementation("com.github.i30mb1:n7-glyph-ui:0.2.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
}
