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
                implementation(projects.data.shared)
                implementation(projects.core.extensionDesktop)
                implementation(projects.core.networkDesktop)
                implementation(projects.domain.shared)
                implementation(projects.sourceApi)
                implementation(projects.sourceLocalDesktop)
                implementation(projects.i18n)
                implementation(projects.platformApi)
                implementation(projects.platformDesktop)
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(libs.metro.runtime)
                implementation(libs.okhttp.core)
                implementation("dev.icerock.moko:resources-compose:0.26.4")
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.bundles.test)
                implementation(libs.kotlinx.coroutines.test)
                runtimeOnly(libs.junit.platform.launcher)
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

tasks.named<Test>("desktopTest") {
    useJUnitPlatform()
}
