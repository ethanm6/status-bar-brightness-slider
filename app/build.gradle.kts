plugins {
    alias(libs.plugins.android.application)
}
android {
    namespace = "dev.module.statusbarbrightnessgesture"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.module.statusbarbrightnessgesture"
        minSdk = 33
        targetSdk = 35
        versionCode = 8
        versionName = "1.7.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    compileOnly(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("com.google.android.material:material:1.12.0")
}