plugins {
    alias(libs.plugins.cruise.android.library)
    alias(libs.plugins.cruise.compose)
}

android {
    namespace = "com.boulbeba.cruise.core.ui"
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.data)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
