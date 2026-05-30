import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

val releaseSigningPropertiesFile = rootProject.file("release-signing.properties")
val secretsPropertiesFile = rootProject.file("secrets.properties")

fun loadSecrets(): Properties {
    val props = Properties()
    if (secretsPropertiesFile.exists()) {
        secretsPropertiesFile.inputStream().use { props.load(it) }
    }
    return props
}

fun secret(props: Properties, key: String, default: String): String =
    props.getProperty(key)?.trim()?.replace("\"", "\\\"")?.takeIf { it.isNotEmpty() } ?: default

android {
    namespace = "org.assistix.proto.nativeapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.assistix.proto"
        minSdk = 26
        targetSdk = 35
        versionCode = 110
        versionName = "1.1.6"

        val secrets = loadSecrets()
        buildConfigField("String", "API_ORIGIN", "\"${secret(secrets, "API_ORIGIN", "https://example.com")}\"")
        buildConfigField("String", "WS_ORIGIN", "\"${secret(secrets, "WS_ORIGIN", "wss://example.com/ws")}\"")
        buildConfigField("String", "TURN_HOST", "\"${secret(secrets, "TURN_HOST", "")}\"")
        buildConfigField("String", "TURN_USER", "\"${secret(secrets, "TURN_USER", "")}\"")
        buildConfigField("String", "TURN_CRED", "\"${secret(secrets, "TURN_CRED", "")}\"")
        buildConfigField("boolean", "ENABLE_WHISPER_NATIVE", "true")
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        ndkVersion = "27.0.12077973"
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments +=
                    listOf(
                        "-DANDROID_STL=c++_shared",
                        "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                    )
            }
        }
    }

    signingConfigs {
        if (releaseSigningPropertiesFile.exists()) {
            create("release") {
                val props = Properties()
                releaseSigningPropertiesFile.inputStream().use { props.load(it) }
                val storePath =
                    props.getProperty("storeFile")?.trim()
                        ?: error("storeFile missing in ${releaseSigningPropertiesFile.path}")
                storeFile = rootProject.file(storePath)
                storePassword = props.getProperty("storePassword")?.trim()
                keyAlias = props.getProperty("keyAlias")?.trim()
                keyPassword = props.getProperty("keyPassword")?.trim()
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig =
                if (releaseSigningPropertiesFile.exists()) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
        }
    }
    packaging {
        jniLibs {
            pickFirsts += setOf("**/libc++_shared.so")
            useLegacyPackaging = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("com.google.accompanist:accompanist-swiperefresh:0.36.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("io.getstream:stream-webrtc-android:1.1.3")
    val camerax = "1.4.0"
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
