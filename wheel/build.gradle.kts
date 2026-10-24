plugins {
    alias(libs.plugins.cruise.android.application)
    alias(libs.plugins.cruise.compose)
    alias(libs.plugins.cruise.hilt)
}

// Cruise Wheel (CHANGE_REQUESTS.md CR-10): steering-wheel control and car-integration probe, a separate app from the
// music player. Shares the design system, the TW head-unit bridge and the car-test tools with Cruise.
android {
    namespace = "com.boulbeba.cruise.wheel"

    defaultConfig {
        applicationId = "com.boulbeba.cruise.wheel"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key, like Cruise, until a release key exists.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.model)
    implementation(projects.core.car)
    implementation(projects.feature.carlab)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
