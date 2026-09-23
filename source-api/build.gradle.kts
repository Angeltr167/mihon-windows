plugins {
    alias(mihonx.plugins.kotlin.multiplatform)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "eu.kanade.tachiyomi.source"
    }

    sourceSets {
        val jvmLikeMainDirectory = "src/jvmLikeMain/kotlin"

        androidMain {
            kotlin.srcDir(jvmLikeMainDirectory)
            dependencies {
                api(projects.core.shared)
                implementation(projects.core.common)

                implementation(libs.kotlinx.serialization.json)
                implementation(libs.injekt)
                implementation(libs.rxJava)
                implementation(libs.jsoup)
                implementation(libs.androidx.preference)
            }
        }

        jvmMain {
            kotlin.srcDir(jvmLikeMainDirectory)
            dependencies {
                api(projects.core.shared)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.rxJava)
                implementation(libs.okhttp.core)
                implementation(libs.jsoup)
            }
        }

        jvmTest.dependencies {
            implementation(libs.bundles.test)
            runtimeOnly(libs.junit.platform.launcher)
        }
    }
}
