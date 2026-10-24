plugins {
    alias(libs.plugins.cruise.android.application)
    alias(libs.plugins.cruise.compose)
    alias(libs.plugins.cruise.hilt)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.boulbeba.cruise"

    defaultConfig {
        applicationId = "com.boulbeba.cruise"
        versionCode = 9
        versionName = "1.2.2"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key until a release key exists (Phase 7).
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // Screenshot tests render the real UI on the JVM (Robolectric) across the screen matrix of SPEC §5.1.
    testOptions.unitTests.isIncludeAndroidResources = true

    // One APK per ABI keeps each install small; head units may run a 32-bit userspace (SPEC §2).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.feature.settings)
    implementation(projects.feature.nowplaying)
    implementation(projects.feature.library)
    implementation(projects.feature.sound)
    implementation(projects.core.data)
    implementation(projects.core.ui)
    implementation(projects.core.playback)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
