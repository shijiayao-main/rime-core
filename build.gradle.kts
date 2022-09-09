plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jiaoay.rime_core"
    compileSdk = libs.versions.compileSdkVersion.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdkVersion.get().toInt()
        targetSdk = libs.versions.targetSdkVersion.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                abiFilters.addAll(arrayOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    externalNativeBuild {
        cmake {
            version = "3.10.2"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    if (file("prebuilt").exists()) {
        sourceSets.getByName("main").jniLibs.srcDirs("prebuilt")
    } else {
        externalNativeBuild.cmake.path = file("src/main/cpp/CMakeLists.txt")
    }

}

dependencies {
    implementation(
        group = libs.androidxCore.get().module.group,
        name = libs.androidxCore.get().module.name,
        version = libs.versions.androidxCoreVersion.get()
    )
    implementation(
        group = libs.androidxAppcompat.get().module.group,
        name = libs.androidxAppcompat.get().module.name,
        version = libs.versions.androidxAppcompatVersion.get()
    )
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
}