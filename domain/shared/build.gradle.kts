plugins {
    alias(mihonx.plugins.kotlin.multiplatform)
    alias(mihonx.plugins.spotless)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "tachiyomi.domain.shared"
    }

    sourceSets {
        val jvmLikeMainDirectory = "src/jvmLikeMain/kotlin"

        androidMain {
            kotlin.srcDir(jvmLikeMainDirectory)
            dependencies {
                api(projects.sourceApi)
                api(projects.core.shared)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        jvmMain {
            kotlin.srcDir(jvmLikeMainDirectory)
            dependencies {
                api(projects.sourceApi)
                api(projects.core.shared)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        jvmTest.dependencies {
            implementation(libs.bundles.test)
        }
    }
}
