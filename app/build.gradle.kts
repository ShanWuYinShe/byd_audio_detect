plugins {
    id("com.android.application")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.byd.audiochanneldetector"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.byd.audiochanneldetector"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // release 签名凭据约定：从环境变量读取
    //   ANDROID_KEYSTORE            keystore 文件路径（相对路径基于 app 模块目录）
    //   ANDROID_KEYSTORE_PASSWORD   keystore 口令
    //   ANDROID_KEY_ALIAS           key 别名
    //   ANDROID_KEY_PASSWORD        key 口令
    // 四者全部非空才创建 release 签名配置；任一缺失时保持默认，
    // release 构建产出 unsigned APK 且不失败（CI 环境无凭据也能构建）。
    signingConfigs {
        if (
            !System.getenv("ANDROID_KEYSTORE").isNullOrBlank() &&
            !System.getenv("ANDROID_KEYSTORE_PASSWORD").isNullOrBlank() &&
            !System.getenv("ANDROID_KEY_ALIAS").isNullOrBlank() &&
            !System.getenv("ANDROID_KEY_PASSWORD").isNullOrBlank()
        ) {
            create("release") {
                storeFile = file(System.getenv("ANDROID_KEYSTORE")!!)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 仅当上面凭据齐全创建了 release 签名配置时挂接；
            // findByName 返回 null 时保持 unsigned 默认行为
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.05.01")
    implementation(composeBom)

    // core-ktx / lifecycle 原先依赖 compose BOM 覆盖范围隐式解析，
    // 现按 debugRuntimeClasspath 实际解析结果（1.16.0 / 2.9.4）显式声明版本
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.activity:activity-compose")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-android-compiler:2.59.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}

hilt {
    enableAggregatingTask = true
}