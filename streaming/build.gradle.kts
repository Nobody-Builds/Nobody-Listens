// Streaming clients shared by the phone app and the watch app: YouTube Music (innertube API, parsers,
// stream clients, format ranking) and JioSaavn (web API, parsers, stream lookup), plus the streaming
// interfaces both implement. No Android UI or app storage here.
plugins {
    alias(libs.plugins.android.library)
    kotlin("plugin.serialization")
}

android {
    namespace = "com.nobodybuilds.listens.streaming"
    compileSdk = 37

    defaultConfig {
        minSdk = 26 // the lower of the app (33) and watch (26) minimums
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
