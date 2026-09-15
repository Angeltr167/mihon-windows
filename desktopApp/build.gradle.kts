plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    id("org.jetbrains.compose") version "1.12.0"
    alias(libs.plugins.moko.resources)
    alias(mihonx.plugins.spotless)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(projects.i18n)
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
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
