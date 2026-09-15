plugins {
    alias(mihonx.plugins.kotlin.multiplatform)
    alias(mihonx.plugins.spotless)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "tachiyomi.core.metadata"
    }

    sourceSets {
        val jvmLikeMainDirectory = "src/main/java"

        androidMain {
            kotlin.srcDir(jvmLikeMainDirectory)
            dependencies {
                implementation(projects.sourceApi)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.xmlutil.serialization)
            }
        }

        jvmMain {
            kotlin.srcDir(jvmLikeMainDirectory)
            dependencies {
                implementation(projects.sourceApi)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.xmlutil.serialization)
            }
        }
    }
}
