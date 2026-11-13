plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.detekt)
}

// Static analysis for every module (ADR-0076). Blocking since 2026-11-13:
// existing findings live in a per-module detekt-baseline.xml that may only
// shrink (tools/check_detekt_baseline.py in CI, AUDIT_PROTOCOL §4.1).
// Any NEW finding fails the build. Regenerate a baseline only to REMOVE
// fixed entries: ./gradlew detektBaseline, then review the diff.
allprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    detekt {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        baseline = file("detekt-baseline.xml")
        ignoreFailures = false
        parallel = true
    }
    dependencies {
        "detektPlugins"(rootProject.libs.detekt.formatting)
    }
}
