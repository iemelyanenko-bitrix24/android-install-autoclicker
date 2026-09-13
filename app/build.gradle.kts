plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ru.installclicker"
    compileSdk = 34

    defaultConfig {
        applicationId = "ru.installclicker"
        minSdk = 24
        // Намеренно низкий targetSdk: версия Android на ГУ неизвестна, а на
        // targetSdk <= 30 нет ужесточений видимости пакетов из Android 13+.
        targetSdk = 30
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Подписываем debug-ключом, чтобы assembleRelease давал APK,
            // который сразу ставится через adb install.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
