@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.chat.multiplatform.library)
    alias(libs.plugins.chat.compose.library)
    alias(libs.plugins.chat.koin)
    alias(libs.plugins.chat.detekt)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.navigation)
            implementation(projects.core.ui)
            implementation(projects.data.auth)
        }
        androidMain.dependencies {
            implementation(libs.credentials)
            implementation(libs.credentials.play.services.auth)
            implementation(libs.googleid)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }

    swiftPMDependencies {
        swiftPackage(
            url = url("https://github.com/google/GoogleSignIn-iOS.git"),
            version = exact(libs.versions.google.signin.ios.get()),
            products = listOf(
                product(name = "GoogleSignIn", platforms = setOf(iOS())),
            ),
        )
    }
}
