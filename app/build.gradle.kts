plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "com.myapp.browser"
    //noinspection GradleDependency
    compileSdk = 33
    defaultConfig {
        applicationId = "com.myapp.browser"
        minSdk = 21
        //noinspection OldTargetApi
        targetSdk = 33
        versionCode = 2
        versionName = "2.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}