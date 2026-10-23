plugins {
    alias(libs.plugins.cruise.android.library)
    alias(libs.plugins.cruise.compose)
    alias(libs.plugins.cruise.hilt)
}

android {
    namespace = "com.boulbeba.cruise.feature.settings"
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.model)
    implementation(projects.core.car)
    implementation(projects.core.data)
    implementation(projects.core.playback)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
}
