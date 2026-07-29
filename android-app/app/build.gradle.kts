import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val privateApiProperties = Properties().apply {
    val file = rootProject.file("private-api.properties")
    if (file.isFile) file.inputStream().use(::load)
}

val oauthClientId = privateApiProperties.getProperty("oauthClientId", "")
val oauthClientSecret = privateApiProperties.getProperty("oauthClientSecret", "")
val releaseStoreFile = System.getenv("QUOTACHECK_STORE_FILE").orEmpty()
val releaseStorePassword = System.getenv("QUOTACHECK_STORE_PASSWORD").orEmpty()
val releaseKeyAlias = System.getenv("QUOTACHECK_KEY_ALIAS").orEmpty()
val releaseKeyPassword = System.getenv("QUOTACHECK_KEY_PASSWORD").orEmpty()
val missingReleaseSigningVariables = listOf(
    "QUOTACHECK_STORE_FILE" to releaseStoreFile,
    "QUOTACHECK_STORE_PASSWORD" to releaseStorePassword,
    "QUOTACHECK_KEY_ALIAS" to releaseKeyAlias,
    "QUOTACHECK_KEY_PASSWORD" to releaseKeyPassword,
).filter { (_, value) -> value.isBlank() }.map { (name, _) -> name }

tasks.configureEach {
    if (name.contains("release", ignoreCase = true)) {
        doFirst {
            check(oauthClientId.isNotBlank() && oauthClientSecret.isNotBlank()) {
                "Release builds require android-app/private-api.properties."
            }
            check(missingReleaseSigningVariables.isEmpty()) {
                "Release builds require signing environment variables: " +
                    missingReleaseSigningVariables.joinToString(", ")
            }
        }
    }
}

android {
    namespace = "com.quotacheck.app"
    compileSdk = 36
    buildToolsVersion = "35.0.0"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.quotacheck.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "OAUTH_CLIENT_ID", "\"$oauthClientId\"")
        buildConfigField("String", "OAUTH_CLIENT_SECRET", "\"$oauthClientSecret\"")
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.create("release").apply {
                if (missingReleaseSigningVariables.isEmpty()) {
                    storeFile = file(releaseStoreFile)
                    storePassword = releaseStorePassword
                    keyAlias = releaseKeyAlias
                    keyPassword = releaseKeyPassword
                }
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)

    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.mockwebserver)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestRuntimeOnly(libs.androidx.test.runner)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
