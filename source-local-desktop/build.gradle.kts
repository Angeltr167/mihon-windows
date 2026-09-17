plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(mihonx.plugins.spotless)
}

kotlin {
    jvmToolchain(mihonx.versions.java.get().toInt())
}

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.core.archiveDesktop)
    implementation(projects.coreMetadata)
    implementation(projects.domain.shared)

    implementation(libs.jsoup)
    implementation(libs.natural.comparator)
    implementation(libs.rxJava)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.xmlutil.serialization)

    testImplementation(libs.bundles.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
