plugins { id("com.android.application") }

android {
    namespace = "com.example.rainbowtrailquest"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.rainbowtrailquest"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
