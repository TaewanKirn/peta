import java.util.Properties

plugins {
    id("com.android.application")
}

// 공공데이터포털 인증키 등 비밀 값은 local.properties(git 제외)에서 읽는다.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.peta.car"
    compileSdk = 37   // Android Studio 기본 설치 플랫폼

    // AAOS Car API: CarUxRestrictionsManager (Pleos 주행 규제 가이드 필수)
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.peta.car"
        minSdk = 28        // Pleos 요구사항: API 28 이상
        targetSdk = 34     // Pleos Connect 에뮬레이터 = API 34
        versionCode = 1
        versionName = "0.2.0"

        // 한국관광공사 반려동물 동반여행 API 인증키(Decoding). 비어 있으면 샘플 데이터로 동작한다.
        val tourKey = localProps.getProperty("TOUR_API_KEY", "").trim()
        buildConfigField("String", "TOUR_API_KEY", "\"$tourKey\"")
        // 카카오 로컬(REST) 키. 동물병원 검색에 쓴다. 비어 있으면 샘플 병원으로 동작한다.
        val kakaoKey = localProps.getProperty("KAKAO_REST_KEY", "").trim()
        buildConfigField("String", "KAKAO_REST_KEY", "\"$kakaoKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 공모전 제출용: 별도 키스토어 없이 Android 디버그 키로 서명한다(설치·실행에는 문제 없다).
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Pleos Playground 공개 SDK
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // 3D 보리·오브젝트 애니메이션 (assets/lottie/bori3d → res/raw)
    implementation("com.airbnb.android:lottie:6.6.7")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
