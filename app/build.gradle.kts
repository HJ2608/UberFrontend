import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val localProps = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProps.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.example.uberfrontend"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.uberfrontend"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val mapsKey = localProps.getProperty("MAPS_API_KEY") ?: ""
        manifestPlaceholders["MAPS_API_KEY"] = mapsKey
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
    buildFeatures {
        viewBinding = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // Use version catalog for these core libraries to avoid duplicates/conflicts
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    
    // Removed duplicate manual implementations of core-ktx, appcompat, material, fragment-ktx
    // to prevent dependency conflicts (versions 1.13.1, 1.7.0, 1.12.0 etc. were clashing with newer libs)

    // Navigation
    val navVersion = "2.8.3"
    implementation("androidx.navigation:navigation-fragment-ktx:$navVersion")
    implementation("androidx.navigation:navigation-ui-ktx:$navVersion")
    
    // Maps & Location
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.libraries.places:places:3.4.0")

    // Network - Retrofit & OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0") 
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // JSON - Moshi
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("com.squareup.moshi:moshi:1.15.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("com.github.NaikSoftware:StompProtocolAndroid:1.6.6")

    implementation("io.reactivex.rxjava2:rxjava:2.2.21")
    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")
    implementation("com.google.maps.android:android-maps-utils:2.4.0")
    implementation("org.java-websocket:Java-WebSocket:1.5.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")

}
