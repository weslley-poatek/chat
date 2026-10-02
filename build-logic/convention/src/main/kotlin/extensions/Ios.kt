package extensions

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * The name Xcode links against — the Swift `import App`, and the product that
 * `embedAndSignAppleFrameworkForXcode` copies into the app bundle. It is fixed by the Xcode project,
 * not derived from the Gradle path, so changing it here means changing the Swift import too.
 */
private const val IOS_FRAMEWORK_NAME = "App"

internal fun Project.configureIosFramework(extension: KotlinMultiplatformExtension) {
    extension.apply {
        listOf(
            iosArm64(),
            iosSimulatorArm64(),
        ).forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = IOS_FRAMEWORK_NAME
                isStatic = true
            }
        }
    }
}
