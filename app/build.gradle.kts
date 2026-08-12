plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.smartambulancesystem"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.smartambulancesystem"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["GOOGLE_MAPS_API_KEY"] =
            project.findProperty("GOOGLE_MAPS_API_KEY") ?: ""

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "ORS_API_KEY",
            "\"${project.findProperty("ORS_API_KEY") ?: ""}\""
        )
        resValue(
            "string",
            "google_maps_key",
            project.findProperty("GOOGLE_MAPS_API_KEY")?.toString() ?: ""
        )
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        resValues = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    implementation("com.google.firebase:firebase-auth:23.2.0")
    implementation("com.google.firebase:firebase-database:20.3.0")


    implementation("com.google.android.gms:play-services-location:21.3.0")



    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // OpenRouteService
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("com.google.android.gms:play-services-maps:19.2.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")


    implementation("com.android.volley:volley:1.2.1")

    implementation("androidx.cardview:cardview:1.0.0")


    implementation("com.google.android.material:material:1.12.0")


    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}