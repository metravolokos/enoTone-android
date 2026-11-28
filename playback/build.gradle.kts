plugins {
    alias(libs.plugins.android.library)
}
android {
    namespace = "app.enotone.playback"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.datasource)
    implementation(libs.media3.database)
    implementation(libs.media3.session)
    implementation(libs.coroutines.android)
    // Platform-independent JSON codec for JVM unit tests and Android runtime.
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
