import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "br.com.weslleycampos.chat.buildlogic"

/**
 * Configure the build-logic plugins to target JDK 21.
 * This matches the JDK used to build the project, and is not related to what is running on device.
 */
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

/**
 * The generated `LibrariesForLibs` accessors, so convention plugins can use typed `libs.*` from
 * gradle/libs.versions.toml — Gradle does not hand them to plugin classes on its own.
 * See https://github.com/gradle/gradle/issues/15383.
 *
 * The class is looked up by name on purpose: Gradle generates it for *this* script's classpath, and
 * naming `libs` here is what the IDE fails to resolve.
 */
val enableTypeAccessors: ConfigurableFileCollection = files(
    Class.forName("org.gradle.accessors.dm.LibrariesForLibs").protectionDomain.codeSource.location
)

/**
 * The Gradle plugins the convention plugins apply by id. `compileOnly`, so they are visible while
 * the scripts compile but are *not* republished on this project's runtime classpath. The consuming
 * build gets them from the root `plugins { ... apply false }` block, which resolves each one exactly
 * once for the whole build — one AGP, one KGP, one classloader.
 */
dependencies {
    compileOnly(enableTypeAccessors)
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
    compileOnly(libs.detekt.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
}

/**
 * Convention plugins are classes, so each id has to be registered against its implementation — the
 * filename means nothing here. Ids come from the catalog so that this registration and the
 * `alias(libs.plugins.chat.*)` calls in the modules cannot drift apart.
 */
gradlePlugin {
    plugins {
        register("android-application") {
            id = libs.plugins.chat.android.application.get().pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("compose-library") {
            id = libs.plugins.chat.compose.library.get().pluginId
            implementationClass = "ComposeLibraryConventionPlugin"
        }
        register("detekt") {
            id = libs.plugins.chat.detekt.get().pluginId
            implementationClass = "DetektConventionPlugin"
        }
        register("koin") {
            id = libs.plugins.chat.koin.get().pluginId
            implementationClass = "KoinConventionPlugin"
        }
        register("multiplatform-library") {
            id = libs.plugins.chat.multiplatform.library.get().pluginId
            implementationClass = "MultiplatformLibraryConventionPlugin"
        }
    }
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}
