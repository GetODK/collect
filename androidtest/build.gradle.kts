plugins {
    id("com.android.library")
    id("kotlin-android")
}

apply(from = "../config/quality.gradle")

android {
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    namespace = "org.odk.collect.androidtest"
}

dependencies {
    coreLibraryDesugaring(libs.desugar)

    implementation(libs.junit)
    implementation(libs.androidxTestCoreKtx)
    implementation(libs.androidxLifecycleLivedataKtx)
    implementation(libs.androidxLifecycleRuntimeKtx)
    implementation(libs.androidxTestEspressoCore)
    implementation(libs.androidxAppcompat)
    implementation(libs.androidxTestEspressoIntents)
    implementation(libs.timber)
}
