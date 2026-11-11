plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt)
}

// Static analysis for every module (ADR-0074, M6-0/8). Report-only for now:
// the existing code has never been checked, so failures would block all work.
// Findings are fixed module by module during M6-0/4–5, then ignoreFailures
// is switched off by a separate PR.
allprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    detekt {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        ignoreFailures = true
        parallel = true
    }
    dependencies {
        "detektPlugins"(rootProject.libs.detekt.formatting)
    }
}
