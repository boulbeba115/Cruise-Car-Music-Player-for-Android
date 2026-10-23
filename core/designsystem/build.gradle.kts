plugins {
    alias(libs.plugins.cruise.android.library)
    alias(libs.plugins.cruise.compose)
}

android {
    namespace = "com.boulbeba.cruise.core.designsystem"
}

dependencies {
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
}
