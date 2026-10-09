import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.jagaldol.dailytarot"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jagaldol.dailytarot"
        minSdk = 24
        targetSdk = 37
        versionCode = 4
        versionName = "2.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing comes from a properties file kept outside the repository
    // (DAILYTAROT_KEYSTORE_PROPERTIES, else ~/.android/dailytarot/keystore.properties).
    // Without it the release APK is built unsigned, as on CI.
    val keystoreProperties = (System.getenv("DAILYTAROT_KEYSTORE_PROPERTIES")
        ?: "${System.getProperty("user.home")}/.android/dailytarot/keystore.properties")
        .let(::File)
        .takeIf { it.isFile }
        ?.let { file -> Properties().apply { file.inputStream().use(::load) } }
    if (keystoreProperties != null) {
        signingConfigs {
            create("release") {
                storeFile = File(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
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
        compose = true
    }
    // English is the default; Korean lives in values-ko. Android 13+ lists both in the per-app language setting.
    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf("en", "ko")
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
