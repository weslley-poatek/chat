plugins {
    alias(libs.plugins.chat.android.application)
    alias(libs.plugins.chat.koin)
}

dependencies {
    implementation(projects.app)
    implementation(libs.activity.compose)

    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
