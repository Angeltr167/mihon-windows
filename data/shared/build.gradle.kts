plugins {
    alias(mihonx.plugins.kotlin.multiplatform)
    alias(mihonx.plugins.spotless)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    android {
        namespace = "tachiyomi.data.shared"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.sourceApi)
            implementation(libs.kotlinx.serialization.json)
            implementation("app.cash.sqldelight:runtime:2.3.2")
        }

        jvmMain.dependencies {
            implementation("app.cash.sqldelight:sqlite-driver:2.3.2")
        }

        jvmTest.dependencies {
            implementation(libs.bundles.test)
            runtimeOnly(libs.junit.platform.launcher)
        }
    }
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("mihon.desktop.data")
            dialect(libs.sqldelight.sqliteDialect338)
            srcDirs.setFrom(rootProject.file("data/src/main/sqldelight"))
        }
    }
}
