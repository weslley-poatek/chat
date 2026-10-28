plugins {
    alias(libs.plugins.chat.multiplatform.library)
    alias(libs.plugins.chat.compose.library)
    alias(libs.plugins.chat.koin)
    alias(libs.plugins.chat.detekt)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `api`: Navigator, EntryProvider and rememberNavigator expose NavKey, NavBackStack and
            // EntryProviderScope to every module that reads them.
            api(libs.navigation3.ui)
        }
        commonTest.dependencies {
            // `VoiceEntryTest` saves `VoiceEntry` through the back stack's JSON.
            implementation(project.dependencies.platform(libs.kotlinx.serialization.bom))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
