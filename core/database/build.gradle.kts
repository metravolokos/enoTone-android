plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}
android {
    namespace = "app.enotone.core.database"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    testOptions { unitTests.isIncludeAndroidResources = true }
    // Robolectric migration helper reads the committed v1–v4 schema as debug assets.
    sourceSets.getByName("debug").assets.srcDir("schemas")
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
    api(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.coroutines.test)
}
ksp {
    // Room schema 1 must be checked in once generated and validated by CI.
    arg("room.schemaLocation", file("schemas").absolutePath)
}
