import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // AGP 9 embarque Kotlin ("built-in Kotlin") : pas besoin de org.jetbrains.kotlin.android
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Clés Supabase : dans local.properties (non versionné) ou en variables d'environnement.
//   supabase.url=https://xxxx.supabase.co
//   supabase.key=sb_publishable_...
// Vides → l'app tourne en mode hors ligne avec des amis simulés.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(prop: String, env: String): String =
    (localProps.getProperty(prop) ?: System.getenv(env) ?: "").trim()

android {
    namespace = "app.hopla"
    // Material 3 1.5.0 (BOM 2026.11.01) demande compileSdk 37.1
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "app.hopla"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "SUPABASE_URL", "\"${secret("supabase.url", "SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${secret("supabase.key", "SUPABASE_KEY")}\"")
    }

    // Signature de release : renseigne ces 4 lignes dans local.properties (jamais dans git)
    //   release.storeFile=C:/chemin/vers/hopla-release.jks
    //   release.storePassword=...
    //   release.keyAlias=hopla
    //   release.keyPassword=...
    val releaseStore = localProps.getProperty("release.storeFile")
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = localProps.getProperty("release.storePassword")
                keyAlias = localProps.getProperty("release.keyAlias")
                keyPassword = localProps.getProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (releaseStore != null) signingConfig = signingConfigs.getByName("release")
            // R8 désactivé pour la 1.0 : on l'activera après avoir testé les règles Supabase/Ktor
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.okhttp)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
