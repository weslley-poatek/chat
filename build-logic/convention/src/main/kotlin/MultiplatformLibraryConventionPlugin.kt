import extensions.configureIosFramework
import extensions.configureLibrary
import extensions.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Convention for a Kotlin Multiplatform library module that owns Android and iOS framework targets.
 * Modules keep their own source-set dependencies.
 */
class MultiplatformLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = libs.plugins.kotlin.multiplatform.get().pluginId)
        apply(plugin = libs.plugins.android.multiplatform.library.get().pluginId)
        extensions.configure<KotlinMultiplatformExtension>(::configureLibrary)
        extensions.configure<KotlinMultiplatformExtension>(::configureIosFramework)
    }
}
