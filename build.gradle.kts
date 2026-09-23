buildscript {
    dependencies {
        classpath(libs.kotlin.gradle)
    }
}

plugins {
    alias(libs.plugins.aboutLibraries) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineProfile) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.metro) apply false
    alias(libs.plugins.moko.resources) apply false
    alias(libs.plugins.sqldelight) apply false

    alias(mihonx.plugins.spotless)
}

val buildLogic: IncludedBuild = gradle.includedBuild("build-logic")
tasks {
    listOf("clean", "spotlessApply", "spotlessCheck").forEach { task ->
        named(task) {
            dependsOn(buildLogic.task(":$task"))
        }
    }
}

if (providers.gradleProperty("mihon.workspace.validation").orNull == "true") {
    subprojects {
        tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
            systemProperty("user.home", System.getProperty("user.home"))
            systemProperty("java.io.tmpdir", System.getProperty("java.io.tmpdir"))
            systemProperty("mihon.desktop.home", System.getenv("MIHON_DESKTOP_HOME"))
            systemProperty("mihon.browser.profile", System.getenv("MIHON_BROWSER_PROFILE"))
            systemProperty("android.sdk.path", System.getenv("ANDROID_HOME"))
        }
    }
}
