plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose") version "1.12.0"
    id("dev.icerock.mobile.multiplatform-resources")
    alias(libs.plugins.metro)
    alias(mihonx.plugins.spotless)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(projects.i18n)
                implementation(projects.platformApi)
                implementation(projects.platformDesktop)
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(libs.metro.runtime)
                implementation("dev.icerock.moko:resources-compose:0.26.4")
            }
        }
    }
}

multiplatformResources {
    resourcesPackage.set("mihon.desktop.resources")
}

compose.desktop {
    application {
        mainClass = "mihon.desktop.MainKt"
    }
}
