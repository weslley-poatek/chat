plugins {
    alias(libs.plugins.chat.multiplatform.library)
    alias(libs.plugins.chat.koin)
    alias(libs.plugins.chat.detekt)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `api`: a reply streams as a Flow.
            api(libs.kotlinx.coroutines.core)
            // Koog's OpenAI client speaks to Fuelix's OpenAI-compatible API, over Ktor.
            implementation(libs.koog.prompt.executor.openai.client)
            implementation(libs.koog.http.client.ktor)
            implementation(libs.ktor.client.core)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
