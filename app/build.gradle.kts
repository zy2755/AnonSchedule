import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        FileInputStream(keystorePropsFile).use { load(it) }
    }
}

android {
    namespace = "com.anon.schedule"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.anon.schedule"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "1.0.11"
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 对比用：开启 R8 代码压缩 + 资源压缩，贴近真实发布包
            isMinifyEnabled = true
            isShrinkResources = true
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    lint {
        // AGP 8.7 自带的 lint 与本项目升级后的 Kotlin 2.2 分析 API 不兼容，
        // 该检测器会直接抛 IncompatibleClassChangeError；项目未使用 LiveData，禁用无实际损失
        disable += "NullSafeMutableLiveData"
    }
}

// Haze 1.7.1 把 androidx.activity 整组（atomic group）抬到 1.11.0，
// 而 1.11.0 要求 AGP 8.9.1+ / compileSdk 36。Haze 源码只用到 ComponentActivity
// （1.9.2 就有），所以整组钉回工程原版本即可，无需为玻璃升级整条构建链。
configurations.all {
    resolutionStrategy.force(
        "androidx.activity:activity:1.9.2",
        "androidx.activity:activity-ktx:1.9.2",
        "androidx.activity:activity-compose:1.9.2",
    )
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.11.01"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // 毛玻璃：NPatch 同代的 Haze 1.x（单体库，含 blur）。
    // 选 1.7.1 而非 2.1.x 的原因：它依赖 Compose 1.9.3 / Kotlin 2.2.21，
    // 与本工程 BOM 2025.11.01（Compose 1.9.5）/ Kotlin 2.2.21 对齐，不会把
    // ui 顶到 1.12.x 而反向要求 AGP 9.1 + compileSdk 36。2.x 是模块化新 API，
    // 等构建链整体升级后再迁移。
    implementation("dev.chrisbanes.haze:haze:1.7.1")
}
