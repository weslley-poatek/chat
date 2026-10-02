package extensions

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import javax.lang.model.SourceVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * The package root of the whole build and the base of every module's [androidNamespace].
 */
internal const val ROOT_PACKAGE = "br.com.weslleycampos.chat"

/** Licence files that several dependencies ship and that must not collide when packaged. */
private const val EXCLUDED_RESOURCES = "/META-INF/{AL2.0,LGPL2.1}"

/**
 * The Android defaults for a module on the classic AGP DSL — `com.android.application`, which
 * creates a [CommonExtension].
 *
 * `targetSdk`, the application identity and the release build type are not here: they exist on the
 * application DSL alone, so the application convention plugin keeps them.
 */
internal fun Project.configureAndroid() {
    extensions.configure<CommonExtension> {
        namespace = androidNamespace
        compileSdk = androidCompileSdk
        defaultConfig.minSdk = androidMinSdk

        compileOptions.apply {
            sourceCompatibility = javaCompatibility
            targetCompatibility = javaCompatibility
        }
        packaging.resources.excludes += EXCLUDED_RESOURCES
    }

    // AGP 9 provides the project-level `kotlin` extension through its built-in Kotlin support.
    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget.set(kotlinJvmTarget)
        }
    }
}

/**
 * The Android defaults for a Kotlin Multiplatform library — everything a multiplatform module needs,
 * so its convention plugin has nothing left to configure.
 *
 * Separate from [configureAndroid] because this DSL is a Kotlin target, not a project extension.
 */
internal fun Project.configureLibrary(extension: KotlinMultiplatformExtension) {
    extension.apply {
        targets.withType<KotlinMultiplatformAndroidLibraryTarget>().configureEach {
            namespace = androidNamespace
            compileSdk = androidCompileSdk
            minSdk = androidMinSdk

            // No `compileOptions` counterpart: this DSL has none, since it compiles no Java unless
            // the module calls `withJava()`. The JVM target comes off the target itself.
            compilerOptions {
                jvmTarget.set(kotlinJvmTarget)
            }

            androidResources {
                enable = true
            }
            withHostTest {
                isIncludeAndroidResources = true
            }
            withDeviceTestBuilder {
                sourceSetTreeName = "test"
            }.configure {
                instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
        }
    }
}

/**
 * The module's Android namespace — the package of its generated `R`, and what relative names in
 * AndroidManifest.xml resolve against.
 *
 * Every module appends its Gradle path, so `:app` is `<root.package>.app` and `:feature:home` is
 * `<root.package>.feature.home`. The sole application overrides its namespace with [ROOT_PACKAGE].
 */
internal val Project.androidNamespace: String
    get() = (listOf(ROOT_PACKAGE) + path.split(":").filter(String::isNotEmpty).map { packageSegment(it) })
        .joinToString(".")

/** Preserve valid segments exactly; reject anything that would need a lossy rewrite. */
private fun Project.packageSegment(pathSegment: String): String {
    require(SourceVersion.isIdentifier(pathSegment) && !SourceVersion.isKeyword(pathSegment)) {
        "Cannot derive Android namespace for $path: '$pathSegment' is not a valid Java package identifier"
    }
    return pathSegment
}

internal val Project.androidCompileSdk: Int
    get() = libs.versions.android.compile.sdk.get().toInt()

internal val Project.androidMinSdk: Int
    get() = libs.versions.android.min.sdk.get().toInt()

internal val Project.androidTargetSdk: Int
    get() = libs.versions.android.target.sdk.get().toInt()

internal val Project.appVersionCode: Int
    get() = libs.versions.app.version.code.get().toInt()

internal val Project.appVersionName: String
    get() = libs.versions.app.version.name.get()
