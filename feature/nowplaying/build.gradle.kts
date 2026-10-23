plugins {
    alias(libs.plugins.cruise.android.library)
    alias(libs.plugins.cruise.compose)
    alias(libs.plugins.cruise.hilt)
}

android {
    namespace = "com.boulbeba.cruise.feature.nowplaying"
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.playback)
    implementation(projects.core.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.palette)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
}
