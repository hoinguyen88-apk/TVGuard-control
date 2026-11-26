plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.giadinh.tvguard.phone"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.giadinh.tvguard.phone"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    sourceSets { getByName("main") { java.srcDir("../shared/src") } }
}
dependencies {
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0") // quét QR
}
