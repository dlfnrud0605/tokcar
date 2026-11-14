import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// TokCar: 화면 시안을 옮긴 차량용 앱.
// applicationId 는 코드 패키지(namespace)와 같은 com.example.tokcar. Pleos 권한(pleos.car.permission.*)은 FUSED_LOCATION(런타임 허용) 말고는 모두 normal 이라
// 패키지 이름과 상관없이 설치만 하면 받는다(에뮬레이터 v2.1.0 에서 protectionLevel 확인).

// 가족 서버 주소(server). 저장소에 올리지 않는 local.properties 의 tokcar.server 로 정한다.
// 앱은 서버가 있어야 동작한다(더미 모드 없음). 안 적으면 에뮬레이터에서 이 PC 의 서버(http://10.0.2.2:8080)를 본다.
val localProps = Properties().also { p -> rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { p.load(it) } }
fun localProp(key: String): String = (localProps.getProperty(key) ?: providers.gradleProperty(key).orNull ?: "").trim()
val tokcarServer: String = localProp("tokcar.server").ifEmpty { "http://10.0.2.2:8080" }

// 릴리스 서명: 저장소에 올리지 않는 keystore.properties(storeFile · storePassword · keyAlias · keyPassword)가 있으면 그 키로 서명하고,
// 없으면 디버그 키로 서명한다(소스에서 release 를 만들어도 설치되게). keystore.properties.example 참고.
val keystoreProps = Properties().also { p -> rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { p.load(it) } }
val hasReleaseKey: Boolean = keystoreProps.getProperty("storeFile") != null

android {
    namespace = "com.example.tokcar"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.tokcar"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "TOKCAR_SERVER", "\"$tokcarServer\"")
    }

    signingConfigs {
        if (hasReleaseKey) create("release") {
            storeFile = file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            // R8 최적화는 끈다: Pleos Vehicle SDK 가 실행할 때 kotlin.Deprecated 의 replaceWith 를 리플렉션으로 읽는데, 켜면 그 표시가 지워져
            // 시작하자마자 IncompleteAnnotationException 으로 죽는다(2026-11-14 에뮬레이터에서 확인).
            optimization {
                enable = false
            }
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    // 옛 앱과 같은 SDK 묶음(내비가 현재 위치를 줄 때 FusedLocation 이 필요할 수 있음)
    implementation("ai.pleos.playground:FusedLocation:1.1.3")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // 가족 서버: REST + WebSocket
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json.test)
}
