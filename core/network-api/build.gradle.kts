plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(mihonx.plugins.spotless)
}

kotlin {
    jvmToolchain(mihonx.versions.java.get().toInt())
}

dependencies {
    api(libs.okhttp.core)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
