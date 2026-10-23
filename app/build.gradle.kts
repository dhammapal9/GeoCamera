import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("androidx.navigation.safeargs.kotlin")
    kotlin("kapt")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}
val mapsApiKey: String = localProps.getProperty("MAPS_API_KEY") ?: ""
val admobAppId: String = localProps.getProperty("ADMOB_APP_ID")
    ?: "ca-app-pub-3940256099942544~3347511713" // AdMob test App ID

android {
    namespace = "com.idp.geostampcamera"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.idp.geostampcamera"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        manifestPlaceholders["ADMOB_APP_ID"] = admobAppId
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            buildConfigField("boolean", "ADS_ENABLED", "true")
            buildConfigField("String", "WEATHER_BASE_URL", "\"https://api.open-meteo.com/\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "ADS_ENABLED", "true")
            buildConfigField("String", "WEATHER_BASE_URL", "\"https://api.open-meteo.com/\"")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources.excludes += setOf(
            "META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
            "META-INF/INDEX.LIST"
        )
        // 16 KB page-size compatibility (Android 15+). AGP 8.5+ aligns .so files inside the APK
        // only when legacy JNI packaging is off.
        jniLibs {
            useLegacyPackaging = false
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.fragment:fragment-ktx:1.8.3")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.gridlayout:gridlayout:1.0.0")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // Lifecycle + ViewModel
    val lifecycle = "2.8.5"
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycle")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycle")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycle")
    implementation("androidx.lifecycle:lifecycle-service:$lifecycle")
    implementation("androidx.lifecycle:lifecycle-process:$lifecycle")

    // Navigation
    val nav = "2.7.7"
    implementation("androidx.navigation:navigation-fragment-ktx:$nav")
    implementation("androidx.navigation:navigation-ui-ktx:$nav")

    // CameraX — 1.4.0+ ships 16 KB page-size-aligned native libraries (NDK r27)
    val camerax = "1.4.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("androidx.camera:camera-video:$camerax")
    implementation("androidx.camera:camera-extensions:$camerax")
    // CameraX overlay effect — lets us burn the GPS stamp into recorded video frames
    // in real-time via an off-screen Canvas, so no FFmpeg post-processing is needed.
    implementation("androidx.camera:camera-effects:$camerax")

    // Guava — CameraX's ProcessCameraProvider.getInstance() returns ListenableFuture.
    // Some Play Services deps bring in a stripped artifact that omits the class.
    implementation("com.google.guava:guava:33.3.1-android")

    // ExifInterface
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Hilt
    val hilt = "2.51.1"
    implementation("com.google.dagger:hilt-android:$hilt")
    kapt("com.google.dagger:hilt-android-compiler:$hilt")
    implementation("androidx.hilt:hilt-navigation-fragment:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    kapt("androidx.hilt:hilt-compiler:1.2.0")

    // Coroutines
    val coroutines = "1.8.1"
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutines")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:$coroutines")

    // Room
    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Location + Maps
    // Play Services — these revisions are built with NDK r27+ so their .so files are
    // 16 KB page-size aligned (required by Google Play for apps targeting Android 15+).
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-maps:19.2.0")
    implementation("com.google.maps.android:android-maps-utils:3.8.2")

    // Google Sign-In (used for Drive sync — requests the drive.file OAuth scope
    // so the app can create and manage only files it uploads itself.)
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    // ML Kit Barcode Scanning
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    // ML Kit's native deps resolve via play-services-mlkit-barcode-scanning transitives.
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")

    // Networking (weather)
    val retrofit = "2.11.0"
    implementation("com.squareup.retrofit2:retrofit:$retrofit")
    implementation("com.squareup.retrofit2:converter-moshi:$retrofit")
    val moshi = "1.15.1"
    implementation("com.squareup.moshi:moshi-kotlin:$moshi")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:$moshi")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Image loading
    implementation("io.coil-kt:coil:2.7.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // AdMob (optional)
    // AdMob 23.6.0 ships 16 KB-aligned .so and is compiled with Kotlin 1.9 metadata.
    // 24.x requires Kotlin 2.x, which this project does not use.
    implementation("com.google.android.gms:play-services-ads:23.6.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutines")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.robolectric:robolectric:4.13")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}

kapt { correctErrorTypes = true }
