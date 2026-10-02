package extensions

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * The shared Compose Multiplatform dependencies of a library module.
 *
 * The Android and iOS targets are guaranteed by the base library convention, so their source sets
 * are read directly. There is no JVM target, hence no `jvmTest` runtime for `runComposeUiTest`.
 */
internal fun Project.configureComposeMultiplatform(extension: KotlinMultiplatformExtension) {
    extension.apply {
        sourceSets.apply {
            commonMain.dependencies {
                // Reactive state model, recomposition and effects.
                implementation(libs.compose.runtime)
                // Layout, gestures, scrolling and focus.
                implementation(libs.compose.foundation)
                // Graphics primitives and the drawing surface.
                implementation(libs.compose.ui)
                // Layout and geometry helpers that custom components lean on.
                implementation(libs.compose.ui.util)
                // Transitions, animated values and the enter and exit APIs.
                implementation(libs.compose.animation)
                // The ready-made component set and theming.
                implementation(libs.compose.material3)
                // Multiplatform strings, images and fonts, and the generated `Res` class that reads them.
                implementation(libs.compose.components.resources)
                // The `@Preview` annotation itself, needed wherever a preview is declared.
                implementation(libs.compose.ui.tooling.preview)
                // `collectAsStateWithLifecycle` and the lifecycle-aware effects.
                implementation(libs.lifecycle.runtime.compose)
                // `viewModel()` from a composable, with its scope handled.
                implementation(libs.lifecycle.viewmodel.compose)
                // The back stack as a snapshot-state list the caller owns, and the entries that map a key to content.
                implementation(libs.navigation3.runtime)
                // `NavDisplay`, which renders the top of that back stack with transitions and predictive back.
                implementation(libs.navigation3.ui)
                // A ViewModel scoped to a navigation entry rather than to the screen that shows it.
                implementation(libs.lifecycle.viewmodel.navigation3)
            }

            commonTest.dependencies {
                // `@Test` and the assertion functions.
                implementation(libs.kotlin.test)
                // Semantics matchers, test rules and `runComposeUiTest`.
                implementation(libs.compose.ui.test)
            }
        }
    }

    // `@Preview` rendering in the IDE, as opposed to the annotation: Android runtime classpath only,
    // so it stays out of the compile classpath and of anything released.
    dependencies.androidRuntimeClasspath(libs.compose.ui.tooling)
}

/**
 * Names the generated `Res` class and its package after the module path, so a screen importing
 * resources from two modules never has to disambiguate by hand. The package follows the Android
 * namespace and so inherits its validation.
 *
 * `:app` becomes `br.com.weslleycampos.chat.app.resources.AppRes`. The class stays internal: no
 * module consumes another's resources, and `publicResClass` is the switch for when one does.
 */
internal fun Project.configureComposeResources() {
    extensions.getByType<ComposeExtension>().extensions.configure<ResourcesExtension> {
        nameOfResClass = path.toResClassName()
        packageOfResClass = "$androidNamespace.resources"
        // The default, spelled out so the policy reads in one place: it keys on the explicit
        // `components-resources` dependency above, not on a `composeResources/` directory.
        generateResClass = auto
    }
}

/** `:feature:home` becomes `FeatureHomeRes` — unique per module, and readable at the use site. */
private fun String.toResClassName(): String = split(":")
    .filter(String::isNotEmpty)
    .joinToString("", postfix = "Res") { it.replaceFirstChar(Char::uppercaseChar) }
