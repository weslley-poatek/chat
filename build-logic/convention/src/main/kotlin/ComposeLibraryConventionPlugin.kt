import extensions.configureComposeMultiplatform
import extensions.configureComposeResources
import extensions.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Convention for the Compose Multiplatform UI of a library module: the shared UI stack, its test
 * dependencies and the generated-resource naming.
 *
 * Apply it after `chat.multiplatform.library`, which owns the Android and iOS targets this reads.
 * The raw KMP and Android library ids are applied here too, so the extension and the
 * `androidRuntimeClasspath` configuration exist whatever the order — a no-op once the base has run.
 */
class ComposeLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = libs.plugins.kotlin.multiplatform.get().pluginId)
        apply(plugin = libs.plugins.android.multiplatform.library.get().pluginId)
        apply(plugin = libs.plugins.compose.multiplatform.get().pluginId)
        apply(plugin = libs.plugins.compose.compiler.get().pluginId)

        extensions.configure<KotlinMultiplatformExtension>(::configureComposeMultiplatform)
        configureComposeResources()
    }
}
