pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "cruise"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:designsystem")
include(":core:model")
include(":core:data")
include(":core:audio")
include(":core:ui")
include(":core:car")
include(":core:playback")
include(":feature:settings")
include(":feature:folders")
include(":feature:nowplaying")
include(":feature:library")
include(":feature:sound")
include(":feature:voice")
