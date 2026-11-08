plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "app.enotone.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.enotone.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1-m0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        // AndroidX Activity ships an unused optional Google Play Photo Picker action.
        // R8 removes unused code; the APK GMS gate remains strict and unmodified.
        getByName("debug") {
            // CI debug signing is generated independently by each runner. Until a stable
            // private signing key is configured, test APKs MUST NOT claim the old app ID.
            // Protect the original on-device Room database by installing side-by-side.
            // Each CI run uses a distinct package until a protected stable signing key exists.
            // Never make Android compare certificates from unrelated ephemeral runners.
            val previewRun = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
            applicationIdSuffix = ".preview.r$previewRun"
            versionNameSuffix = "-preview.r$previewRun"
            manifestPlaceholders["previewLabel"] = "enoTone Preview #$previewRun"
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":data:library"))
    implementation(project(":playback"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
}
