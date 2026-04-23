plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("plugin.serialization") version "1.9.25"
}

android {
    namespace = "com.example.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.android"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Supabase keys from android/.env (copy from .env.example)
        val envFile = rootProject.file(".env")
        var supabaseUrl = "https://placeholder.supabase.co"
        var supabaseAnonKey = ""
        var apiBaseUrl = ""
        if (envFile.exists()) {
            envFile.readLines()
                .filter { it.contains("=") && !it.trim().startsWith("#") }
                .forEach { line ->
                    val parts = line.split("=", limit = 2)
                    if (parts.size == 2) {
                        when (parts[0].trim()) {
                            "SUPABASE_URL" -> supabaseUrl = parts[1].trim().removeSurrounding("\"")
                            "SUPABASE_ANON_KEY" -> supabaseAnonKey = parts[1].trim().removeSurrounding("\"")
                            "API_BASE_URL" -> apiBaseUrl = parts[1].trim().removeSurrounding("\"")
                        }
                    }
                }
        }
        buildConfigField("String", "SUPABASE_URL", "\"${supabaseUrl.replace("\"", "\\\"")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${supabaseAnonKey.replace("\"", "\\\"")}\"")
        buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl.replace("\"", "\\\"")}\"")
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
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // Supabase
    implementation(platform("io.github.jan-tennert.supabase:bom:2.0.2"))
    implementation("io.github.jan-tennert.supabase:gotrue-kt")
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.serialization.json)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
