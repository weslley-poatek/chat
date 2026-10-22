plugins {
    alias(libs.plugins.chat.multiplatform.library)
    alias(libs.plugins.chat.room)
    alias(libs.plugins.chat.koin)
    alias(libs.plugins.chat.detekt)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `api`: DAOs expose `Flow` to every module that observes a table.
            api(libs.kotlinx.coroutines.core)
            // The `messages.blocks` column is a JSON array. The BOM also lifts the 1.8.1
            // `kotlinx-serialization-json-okio` that Room's migration code reads schemas with,
            // which fails to link against a newer `kotlinx-serialization-json`.
            implementation(project.dependencies.platform(libs.kotlinx.serialization.bom))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
