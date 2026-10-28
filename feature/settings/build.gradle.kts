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
            // AccountRepository and ProviderKeyRepository, with `:data:auth`'s AuthUser, SignInMethod and
            // AuthException through its `api`.
            implementation(projects.data.settings)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            // `SettingsEntryProviderTest` saves `SettingsEntry` through the back stack's JSON.
            implementation(project.dependencies.platform(libs.kotlinx.serialization.bom))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
