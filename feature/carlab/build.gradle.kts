plugins {
    alias(libs.plugins.cruise.android.library)
    alias(libs.plugins.cruise.compose)
    alias(libs.plugins.cruise.hilt)
}

// Car bring-up tools moved out of Cruise (CHANGE_REQUESTS.md CR-05): steering-wheel mapping, car test, device report,
// key tester. Not part of the music player; kept for the steering-wheel app (CR-10).
android {
    namespace = "com.boulbeba.cruise.feature.carlab"
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.model)
    implementation(projects.core.car)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
}
