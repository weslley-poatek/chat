import com.android.build.api.dsl.ApplicationExtension
import extensions.ROOT_PACKAGE
import extensions.androidTargetSdk
import extensions.appVersionCode
import extensions.appVersionName
import extensions.configureAndroid
import extensions.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

/** Convention for the sole Android application module, which compiles Compose. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = libs.plugins.android.application.get().pluginId)
        apply(plugin = libs.plugins.compose.compiler.get().pluginId)
        configureAndroid()

        extensions.configure<ApplicationExtension> {
            namespace = ROOT_PACKAGE
            defaultConfig {
                applicationId = ROOT_PACKAGE
                targetSdk = androidTargetSdk
                versionCode = appVersionCode
                versionName = appVersionName
            }

            buildFeatures {
                compose = true
            }

            buildTypes {
                release {
                    isMinifyEnabled = false
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro",
                    )
                }
            }
        }
    }
}
