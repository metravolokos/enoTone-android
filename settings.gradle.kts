pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Only in CI: verified Kotlin compiler jar preloaded to avoid a Maven CDN 504.
        // All unrelated modules continue to resolve from Google / Maven Central.
        val compilerMirror = System.getenv("ENOTONE_CI_COMPILER_MIRROR")
        if (!compilerMirror.isNullOrBlank()) {
            maven {
                url = uri(compilerMirror)
                content { includeModule("org.jetbrains.kotlin", "kotlin-compiler-embeddable") }
            }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "enoTone-android"
include(":app")
include(":core:model", ":core:database", ":core:network")
include(":data:library", ":data:playlist")
include(":playback")
include(":feature:library", ":feature:player", ":feature:settings")
