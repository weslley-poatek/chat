package extensions

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * The Koin runtime and annotations for a Kotlin Multiplatform module, in `commonMain` so every
 * target the module owns — Android and iOS included — compiles against the same definitions.
 *
 * The compiler plugin reads other modules' definitions through the annotations, so they belong on
 * every module that calls `startKoin<T>` or `get<T>()`, not only on the one declaring the graph.
 *
 * A module that compiles Compose also gets `koinInject()`, `currentKoinScope()` and `koinViewModel()`.
 * `withPlugin` waits for the Compose compiler, so the order the module lists its plugins in does not
 * matter here.
 */
internal fun Project.configureKoinMultiplatform(extension: KotlinMultiplatformExtension) {
    extension.apply {
        sourceSets.apply {
            commonMain.dependencies {
                implementation(libs.bundles.koin)
            }
        }
    }

    pluginManager.withPlugin(libs.plugins.compose.compiler.get().pluginId) {
        extension.apply {
            sourceSets.apply {
                commonMain.dependencies {
                    implementation(libs.bundles.koin.compose)
                }
            }
        }
    }
}

/**
 * The Koin dependencies of an Android application, on its ordinary `implementation` configuration.
 * `koin-android` is what supplies `androidContext(...)` at startup, so it stays out of any
 * common source set.
 */
internal fun Project.configureKoinAndroidApplication() {
    dependencies.implementation(libs.bundles.koin)
    dependencies.implementation(libs.koin.android)
}
