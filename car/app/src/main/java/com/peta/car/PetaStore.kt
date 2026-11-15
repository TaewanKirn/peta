package com.peta.car

import com.peta.car.place.GeoPoint
import com.peta.car.place.Place
import com.peta.car.place.PlaceDistance
import com.peta.car.trip.TripPhase
import com.peta.car.trip.TripPlan
import com.peta.car.trip.TripStage
import com.peta.car.trip.TripStop
import com.peta.car.trip.Visit
import com.peta.car.vehicle.VehicleState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** C06 휴식 제안 (주행 중 음성 질문 + 정적 화면). 경유지가 없을 때만 뜬다. */
data class RestPrompt(
    val stop: TripStop,
    val kmAhead: Double,
    val minutesAway: Int,
    val drivenMinutes: Long,
    val listening: Boolean = false,
    /** true = "응", false = "아니", null = 아직 */
    val answer: Boolean? = null,
    /** 한 번 못 알아들어 다시 묻는 중 (C06-E) */
    val retrying: Boolean = false,
)

/** C05-V 「쉬었다 갈 곳 변경/취소」 음성 질문 */
data class StopQuestion(
    val stop: TripStop,
    val listening: Boolean = false,
    /** 답한 뒤 화면에 잠깐 보여 줄 문장 */
    val reply: String? = null,
)

data class PetaUiState(
    val vehicle: VehicleState = VehicleState(),
    val naviConnected: Boolean = false,
    val plan: TripPlan? = null,
    /** 폰에서 여행을 불러온 시각 */
    val syncedAt: Long? = null,
    /** false면 폰에 저장된 여행이 없어 샘플 여행을 보여주는 중 */
    val planFromPhone: Boolean = false,
    /** 여행 조회가 네트워크 오류로 실패한 상태 (C12-B) */
    val syncOffline: Boolean = false,
    /** 경유지를 들렀는지. 다음 쉴 곳 표시에 쓴다. */
    val waypointVisited: Boolean = false,
    /** 내비가 경로를 찾지 못한 상태. 안내 중으로 표시하지 않는다. */
    val routeFailed: Boolean = false,
    /** 내비가 경로 안내를 실제로 시작했는지 */
    val routeStarted: Boolean = false,
    /** 출발 준비(C04)에서 정한 쉬었다 갈 곳. null = 쉬지 않고 가기 */
    val plannedStop: TripStop? = null,
    /** 사용자가 쉬었다 갈 곳을 직접 정했는지 (안 정했으면 추천 경유지) */
    val plannedStopDecided: Boolean = false,
    /** 주행 중 향하는 쉬었다 갈 곳. 내비는 이곳을 목적지로 안내하고, 쉬고 나면 원래 목적지로 다시 안내한다. */
    val pendingStop: TripStop? = null,
    /** 응급 화면에서 「길 안내」를 누른 병원. 있으면 주행 카드(C05)가 원래 목적지 대신 이 병원을 보여 준다 */
    val emergencyPlace: com.peta.car.place.Place? = null,
    /** C05-V 음성 질문 중 */
    val stopQuestion: StopQuestion? = null,
    /** 설정(C14) 음성 안내 */
    val voiceEnabled: Boolean = true,
    /** 지금 향하는 곳(들를 곳 또는 목적지)까지 남은 거리 */
    val legRemainKm: Double? = null,
    val stage: TripStage = TripStage.WELCOME,
    val phase: TripPhase = TripPhase.READY,
    val currentStop: TripStop? = null,
    /** 경유지에서 [휴식 시작]을 눌렀는지 (C07 → C08) */
    val resting: Boolean = false,
    /** C07 [휴식 시작] 펫 케어 공조 상태: null(안 켬) · "pending" · "on" · "failed" */
    val petClimate: String? = null,
    val parkedAt: Long? = null,
    /** 정차 중 운전석 도어가 열린 시각 (하차 감지) */
    val leftVehicleAt: Long? = null,
    val stayedMinutes: Long = 0,
    val delayMinutes: Long = 0,
    /** 길 안내 시작 후 함께 달린 시간 (C04) */
    val tripDrivingMinutes: Long = 0,
    /** 게이지를 매끄럽게 그리기 위한 밀리초 단위 값 */
    val tripDrivingMillis: Long = 0,
    /** 차량 속도로 잰 주행 거리(km) */
    val tripKm: Double = 0.0,
    val continuousDrivingMinutes: Long = 0,
    val restAfterMinutes: Long = PetaConfig.DRIVER_REST_AFTER_MINUTES,
    val restDue: Boolean = false,
    val restPrompt: RestPrompt? = null,
    val restAccepted: Boolean = false,
    val routeMinutes: Int? = null,
    val remainMinutes: Int? = null,
    val location: GeoPoint = DEFAULT_LOCATION,
    val nearbyRestPlaces: List<PlaceDistance> = emptyList(),
    val hospitals: List<PlaceDistance> = emptyList(),
    /** 단골 병원에 도로 거리 · 시간을 붙인 것. 아직 못 쟀으면 null이고 계획의 값을 쓴다. */
    val regularHospital: Place? = null,
    /** 서버에서 장소를 새로 받아 온 횟수. 검색 화면이 다시 그려지게 하는 신호다. */
    val placesRev: Int = 0,
    val pendingReview: Visit? = null,
) {
    companion object {
        /**
         * 내비 위치를 못 받을 때 쓰는 기본 위치. 에뮬레이터 Pleos 내비의 위치(성남 수정구, 37.45 · 127.10)에 맞춘다.
         * 에뮬레이터 내비는 위치 입력(geo fix · NMEA)을 받지 않아 이 위치에 고정돼 있다(2026-11-13 확인).
         */
        val DEFAULT_LOCATION = GeoPoint(37.4500, 127.1000)
    }
}

