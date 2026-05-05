import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
}

// Cloudinary configuration is read from local.properties (gitignored). Set:
//   cloudinary.cloudName=<your-cloud-name>
//   cloudinary.uploadPreset=<your-unsigned-preset>
// to enable media uploads. Without these the app builds but media sending will fail at runtime.
val cloudinaryProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val cloudinaryCloudName: String = cloudinaryProps.getProperty("cloudinary.cloudName", "")
val cloudinaryUploadPreset: String = cloudinaryProps.getProperty("cloudinary.uploadPreset", "")

// Apply google-services only when google-services.json is present so the project can be cloned
// and built without a Firebase config (the auth/firestore calls will fail at runtime, but the
// app compiles). Replace `app/google-services.json` with the file from your Firebase Console.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "ru.ilyakirollov.messenger"
    compileSdk = 34

    defaultConfig {
        applicationId = "ru.ilyakirollov.messenger"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"$cloudinaryCloudName\"")
        buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET", "\"$cloudinaryUploadPreset\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xjvm-default=all")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    implementation(libs.accompanist.permissions)

    implementation(libs.webrtc)

    implementation(libs.okhttp)
}

kapt {
    correctErrorTypes = true
}
