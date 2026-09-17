plugins {
    alias(mihonx.plugins.kotlin.multiplatform)
    alias(mihonx.plugins.spotless)
}

kotlin {
    android {
        namespace = "tachiyomi.core.shared"
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }

        val jvmLikeMainDirectory = "src/jvmLikeMain/kotlin"

        androidMain {
            kotlin.srcDir(jvmLikeMainDirectory)
            dependencies {
                api(libs.rxJava)
            }
        }

        jvmMain {
            kotlin.srcDir(jvmLikeMainDirectory)
            dependencies {
                api(libs.rxJava)
            }
        }
    }
}
