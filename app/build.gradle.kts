plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.skippy.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.skippy.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"
        // Base64 SHA-1 of your signing certificate (see README). Put it in gradle.properties as MSAL_SIGNATURE_HASH.
        manifestPlaceholders["msalSignatureHash"] =
            (project.findProperty("MSAL_SIGNATURE_HASH") as String?) ?: "REPLACE_WITH_SIGNATURE_HASH"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.microsoft.identity.client:msal:6.0.1")
    testImplementation("junit:junit:4.13.2")
}
