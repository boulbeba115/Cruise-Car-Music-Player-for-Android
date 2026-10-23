plugins {
    alias(libs.plugins.cruise.android.library)
}

android {
    namespace = "com.boulbeba.cruise.core.model"
}

dependencies {
    testImplementation(libs.junit)
}
