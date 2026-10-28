plugins {
    alias(libs.plugins.chat.multiplatform.library)
    alias(libs.plugins.chat.compose.library)
    alias(libs.plugins.chat.koin)
    alias(libs.plugins.chat.detekt)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.database)
            implementation(projects.core.navigation)
            implementation(projects.core.preferences)
            implementation(projects.core.ui)
            implementation(projects.data.auth)
            implementation(projects.data.chat)
            implementation(projects.feature.auth)
            implementation(projects.feature.chat)
            implementation(projects.feature.history)
            implementation(projects.feature.voice)
        }
        commonTest.dependencies {
            // `AppViewModelTest` runs the root accent on virtual time.
            implementation(libs.kotlinx.coroutines.test)
            // `AppEntriesTest` saves every key the app navigates to through the back stack's JSON.
            implementation(project.dependencies.platform(libs.kotlinx.serialization.bom))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
