pluginManagement {
    includeBuild("gradle/build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven(url = "https://www.jitpack.io")
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("mihonx") {
            from(files("gradle/mihon.versions.toml"))
        }
    }

    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
        maven(url = "https://www.jitpack.io")
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "Mihon"
include(":app")
include(":backup-shared")
include(":baseline-profile")
include(":core-metadata")
include(":core:archive")
include(":core:archive-api")
include(":core:archive-desktop")
include(":core:common")
include(":core:metro")
include(":core:shared")
include(":data")
include(":data:shared")
include(":desktopApp")
include(":domain")
include(":domain:shared")
include(":i18n")
include(":icons:material-symbols")
include(":icons:simple-icons")
include(":platform-api")
include(":platform-android")
include(":platform-desktop")
include(":presentation-core")
include(":presentation-widget")
include(":source-api")
include(":source-local")
include(":source-local-desktop")
include(":telemetry")
