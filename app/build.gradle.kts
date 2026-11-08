plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 版本号由 CI 的运行序号决定，每次推送自动递增；本地编译默认为 1
val ciRun = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.eric.toolbox"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.eric.toolbox"
        minSdk = 26
        targetSdk = 35
        versionCode = ciRun
        versionName = "2.1.$ciRun"
        buildConfigField("String", "UPDATE_REPO", "\"AoYuan123/eric-toolbox\"")
        buildConfigField("String", "GITEE_REPO", "\"aos-promise/eric-toolbox-release\"")
    }

    signingConfigs {
        create("release") {
            val ks = System.getenv("KEYSTORE_FILE")
            if (!ks.isNullOrBlank() && file(ks).isFile) {
                storeFile = file(ks)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS") ?: "eric"
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val rel = signingConfigs.getByName("release")
            signingConfig = if (rel.storeFile != null) rel else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // 影视板块：播放器 + 海报加载
    // Media3 1.9+ 要求 compileSdk 36，本工程锁在 35（AGP 8.7.3 官方推荐上限也是 35），
    // 所以停在 1.6.1——这个版本的播放器 API 与我们要用的完全一致，不要随手升。
    val media3 = "1.6.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // 读书 › 听书：通知栏/锁屏播放控制、耳机按键（MediaSessionCompat + MediaStyle）
    implementation("androidx.media:media:1.7.0")

    // 影视板块：TVBox 的 Spider 插件运行时。
    // 这些 jar 是照 TVBox 的类签名编译的，运行时必须能解析到 okhttp3 / gson，
    // 否则一加载就 NoClassDefFoundError。okhttp 本来就被 Coil 带进来了，
    // 这里显式声明是为了锁住版本（3.x 与 4.x 的类名/方法签名一致，jar 都能用）。
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
