plugins {
    alias(mihonx.plugins.kotlin.multiplatform)
    alias(mihonx.plugins.spotless)
}

kotlin {
    android {
        namespace = "mihon.core.archive.api"
    }

    sourceSets {
        val jvmLikeMainDirectory = "src/jvmLikeMain/kotlin"
        androidMain { kotlin.srcDir(jvmLikeMainDirectory) }
        jvmMain { kotlin.srcDir(jvmLikeMainDirectory) }
    }
}
