plugins {
    alias(libs.plugins.cruise.android.library)
    alias(libs.plugins.cruise.compose)
    alias(libs.plugins.cruise.hilt)
}

android {
    namespace = "com.boulbeba.cruise.feature.voice"
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.playback)
    implementation(projects.core.data)
    implementation(projects.core.ui)
    implementation(projects.core.car)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    // Offline speech recognition (Kaldi). JNA is pulled in as an AAR so its native libraries are packaged.
    implementation(libs.vosk.android) { exclude(group = "net.java.dev.jna") }
    implementation(libs.jna) { artifact { type = "aar" } }

    testImplementation(libs.junit)
}
