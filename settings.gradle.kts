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
        google()
        mavenCentral()
    }
}
rootProject.name = "enoTone-android"
include(":app")
include(":core:common", ":core:model", ":core:database", ":core:network", ":core:datastore")
include(":data:library", ":data:source", ":data:cache", ":data:playlist")
include(":playback")
include(":feature:library", ":feature:player", ":feature:settings", ":feature:ai")
