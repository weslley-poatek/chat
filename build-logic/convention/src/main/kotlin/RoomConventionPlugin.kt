import androidx.room3.gradle.RoomExtension
import extensions.configureRoom
import extensions.configureRoomCompiler
import extensions.configureRoomSchemas
import extensions.configureRoomTests
import extensions.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Convention for a module that owns a Room database: KSP, the Room Gradle plugin and its exported
 * schemas, the runtime and bundled SQLite driver, the Room compiler on every target, and what its
 * `commonTest` needs to open databases on the Android host and the iOS simulator.
 *
 * Apply it after `chat.multiplatform.library`, which owns the Android and iOS targets the compiler is
 * added for. The raw KMP and Android library ids are applied here too, so the extension exists
 * whatever the order — a no-op once the base has run.
 */
class RoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = libs.plugins.kotlin.multiplatform.get().pluginId)
        apply(plugin = libs.plugins.android.multiplatform.library.get().pluginId)
        apply(plugin = libs.plugins.ksp.get().pluginId)
        apply(plugin = libs.plugins.room.get().pluginId)

        extensions.configure<RoomExtension>(::configureRoomSchemas)
        extensions.configure<KotlinMultiplatformExtension>(::configureRoom)
        extensions.configure<KotlinMultiplatformExtension>(::configureRoomCompiler)
        configureRoomTests()
    }
}
