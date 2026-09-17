plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(mihonx.plugins.spotless)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(mihonx.versions.java.get().toInt())
}

dependencies {
    api(projects.core.networkApi)
    implementation(projects.platformApi)

    implementation(libs.kotlinx.serialization.json)
    implementation("com.microsoft.playwright:playwright:1.63.0")

    testImplementation(projects.sourceApi)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.bundles.test)
    testImplementation("com.squareup.okhttp3:mockwebserver3:5.5.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:5.5.0")
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
