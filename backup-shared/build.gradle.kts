plugins {
    alias(mihonx.plugins.kotlin.multiplatform)
    alias(mihonx.plugins.spotless)
}

kotlin {
    android {
        namespace = "mihon.backup.shared"
    }

    sourceSets {
        val jvmLikeMainDirectory = "src/jvmLikeMain/kotlin"
        androidMain { kotlin.srcDir(jvmLikeMainDirectory) }
        jvmMain { kotlin.srcDir(jvmLikeMainDirectory) }

        jvmTest.dependencies {
            implementation(libs.bundles.test)
            runtimeOnly(libs.junit.platform.launcher)
        }
    }
}
