package com.peta.car

object PetaConfig {
    /** 시연용이면 true: 시간을 빠르게 흘리고(1초 = 1분), 내비 도착 이벤트를 주행 시간으로 대신 만든다. */
    const val DEMO_MODE = true

    /** SDK 가용성 점검(logcat SdkProbe). 시연 빌드에서는 false */
    const val SDK_PROBE = false

    /** 데모 배속. 실제 1초가 앱 속 2분 → 1시간 40분 여행이 약 1분 주행으로 끝나 데모 전체가 3분 안팎 */
    val TIME_SCALE: Long = if (DEMO_MODE) 120 else 1
    private val epoch = System.currentTimeMillis()

    /** 체류/운전 시간 계산용 시계 (데모에서는 배속 적용). 화면에 보이는 시각(오전 4:32 등)은 실제 시계를 쓴다. */
    fun now(): Long = epoch + (System.currentTimeMillis() - epoch) * TIME_SCALE

    /** 정차·체류 시간은 덜 빠르게 (데모: 1초 = 1분) — 쉬는 장면을 보여줄 시간이 필요해서 */
    private val STAY_TIME_SCALE: Long = if (DEMO_MODE) 60 else 1
    fun stayNow(): Long = epoch + (System.currentTimeMillis() - epoch) * STAY_TIME_SCALE

    /** 정차 후 "N분째 머물고 있어요" 알림 기준 / 반복 간격 */
    const val STAY_ALERT_MINUTES = 30L
    const val STAY_REPEAT_MINUTES = 15L

    /** 운전자 기준 연속 운전 한도. 실제 기준 = min(운전자 한도, 반려견 휴식 주기) */
    const val DRIVER_REST_AFTER_MINUTES = 120L

    /** 이만큼 정차하면 "쉬었다"로 보고 연속 운전 시간을 초기화 */
    const val REST_RESET_PARKED_MINUTES = 15L

    /** 음성 질문("들를까요?") 후 답을 기다리는 실제 시간. 데모에서는 답이 없으면 "응"으로 처리 */
    val VOICE_ANSWER_TIMEOUT_MILLIS: Long = if (DEMO_MODE) 4_000L else 8_000L

    /** 화면 갱신 주기 (실제 시간) */
    const val TICK_MILLIS = 500L

    /** 서버 장소 데이터. 키가 local.properties에 없으면 비어 있고, 그때는 샘플 데이터로 동작한다. */
    val TOUR_API_KEY: String = BuildConfig.TOUR_API_KEY
    val KAKAO_REST_KEY: String = BuildConfig.KAKAO_REST_KEY

    /**
     * [가정] 시연용 가상 수치. 관광공사 API에 후기·이용자 수가 없어 장소 id를 시드로 만든 값을 채운다.
     * 실제 사용자 데이터가 아니므로 제출 문서에 "시연용 가상 수치"로 밝힌다. 끄면 비워 둔다.
     */
    const val DEMO_PLACE_STATS = true

    /** 이 거리(km)보다 많이 움직이면 주변 장소를 서버에서 다시 받는다. */
    const val PLACES_REFRESH_KM = 3.0

    /** 모바일에서 저장한 여행 조회 주소. 10.0.2.2는 에뮬레이터에서 본 PC의 localhost다. */
    const val TRIP_SYNC_URL = "http://10.0.2.2:5173/api/trip"
    const val TRIP_SYNC_INTERVAL_MILLIS = 3_000L

    /** 플로우 점검 페이지(모바일 dev 서버 /admin)에서 화면 이동 명령을 받는다. 제출 빌드에서는 false */
    const val DEBUG_BRIDGE = false // 제출 빌드: 시연 모드(DEMO_MODE)와 분리해 끈다. 개발 중 /admin을 쓰려면 true로
    const val DEBUG_SYNC_URL = "http://10.0.2.2:5173/api/debug/sync"
    const val DEBUG_SYNC_INTERVAL_MILLIS = 1_000L

    /** 시연용 거리 배속: 80km/h로 1초에 약 0.9km. 들를 곳·목적지 도착이 너무 빨리 오지 않게 시간 배속과 따로 둔다. */
    val DISTANCE_SCALE: Double = if (DEMO_MODE) 40.0 else 1.0

    /** 출발지에서 목적지까지 거리(시연용). 실제로는 내비 경로 거리를 쓴다. */
    /** 휴식 시작 때 공조 온도(°C) [가정]. 반려견이 차에서 쉬기 편한 실내 온도로 둔 시안 값 */
    const val PET_CARE_CELSIUS = 22f

    const val DEMO_ROUTE_KM = 195.0 // 성남 수정구(내비 위치) → 강릉 직행 도로 거리 (카카오 길찾기 2026-11-13)

    /** 시연에서 C09를 보여주는 시간 */
    const val DEMO_ARRIVAL_SCREEN_MILLIS = 8_000L
}
