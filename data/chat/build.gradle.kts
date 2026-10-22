plugins {
    alias(libs.plugins.chat.multiplatform.library)
    alias(libs.plugins.chat.koin)
    alias(libs.plugins.chat.detekt)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `api`: ChatRepository exposes Flow to every module that observes a conversation.
            api(libs.kotlinx.coroutines.core)
            // `api`: `groupHistory` takes the time zone History's days are read in.
            api(libs.kotlinx.datetime)
            implementation(projects.core.database)
            // Favorites and the default model are plain preferences in its unencrypted DataStore.
            implementation(projects.core.preferences)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
