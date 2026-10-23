plugins {
    alias(libs.plugins.cruise.android.library)
    alias(libs.plugins.cruise.hilt)
}

android {
    namespace = "com.boulbeba.cruise.core.car"
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
