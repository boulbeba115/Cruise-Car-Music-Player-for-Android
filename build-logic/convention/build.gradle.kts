plugins {
    `kotlin-dsl`
}

group = "com.boulbeba.cruise.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "cruise.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "cruise.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("compose") {
            id = "cruise.compose"
            implementationClass = "ComposeConventionPlugin"
        }
        register("hilt") {
            id = "cruise.hilt"
            implementationClass = "HiltConventionPlugin"
        }
    }
}
