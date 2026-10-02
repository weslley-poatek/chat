package extensions

import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler

/**
 * Gradle generates the `implementation(...)` accessors for build scripts only, so a plugin class has
 * to go through `add(...)`. Spelling the configuration name once here keeps a misspelling from
 * surfacing as an unknown-configuration failure at configuration time.
 */
internal fun DependencyHandler.implementation(dependency: Any): Dependency? =
    add("implementation", dependency)

/**
 * Only where the Android KMP library plugin has run: it is what creates this configuration.
 */
internal fun DependencyHandler.androidRuntimeClasspath(dependency: Any): Dependency? =
    add("androidRuntimeClasspath", dependency)
