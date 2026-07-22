import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jp.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jp.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 54
        versionName = "beta0.7.10"
    }

    signingConfigs {
        create("release") {
            storeFile = System.getenv("ANDROID_RELEASE_STORE_FILE")?.let { file(it) }
            storePassword = System.getenv("ANDROID_RELEASE_STORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("ANDROID_RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    applicationVariants.all {
        outputs.all {
            val output = this as BaseVariantOutputImpl
            output.outputFileName = "jp-media-viewer-${versionName}-${buildType.name}.apk"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.exifinterface:exifinterface:1.3.7")

    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-video:2.5.0")

    implementation("androidx.media3:media3-exoplayer:1.2.0")
    implementation("androidx.media3:media3-ui:1.2.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.11.1")
}
