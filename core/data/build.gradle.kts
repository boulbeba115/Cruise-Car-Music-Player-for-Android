plugins {
    alias(libs.plugins.cruise.android.library)
    alias(libs.plugins.cruise.hilt)
}

android {
    namespace = "com.boulbeba.cruise.core.data"
    testOptions.unitTests.isIncludeAndroidResources = true
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
