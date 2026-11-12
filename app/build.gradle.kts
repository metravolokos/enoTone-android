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
        // Monotonic across GitHub Actions runs, including the stable Preview channel.
        // Local builds retain a harmless initial versionCode.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        // 0.<milestone>.<patch> until the first public release 1.0.0 (ADR-0078).
        // MINOR = current roadmap milestone; PATCH = bumped by hand when the
        // owner accepts a result on Huawei. Build number is appended per variant.
        versionName = "0.6.1"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    signingConfigs {
        create("stablePreview") {
            // Never use a CI runner's ephemeral debug certificate for in-place
            // upgrades. The PKCS12 private key is supplied only from Actions
            // secrets, never committed or included in the output APK.
            val path = System.getenv("ENOTONE_PREVIEW_KEYSTORE_PATH")
            if (!path.isNullOrBlank()) storeFile = file(path)
            storePassword = System.getenv("ENOTONE_PREVIEW_KEYSTORE_PASSWORD")
            keyAlias = "enotone-preview"
            keyPassword = System.getenv("ENOTONE_PREVIEW_KEYSTORE_PASSWORD")
            storeType = "PKCS12"
        }
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
        create("stablePreview") {
            // Distinct from release and from legacy .preview.r<N> debug builds.
            // Subsequent stablePreview builds have the SAME package and cert.
            initWith(getByName("release"))
            // The library modules only publish debug/release variants.
            // Without an explicit fallback AGP cannot resolve :playback et al.
            matchingFallbacks += listOf("release", "debug")
            applicationIdSuffix = ".preview"
            // e.g. 0.6.0-preview.425 — the CI run number identifies the exact APK.
            val stableRun = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
            versionNameSuffix = "-preview.$stableRun"
            signingConfig = signingConfigs.getByName("stablePreview")
            isDebuggable = false
        }
    }
    // Fail closed even when a developer requests assembleStablePreview locally.
    if (gradle.startParameter.taskNames.any { requested ->
        listOf("assembleStablePreview", "packageStablePreview", "bundleStablePreview",
            "installStablePreview").any { requested.endsWith(it, ignoreCase = true) }
    }) {
        require(!System.getenv("ENOTONE_PREVIEW_KEYSTORE_PATH").isNullOrBlank() &&
            !System.getenv("ENOTONE_PREVIEW_KEYSTORE_PASSWORD").isNullOrBlank()) {
            "Stable Preview signing secrets are required; unsigned builds are forbidden"
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
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.coroutines.android)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
