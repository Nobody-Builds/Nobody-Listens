// YouTube Music client shared by the phone app and the watch app: the innertube API,
// response parsers, stream clients and format ranking. No Android UI or app storage here.
plugins {
    alias(libs.plugins.android.library)
    kotlin("plugin.serialization")
}

android {
    namespace = "com.nobodybuilds.listens.ytmusic"
    compileSdk = 37

    defaultConfig {
        minSdk = 30 // the lower of the app (33) and watch (30) minimums
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    api(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
}
