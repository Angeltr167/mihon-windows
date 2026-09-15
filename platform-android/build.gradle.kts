plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.spotless)
    alias(libs.plugins.metro)
}

android {
    namespace = "mihon.platform.android"
}

dependencies {
    implementation(projects.platformApi)
    implementation(libs.metro.runtime)
}
