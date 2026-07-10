plugins {
    alias(libs.plugins.android.application)
}
base {
    archivesName.set("brightness-slider")
}
android {
    namespace = "dev.module.statusbarbrightnessgesture"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.module.statusbarbrightnessgesture"
        minSdk = 33
        targetSdk = 35
        versionCode = 24
        versionName = "1.0.14"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    lint {
        checkReleaseBuilds = false
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