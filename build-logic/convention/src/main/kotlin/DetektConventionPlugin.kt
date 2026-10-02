import extensions.detektPlugins
import extensions.libs
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/**
 * Build-wide Detekt policy, applied beside a module's own convention rather than instead of it: it
 * never applies Android, JVM or Kotlin Multiplatform plugins itself.
 *
 * The analysed sources are every `.kt` file under the module's `src`, so whichever source sets the
 * module has are covered without naming targets. `autoCorrect` is on, which means `detekt` and
 * `check` may rewrite source files.
 */
class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = libs.plugins.detekt.get().pluginId)

            extensions.configure<DetektExtension> {
                buildUponDefaultConfig = true
                toolVersion = libs.versions.detekt.get()
                config.setFrom(files("$rootDir/detekt.yml"))
                parallel = true
                autoCorrect = true
                source.setFrom(fileTree("src") {
                    include("**/*.kt")
                })
            }

            tasks.withType<Detekt>().configureEach {
                exclude(
                    "**/.gradle/**",
                    "**/.idea/**",
                    "**/build/**",
                    ".github/**",
                    "gradle/**",
                )
                reports {
                    html.required.set(true)
                }
            }

            dependencies {
                detektPlugins(libs.detekt.formatting)
            }
        }
    }
}
