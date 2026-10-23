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
            // Local days for the groups, and the rows' clock times and dates.
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            // `HistoryEntryProviderTest` saves `HistoryEntry` through the back stack's JSON.
            implementation(project.dependencies.platform(libs.kotlinx.serialization.bom))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
