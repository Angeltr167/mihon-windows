plugins {
    alias(mihonx.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(mihonx.plugins.spotless)
}

kotlin {
    android {
        namespace = "mihon.backup.shared"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.protobuf)
        }

        val jvmLikeMainDirectory = "src/jvmLikeMain/kotlin"
        androidMain { kotlin.srcDir(jvmLikeMainDirectory) }
        jvmMain { kotlin.srcDir(jvmLikeMainDirectory) }

        jvmTest.dependencies {
            implementation(libs.bundles.test)
            runtimeOnly(libs.junit.platform.launcher)
        }
    }
}