/** 화면 → 서비스로 보내는 사용자 동작. 화면 번호는 2026-11-12에 다시 매긴 번호다. */
interface PetaActions {
    /** C02 [이 여행으로 시작] */
    fun startTrip()
    /** C03 [출발하기] / [건너뛰기] */
    fun confirmBriefing()
    /** C04 [길 안내 시작] */
    fun startGuidance()
    /** C04 [다른 곳 고르기] → 검색에서 고른 곳을 경유지로 */
    fun planStop(place: Place)
    /** C04 [쉬지 않고 가기] */
    fun planNoStop()
    /** C04 쉬지 않고 가기를 고른 뒤 추천 경유지로 되돌리기 */
    fun planRecommendedStop()
    /** C04-E [다시 찾기] */
    fun retryRoute()
    /** C04-E [이전으로] */
    fun backToSummary()
    /** C05 [쉬었다 갈 곳 변경/취소] → C05-V 음성 질문 */
    fun askStopChange()
    /** C07 [휴식 시작]: 앱 안 휴식 상태(C08). 차량 펫 케어 모드 연동은 SDK에 API가 없다 */
    fun startRest()
    fun addWaypoint(place: Place)
    fun routeTo(place: Place)
    /** C01-R [목적지 변경하기] → 검색 「여행하기 좋은 곳」에서 고른 곳으로 여행 계획의 목적지를 바꾼다 */
    fun changeDestination(place: Place)
    fun submitReview(stars: Int)
    fun dismissReview()
    fun refreshNearby()
    fun resetTrip()

    // 데모: 에뮬레이터 Virtual Console에 내비 카드가 없어서 도착 이벤트를 직접 발생시킨다.
    fun simulateWaypointArrival()
    fun simulateDestinationArrival()
    fun simulateLongDrive()
    fun answerRest(yes: Boolean)

    /** 여행 중 쉬었다 갈 곳을 정해 바로 안내한다 (C06 "응", 검색 탭 [경유지로 추가]) */
    fun chooseStop(place: Place)
    /** 쉬었다 갈 곳을 취소하고 목적지로 계속 간다 */
    fun skipStop()

    /** C01-E [다시 시도] */
    fun retrySync()
    /** C01-E [샘플 여행으로 시작] · C01 [여기로 떠나기] (모바일 여행이 없을 때) */
    fun useSampleTrip()

    /** C12·C13 검색어로 서버(관광공사)에서 장소를 찾아 온다. 끝나면 화면이 다시 그려진다. */
    fun searchPlaces(query: String)

    /**
     * C13-E [범위 넓혀 찾기]: 반경 20km(관광공사 위치 조회의 최대)에서 못 찾았을 때
     * 낱말로 전국을 찾아 가까운 순으로 보여 준다. 끝나면 화면이 다시 그려진다.
     */
    fun widenSearch(keyword: String)

    /** C01 [범위 넓히기]: 관광공사 위치 조회 반경을 넓혀(km) 주변 장소를 다시 받는다. 끝나면 화면이 다시 그려진다. */
    fun widenNearby(radiusKm: Int)

    /** C12 마이크: 검색어를 말로 받는다 */
    fun listenQuery(onResult: (String?) -> Unit)
    /** C14 음성 안내 켜기/끄기 */
    fun setVoiceEnabled(on: Boolean)
    /** C14 [앱 초기화] */
    fun resetApp()
}

/** 서비스와 화면이 공유하는 상태 저장소 */
object PetaStore {
    private val _state = MutableStateFlow(PetaUiState())
    val state: StateFlow<PetaUiState> = _state.asStateFlow()

    @Volatile
    var actions: PetaActions? = null

    internal fun update(transform: (PetaUiState) -> PetaUiState) = _state.update(transform)
}
