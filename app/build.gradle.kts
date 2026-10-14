plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "br.com.focoleitura"
    compileSdk = 35
    defaultConfig {
        applicationId = "br.com.focoleitura"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "2.3.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        getByName("release") { isMinifyEnabled = false }
    }
}
dependencies { implementation("androidx.webkit:webkit:1.12.1"); implementation("com.tom-roush:pdfbox-android:2.0.27.0") }
