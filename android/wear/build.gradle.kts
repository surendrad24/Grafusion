plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fusionlancers.grafusion.wear"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fusionlancers.grafusion.wear"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Minimum Wear runtime.
    implementation("androidx.core:core-ktx:1.13.1")

    // Tiles + ProtoLayout for the on-watch alert count tile.
    implementation("androidx.wear.tiles:tiles:1.4.0")
    implementation("androidx.wear.tiles:tiles-material:1.4.0")
    implementation("androidx.wear.protolayout:protolayout:1.2.0")
    implementation("androidx.wear.protolayout:protolayout-material:1.2.0")
    implementation("androidx.wear.protolayout:protolayout-expression:1.2.0")

    // Data Layer for phone -> watch sync (alert count + last-updated).
    implementation("com.google.android.gms:play-services-wearable:18.2.0")

    // Coroutines for the tile future adapter.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.guava:guava:33.3.0-android")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0")
}
