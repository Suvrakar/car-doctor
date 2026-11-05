plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services) apply false
}

// Firebase (Remote Config templates, Crashlytics/Analytics later) is optional:
// drop your google-services.json into app/ and the plugin is applied automatically.
val enableNano = providers.gradleProperty("enableNano").isPresent

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.garidoctor.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.garidoctor.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // Salt for offline promo codes. Override with -PgariProSalt=... for release builds.
        buildConfigField("String", "PRO_SALT", "\"${providers.gradleProperty("gariProSalt").getOrElse("garidoctor-dev-salt")}\"")
        // AdMob: Google's public TEST ids by default. Pass real ids for release:
        //   -PadmobAppId=ca-app-pub-xxx~yyy -PadmobRewardedId=ca-app-pub-xxx/zzz -PadmobBannerId=ca-app-pub-xxx/www
        manifestPlaceholders["admobAppId"] = providers.gradleProperty("admobAppId").getOrElse("ca-app-pub-3940256099942544~3347511713")
        buildConfigField("String", "ADMOB_REWARDED", "\"${providers.gradleProperty("admobRewardedId").getOrElse("ca-app-pub-3940256099942544/5224354917")}\"")
        buildConfigField("String", "ADMOB_BANNER", "\"${providers.gradleProperty("admobBannerId").getOrElse("ca-app-pub-3940256099942544/6300978111")}\"")
        // Play Console in-app product id for the one-time Pro unlock.
        buildConfigField("String", "PRO_PRODUCT_ID", "\"garidoctor_pro\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources { noCompress += "tflite" }

    // Templates, symptom trees, fault codes and translations ship as Android assets: opening an
    // asset is far faster at startup than reading Java resources out of the APK.
    sourceSets.getByName("main").assets.srcDir("../core/src/main/resources")

    // On-device Gemini Nano (ML Kit GenAI Prompt API, beta) is opt-in until verified on a real
    // device: build with -PenableNano to compile the Nano implementation instead of the stub.
    sourceSets.getByName("main").java.srcDir(if (enableNano) "src/nano/kotlin" else "src/nanoOff/kotlin")
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Already in assets (see above); don't ship them twice as Java resources.
            excludes += listOf("templates/**", "symptoms/**", "obd/**", "i18n/**")
        }
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime)
    // Play-services OCR: the model comes from Google Play services instead of being bundled,
    // which keeps the APK several MB smaller on low-storage phones.
    implementation(libs.mlkit.text.play)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.exifinterface)
    implementation(libs.zxing.core)
    implementation(libs.tflite)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.config)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.ai)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    if (enableNano) implementation(libs.mlkit.genai.prompt)
    implementation(libs.kotlinx.coroutines.play)
    implementation(libs.billing)
    implementation(libs.usb.serial) // USB ELM327 cables (FTDI, CH340, CP210x, PL2303, CDC-ACM)
    implementation(libs.play.ads)
    testImplementation(libs.junit)
}
