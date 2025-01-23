plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinKapt)
}

apply(from = "../config/quality.gradle")

android {
    namespace = "org.odk.collect.draw"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar)

    implementation(project(":shared"))
    implementation(project(":androidshared"))
    implementation(project(":strings"))
    implementation(project(":async"))
    implementation(project(":settings"))
    implementation(project(":icons"))

    implementation(libs.rarepebbleColorpicker)
    implementation(libs.androidxLifecycleLivedataKtx)
    implementation(libs.androidxLifecycleViewmodelKtx)
    implementation(libs.androidxFragmentKtx)
    implementation(libs.androidMaterial)
    implementation(libs.timber)

    implementation(libs.dagger)
    kapt(libs.daggerCompiler)

    debugImplementation(project(":fragments-test"))

    testImplementation(libs.androidxTestExtJunit)
    testImplementation(libs.mockitoKotlin)
    testImplementation(libs.androidxTestEspressoCore)
    testImplementation(libs.robolectric)
}
