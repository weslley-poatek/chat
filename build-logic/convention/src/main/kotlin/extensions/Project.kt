package extensions

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.the
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Typed access to gradle/libs.versions.toml. Gradle injects `libs` into build scripts but not into
 * plugin classes, so this is the single place that bridges it — every convention plugin reads the
 * catalog through here. See the `enableTypeAccessors` note in build-logic/convention/build.gradle.kts.
 */
internal val Project.libs: LibrariesForLibs get() = the<LibrariesForLibs>()

/**
 * The bytecode level the app ships, from the `jvm-target` catalog entry. Named to not collide with
 * the `jvmTarget` property inside Kotlin's `compilerOptions { }`, where this is read.
 */
internal val Project.kotlinJvmTarget: JvmTarget
    get() = JvmTarget.fromTarget(libs.versions.jvm.target.get())

/** The same level as a [JavaVersion], for AGP's `compileOptions`. */
internal val Project.javaCompatibility: JavaVersion
    get() = JavaVersion.toVersion(libs.versions.jvm.target.get())
