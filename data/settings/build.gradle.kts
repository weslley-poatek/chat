plugins {
    alias(libs.plugins.chat.multiplatform.library)
    alias(libs.plugins.chat.koin)
    alias(libs.plugins.chat.detekt)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `api`: the repositories expose Flow to the Settings screen.
            api(libs.kotlinx.coroutines.core)
            // `api`: an Account carries AuthUser and SignInMethod, and deleteAccount throws AuthException.
            api(projects.data.auth)
            // Deleting the account wipes the chat's conversations and choices through its ChatDataEraser.
            implementation(projects.data.chat)
            // Fuelix's requests read the key saved here through its FuelixKeySource.
            implementation(projects.data.fuelix)
            // Provider keys are kept in its encrypted DataStore.
            implementation(projects.core.preferences)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
