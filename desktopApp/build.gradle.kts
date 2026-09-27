import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

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
        val windowsVersion = providers.gradleProperty("mihonWindowsVersion").orElse("1.0.10").get()
        mainClass = "mihon.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            appResourcesRootDir.set(layout.buildDirectory.dir("suwayomi-resources"))
            packageName = "Mihon"
            packageVersion = windowsVersion
            description = "Mihon manga reader for Windows"
            vendor = "Mihon"
            licenseFile.set(rootProject.file("LICENSE"))
            includeAllModules = true

            windows {
                packageVersion = windowsVersion
                msiPackageVersion = windowsVersion
                exePackageVersion = windowsVersion
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

val suwayomiVersion = "v2.3.2243"
val suwayomiSha256 = "821141b32e170d4a02d3cbdfed577ed8f07bd22383ff5f4132ebb5ae40e98dd5"
// Compose places .jar app resources on Mihon's classpath; keep this sidecar archive isolated.
val suwayomiOutput = layout.buildDirectory.file("suwayomi-resources/common/suwayomi-server.bin")
val suwayomiLicenseSha256 = "3f3d9e0024b1921b067d6f7f88deb4a60cbe7a78e76c64e3f1d7fc3b779b9d04"
val suwayomiLicense = layout.buildDirectory.file("suwayomi-resources/common/suwayomi-LICENSE.txt")

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val size = input.read(buffer)
            if (size < 0) break
            digest.update(buffer, 0, size)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

tasks.register("prepareSuwayomiEngine") {
    outputs.files(suwayomiOutput, suwayomiLicense)
    outputs.upToDateWhen {
        val jar = suwayomiOutput.get().asFile
        val license = suwayomiLicense.get().asFile
        jar.isFile && sha256(jar) == suwayomiSha256 &&
            license.isFile && sha256(license) == suwayomiLicenseSha256
    }
    doLast {
        val downloads = listOf(
            Triple(
                suwayomiOutput.get().asFile,
                "https://github.com/Suwayomi/Suwayomi-Server/releases/download/$suwayomiVersion/" +
                    "Suwayomi-Server-$suwayomiVersion.jar",
                suwayomiSha256,
            ),
            Triple(
                suwayomiLicense.get().asFile,
                "https://raw.githubusercontent.com/Suwayomi/Suwayomi-Server/$suwayomiVersion/LICENSE",
                suwayomiLicenseSha256,
            ),
        )
        downloads.forEach { (target, url, expectedHash) ->
            if (target.isFile && sha256(target) == expectedHash) return@forEach
            target.parentFile.mkdirs()
            val temporary = Files.createTempFile(target.parentFile.toPath(), "suwayomi-", ".tmp")
            try {
                URL(url).openStream().use { input ->
                    Files.newOutputStream(temporary).use { output -> input.copyTo(output) }
                }
                require(sha256(temporary.toFile()) == expectedHash) {
                    "Suwayomi resource checksum does not match the pinned release: $url"
                }
                Files.move(temporary, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
    }
}

tasks.matching { it.name == "prepareAppResources" }.configureEach {
    dependsOn("prepareSuwayomiEngine")
}

val includePackagedJavaw = tasks.register("includePackagedJavaw") {
    val sourceJavaw = File(System.getProperty("java.home"), "bin/javaw.exe")
    val packagedJavaw = layout.buildDirectory.file("compose/tmp/main/runtime/bin/javaw.exe")
    inputs.file(sourceJavaw)
    outputs.file(packagedJavaw)
    dependsOn(tasks.named("createRuntimeImage"))
    onlyIf { System.getProperty("os.name").startsWith("Windows", ignoreCase = true) }
    doLast {
        require(sourceJavaw.isFile) { "JDK javaw.exe is required to package the Windows extension engine" }
        val target = packagedJavaw.get().asFile
        target.parentFile.mkdirs()
        Files.copy(sourceJavaw.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

tasks.matching { it.name == "createDistributable" }.configureEach {
    dependsOn(includePackagedJavaw)
}

tasks.matching { it.name == "packageExe" || it.name == "packageMsi" }.configureEach {
    dependsOn(includePackagedJavaw)
}
