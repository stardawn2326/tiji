plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.tiji.mistakes"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tiji.mistakes"
        minSdk = 26
        targetSdk = 34
        versionCode = 114
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    // Release acceptance must opt into fail-closed signing. Ordinary local debug
    // builds keep the existing debug-keystore fallback for API30 test execution.
    val requireReleaseSigning = providers.gradleProperty("TIJI_REQUIRE_RELEASE_SIGNING").orNull?.toBoolean() == true ||
        System.getenv("TIJI_REQUIRE_RELEASE_SIGNING")?.toBoolean() == true
    val signingStoreFileValue = providers.gradleProperty("TIJI_SIGNING_STORE_FILE").orNull
        ?: System.getenv("TIJI_SIGNING_STORE_FILE")
    val signingStorePasswordValue = providers.gradleProperty("TIJI_SIGNING_STORE_PASSWORD").orNull
        ?: System.getenv("TIJI_SIGNING_STORE_PASSWORD")
    val signingKeyAliasValue = providers.gradleProperty("TIJI_SIGNING_KEY_ALIAS").orNull
        ?: System.getenv("TIJI_SIGNING_KEY_ALIAS")
    val signingKeyPasswordValue = providers.gradleProperty("TIJI_SIGNING_KEY_PASSWORD").orNull
        ?: System.getenv("TIJI_SIGNING_KEY_PASSWORD")
    val missingSigningProperties = listOf(
        "TIJI_SIGNING_STORE_FILE" to signingStoreFileValue,
        "TIJI_SIGNING_STORE_PASSWORD" to signingStorePasswordValue,
        "TIJI_SIGNING_KEY_ALIAS" to signingKeyAliasValue,
        "TIJI_SIGNING_KEY_PASSWORD" to signingKeyPasswordValue
    ).filter { it.second.isNullOrBlank() }.map { it.first }
    if (requireReleaseSigning && missingSigningProperties.isNotEmpty()) {
        throw GradleException(
            "TIJI_REQUIRE_RELEASE_SIGNING=true requires: ${missingSigningProperties.joinToString(", ")}"
        )
    }

    signingConfigs {
        create("stableRelease") {
            // v1.0.0 was signed with this certificate. Keeping it is mandatory for
            // Android to accept an in-place update without deleting private data.
            val configuredStore = signingStoreFileValue
                ?: "${System.getProperty("user.home")}/.android/debug.keystore"
            storeFile = file(configuredStore)
            storePassword = signingStorePasswordValue ?: "android"
            keyAlias = signingKeyAliasValue ?: "androiddebugkey"
            keyPassword = signingKeyPasswordValue ?: "android"
        }
    }

    buildTypes {
        debug {
            // Instrumented tests must be able to update the installed release build
            // without clearing its private database.
            signingConfig = signingConfigs.getByName("stableRelease")
        }
        release {
            // The accepted v1 release is optimized with R8 and resource shrinking.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("stableRelease")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // OpenCV and its C++ runtime are downloaded with the OCR package and
        // loaded from app-private storage. Keeping them out of the APK brings
        // the release back to the pre-OCR size without losing offline OCR.
        jniLibs.excludes += "**/libopencv_java4.so"
        jniLibs.excludes += "**/libc++_shared.so"
    }
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
    buildFeatures { compose = true; buildConfig = true }
}

// Keep generated APK names traceable to the app version.
android.applicationVariants.all {
    outputs.all {
        val variantName = name
        (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
            "tiji-v${versionName}-${variantName}.apk"
    }
}

// Keep every versioned release APK in a persistent archive directory.
val archiveReleaseApk = tasks.register<org.gradle.api.tasks.Copy>("archiveReleaseApk") {
    from(layout.buildDirectory.dir("outputs/apk/release"))
    into(rootProject.layout.projectDirectory.dir("outputs/apk/releases"))
    include("tiji-v*-release.apk")
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(archiveReleaseApk)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.exifinterface)
    // Only the small Java APIs are compiled into the app. Native OCR runtimes and
    // all model data are downloaded together into app-private storage on demand.
    implementation(project(":paddleocr"))

    implementation(platform("androidx.compose:compose-bom:${libs.versions.composeBom.get()}"))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(platform("androidx.compose:compose-bom:${libs.versions.composeBom.get()}"))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.expandProjection", "true")
}
