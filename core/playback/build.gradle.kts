plugins {
    alias(libs.plugins.cruise.android.library)
    alias(libs.plugins.cruise.hilt)
}

android {
    namespace = "com.boulbeba.cruise.core.playback"
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.car)
    api(projects.core.data)
    api(projects.core.audio)

    api(libs.media3.common)
    api(libs.media3.session)
    implementation(libs.media3.exoplayer)
    // FLAC / ALAC / WavPack / DSD / WMA … (the owner's unit has no platform FLAC decoder). Pinned with Media3.
    implementation(libs.jellyfin.media3.ffmpeg.decoder)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
