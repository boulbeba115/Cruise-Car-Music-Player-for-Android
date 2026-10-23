plugins {
    alias(libs.plugins.cruise.android.library)
    alias(libs.plugins.cruise.compose)
    alias(libs.plugins.cruise.hilt)
}

android {
    namespace = "com.boulbeba.cruise.feature.sound"
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.audio)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
}
