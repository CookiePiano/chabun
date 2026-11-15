import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

// Release signing is read from keystore.properties in the project root, which is not committed.
// Without that file the release build is left unsigned and every other task works as usual.
val keystoreProperties = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("keystore.properties"))
        .asText.orNull
        ?.let { load(it.reader()) }
}

android {
    namespace = "com.syncdesk.app"
    // Compose 1.12 and Lifecycle 2.11 need to compile against API 37. This only widens the APIs
    // the compiler sees; runtime behaviour still follows targetSdk below.
    compileSdk {
        version = release(37) { minorApiLevel = 0 }
    }

    // AAOS car API (CarUxRestrictionsManager), provided by the vehicle at run time.
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.syncdesk.app"
        minSdk = 28      // Pleos Connect requires API 28 or later
        targetSdk = 34   // Pleos Connect Emulator system image is API 34
        versionCode = 1
        versionName = "0.1.0"

        // The phone call and the driver's speech are still simulated, so the in-app demo controls
        // are the only way to start them. Vehicle signals come from the Pleos Vehicle SDK.
        buildConfigField("boolean", "DEMO_CONTROLS", "true")
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // R8 removes unused code, including the Pleos SDKs kept for later, and resource
            // shrinking drops the resources only they used. Rules are in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            // JNA (from the Pleos speech SDKs) ships libraries for ABIs that Android 9 (minSdk 28)
            // and later never load.
            excludes += setOf("lib/mips/**", "lib/mips64/**", "lib/armeabi/**")
        }
    }

    testOptions {
        unitTests {
            // Robolectric renders the real resources (fonts, strings, drawables) for screenshot tests.
            isIncludeAndroidResources = true
            all { test ->
                test.maxHeapSize = "3g"
                // Robolectric tests are independent; spread them over the build machine's cores.
                test.maxParallelForks = (Runtime.getRuntime().availableProcessors() / 4).coerceAtLeast(1)
            }
        }
    }
}

composeCompiler {
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose-stability.conf"))
}

dependencies {
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.speechtotext)
    implementation(libs.pleos.texttospeech)

    // Pleos SDKs retained for future app development.
    implementation(libs.pleos.navihelper)
    implementation(libs.pleos.fusedlocation)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Versioned by the Compose BOM declared above; test configurations inherit it.
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
}
