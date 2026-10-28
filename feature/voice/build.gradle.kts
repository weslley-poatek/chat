plugins {
    alias(libs.plugins.chat.multiplatform.library)
    alias(libs.plugins.chat.compose.library)
    alias(libs.plugins.chat.koin)
    alias(libs.plugins.chat.detekt)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.navigation)
            implementation(projects.core.ui)
            implementation(projects.data.chat)
            // The model chip's sheet, `ModelPickerSheet` and its rows, and `ChatModel.palette`: spec 04
            // keeps the picker in `:feature:chat` for Voice and Settings to share.
            implementation(projects.feature.chat)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            // `VoiceEntryProviderTest` saves `VoiceEntry` through the back stack's JSON.
            implementation(project.dependencies.platform(libs.kotlinx.serialization.bom))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
