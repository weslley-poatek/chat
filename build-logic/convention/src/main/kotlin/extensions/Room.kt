package extensions

import androidx.room3.gradle.RoomExtension
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

/**
 * Where Room writes `<database class>/<version>.json` for every `@Database(exportSchema = true)`.
 * The files are committed: auto-migrations are generated from them, so one that is lost or edited
 * after release makes the next migration impossible to derive.
 */
private const val SCHEMA_DIRECTORY = "schemas"

/**
 * The environment variable that hands tests the absolute path of [SCHEMA_DIRECTORY]. The iOS
 * simulator only passes variables prefixed with `SIMCTL_CHILD_` on to the test process, without
 * the prefix.
 */
private const val SCHEMA_DIRECTORY_VARIABLE = "ROOM_SCHEMA_DIRECTORY"
private const val SIMULATOR_VARIABLE_PREFIX = "SIMCTL_CHILD_"

/** The Android host tests' runtime classpath, created by the Android KMP library plugin. */
private const val ANDROID_HOST_TEST_RUNTIME_CLASSPATH = "androidHostTestRuntimeClasspath"

internal fun Project.configureRoomSchemas(extension: RoomExtension) {
    extension.schemaDirectory(layout.projectDirectory.dir(SCHEMA_DIRECTORY))
}

/**
 * The Room runtime and the SQLite driver every database is built with, in `commonMain`.
 *
 * The runtime is `api`: a module's database extends `RoomDatabase`, and a consumer cannot call a
 * DAO getter on a class whose supertype it cannot see. The driver stays `implementation`, since only
 * the database builders in the owning module name it.
 *
 * Room finds the generated database through an `expect object` that `@ConstructedBy` names, so the
 * Beta warning for expect and actual classes is switched off: every Room module has one.
 */
internal fun Project.configureRoom(extension: KotlinMultiplatformExtension) {
    extension.apply {
        compilerOptions {
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
        sourceSets.apply {
            commonMain.dependencies {
                api(libs.room.runtime)
                implementation(libs.sqlite.bundled)
            }
            // `MigrationTestHelper` for auto-migration tests. Only the native helper reads schemas
            // from a directory; the Android one needs an Instrumentation, which host tests lack.
            iosTest.dependencies {
                implementation(libs.room.testing)
            }
        }
    }
}

/**
 * The Room compiler on the KSP configuration of each target, `kspAndroid`, `kspIosArm64` and
 * `kspIosSimulatorArm64` today, so every target compiles its own generated database. The shared
 * `ksp(...)` configuration is deprecated for multiplatform projects, and the metadata target is left
 * out: it compiles no binary, so there is nothing for Room to generate there.
 */
internal fun Project.configureRoomCompiler(extension: KotlinMultiplatformExtension) {
    extension.targets
        .matching { it.platformType != KotlinPlatformType.common }
        .configureEach {
            dependencies.add("ksp${name.replaceFirstChar(Char::uppercaseChar)}", libs.room.compiler)
        }
}

/**
 * What lets one `commonTest` open real databases on the Android host and on the iOS simulator.
 *
 * - The Android artifact of the bundled driver only carries `.so` files for Android devices, so the
 *   host JVM cannot load it. Its host tests run the desktop artifact instead, which has the same
 *   classes and carries natives for macOS, Linux and Windows.
 * - Every test process gets the schema directory's absolute path in [SCHEMA_DIRECTORY_VARIABLE],
 *   since a simulator process does not start in the module directory.
 */
internal fun Project.configureRoomTests() {
    val sqliteBundledJvm = libs.sqlite.bundled.jvm.get().toString()
    configurations
        .matching { it.name == ANDROID_HOST_TEST_RUNTIME_CLASSPATH }
        .configureEach {
            resolutionStrategy.dependencySubstitution {
                substitute(module("androidx.sqlite:sqlite-bundled-android"))
                    .using(module(sqliteBundledJvm))
                    .because("The Android artifact has no natives for the host JVM")
            }
        }

    val schemaDirectory = layout.projectDirectory.dir(SCHEMA_DIRECTORY).asFile.absolutePath
    tasks.withType<Test>().configureEach {
        environment(SCHEMA_DIRECTORY_VARIABLE, schemaDirectory)
    }
    tasks.withType<KotlinNativeSimulatorTest>().configureEach {
        environment(SIMULATOR_VARIABLE_PREFIX + SCHEMA_DIRECTORY_VARIABLE, schemaDirectory)
    }
}
