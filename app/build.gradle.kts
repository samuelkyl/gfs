import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

fun computeSemVerCode(semVer: String): Int {
    val core = semVer.trimStart('v', 'V').substringBefore('-').substringBefore('+')
    val parts = core.split('.')
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    return (major * 1_000_000 + minor * 1_000 + patch).coerceAtLeast(1)
}

val appVersionName: String = providers.gradleProperty("versionName")
    .orElse("0.1.0")
    .get()
    .trimStart('v', 'V')

val appVersionCode: Int = providers.gradleProperty("versionCode")
    .map { it.toInt() }
    .orElse(computeSemVerCode(appVersionName))
    .get()

val localSigningProps = Properties().apply {
    providers.fileContents(
        rootProject.layout.projectDirectory.file("keystore/signing.properties")
    ).asText.orNull?.byteInputStream()?.use { load(it) }
}

val releaseStoreFile: File? = providers.environmentVariable("ANDROID_KEYSTORE_PATH")
    .orNull
    ?.takeIf { it.isNotBlank() }
    ?.let(::File)
    ?: localSigningProps.getProperty("storeFile")
        ?.takeIf { it.isNotBlank() }
        ?.let { rootProject.file(it) }

val releaseStorePassword: String? = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD")
    .orNull
    ?.takeIf { it.isNotBlank() }
    ?: localSigningProps.getProperty("storePassword")?.takeIf { it.isNotBlank() }

val releaseKeyAlias: String? = providers.environmentVariable("ANDROID_KEY_ALIAS")
    .orNull
    ?.takeIf { it.isNotBlank() }
    ?: localSigningProps.getProperty("keyAlias")?.takeIf { it.isNotBlank() }

val releaseKeyPassword: String? = providers.environmentVariable("ANDROID_KEY_PASSWORD")
    .orNull
    ?.takeIf { it.isNotBlank() }
    ?: localSigningProps.getProperty("keyPassword")?.takeIf { it.isNotBlank() }

val hasReleaseSigning: Boolean = releaseStoreFile?.isFile == true &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "ca.sleung.gfs"
    compileSdk = 37

    defaultConfig {
        applicationId = "ca.sleung.gfs"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

dependencies {
    // Core & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose & Material 3
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Navigation & Serialization
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    // Dependency Injection (Hilt)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Persistence (Room & DataStore)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
