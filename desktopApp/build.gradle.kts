import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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
                implementation(project(":backup-shared"))
                implementation(projects.core.extensionDesktop)
                implementation(projects.core.networkDesktop)
                implementation(projects.core.readerCore)
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
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.serialization.protobuf)
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
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Mihon"
            packageVersion = providers.gradleProperty("mihonWindowsVersion").orElse("1.0.0").get()
            description = "Mihon manga reader for Windows"
            vendor = "Mihon"
            licenseFile.set(rootProject.file("LICENSE"))
            includeAllModules = true

            windows {
                perUserInstall = true
                shortcut = true
                menuGroup = "Mihon"
                upgradeUuid = "c764cc56-8996-49ef-b813-1ee3815d9da2"
            }
        }
    }
}

tasks.named<Test>("desktopTest") {
    useJUnitPlatform()
}
