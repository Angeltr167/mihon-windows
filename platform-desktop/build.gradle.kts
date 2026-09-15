plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.metro)
    alias(mihonx.plugins.spotless)
}

kotlin {
    jvmToolchain(mihonx.versions.java.get().toInt())
}

dependencies {
    implementation(projects.platformApi)
    implementation(libs.metro.runtime)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
