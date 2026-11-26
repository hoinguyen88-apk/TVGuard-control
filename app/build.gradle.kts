plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.giadinh.tvguard"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.giadinh.tvguard"
        minSdk = 26
        targetSdk = 33
        versionCode = 2
        versionName = "2.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    sourceSets { getByName("main") { java.srcDir("../shared/src") } }
}
dependencies {
    implementation("com.google.zxing:core:3.5.3") // vẽ mã QR ghép đôi
}
