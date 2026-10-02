import com.android.build.api.dsl.ApplicationExtension
import extensions.configureKoinAndroidApplication
import extensions.configureKoinMultiplatform
import extensions.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.findByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Convention for a module that uses Koin: the compiler plugin, plus the dependencies of whichever
 * module type it finds. Each type registers exactly one of the extensions below, so exactly one
 * branch runs per module.
 *
 * It never applies Kotlin or Android itself — the module-type convention does — so apply it after
 * that one. `findByType` checks once, when this is applied: listed first, it adds no dependencies.
 */
class KoinConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = libs.plugins.koin.compiler.get().pluginId)

            extensions.findByType<KotlinMultiplatformExtension>()?.let(::configureKoinMultiplatform)
            extensions.findByType<ApplicationExtension>()?.apply {
                configureKoinAndroidApplication()
            }
        }
    }
}
