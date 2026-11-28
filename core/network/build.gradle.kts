plugins {
    alias(libs.plugins.android.library)
}
android {
    namespace = "app.enotone.core.network"
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
    implementation(libs.ktor.client.cio)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver) {
        exclude(group = "com.squareup.okhttp3", module = "okhttp")
    }
    testImplementation(libs.okhttp.jvm)
}
