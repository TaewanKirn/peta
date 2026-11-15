package com.peta.car

import com.peta.car.trip.withRang
import com.peta.car.trip.withRo
import com.peta.car.trip.withWa
import com.peta.car.trip.withEulReul
import java.io.IOException
import org.json.JSONException
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.peta.car.debug.DebugBridge
import com.peta.car.navi.NaviEvent
import com.peta.car.navi.NaviGateway
import com.peta.car.navi.PleosNaviGateway
import com.peta.car.navi.RouteEstimate
import com.peta.car.place.DemoStats
import com.peta.car.place.GeoPoint
import com.peta.car.place.KakaoLocalApi
import com.peta.car.place.Place
import com.peta.car.place.PlaceRepository
import com.peta.car.place.PlaceType
import com.peta.car.place.TourApi
import com.peta.car.place.distanceKm
import com.peta.car.remote.HttpPetaApi
import com.peta.car.remote.PetaApi
import com.peta.car.remote.SampleTrip
import com.peta.car.rest.RestAdvisor
import com.peta.car.stay.StayEvent
import com.peta.car.stay.StayTracker
import com.peta.car.trip.ReviewStore
import com.peta.car.trip.TripPhase
import com.peta.car.trip.TripPlan
import com.peta.car.trip.TripStage
import com.peta.car.trip.TripStop
import com.peta.car.trip.Visit
import com.peta.car.vehicle.Gear
import com.peta.car.vehicle.PleosVehicleStateSource
import com.peta.car.vehicle.VehicleState
import com.peta.car.vehicle.VehicleStateSource
import com.peta.car.voice.StopChange
import com.peta.car.voice.VoiceAssistant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 여행 전체 흐름을 관리하는 Foreground Service. (화면 번호는 2026-11-12 기준)
 * C02 여행 확인 → C03 준비물 → C04 경로 요약(쉬었다 갈 곳 결정) → 길 안내
 * → C05 주행 중(경유지까지) / C06 휴식 제안(경유지가 없을 때만, 음성)
 * → C07 쉬었다 갈 곳 도착 → C08 휴식 중 → C09 목적지 도착 → 다시 탈 때 C10 후기
 */
class PetaService : LifecycleService(), PetaActions {

    private lateinit var vehicleSource: VehicleStateSource
    private lateinit var navi: NaviGateway
    private lateinit var notifier: PetaNotifier
    private lateinit var reviews: ReviewStore
    private lateinit var voice: VoiceAssistant

    private val api: PetaApi = HttpPetaApi(PetaConfig.TRIP_SYNC_URL)
    private val places = PlaceRepository.shared
    private val tour = TourApi(PetaConfig.TOUR_API_KEY)
    private val kakao = KakaoLocalApi(PetaConfig.KAKAO_REST_KEY)
    /** 서버에서 주변 장소를 마지막으로 받은 위치·시각. 3km 넘게 움직이거나 실패 후 30초가 지나면 다시 받는다. */
    private var placesAt: GeoPoint? = null
    private var placesAttemptMillis = 0L
    private var placesFailed = false
    private var placesFetching = false
    /** 위치가 크게 바뀌면 비운다. 장소 id → 내비가 잰 도로 거리·시간 */
    private val routeCache = HashMap<String, RouteEstimate>()
    private val stay = StayTracker(
        thresholdMillis = PetaConfig.STAY_ALERT_MINUTES * MINUTE,
        repeatMillis = PetaConfig.STAY_REPEAT_MINUTES * MINUTE,
        clock = PetaConfig::stayNow,
    )
    private var rest = restAdvisorFor(null)
    private var wasParked: Boolean? = null

    // 길 안내 시작 후 함께 달린 시간·거리: 차량 속도(PERF_VEHICLE_SPEED)가 있을 때만 쌓는다
    private var tripDrivingMillis = 0L
    private var tripKm = 0.0
    private var lastTickApp = PetaConfig.now()
    private var wasMoving = false
    // 지금 향하는 곳(들를 곳 또는 목적지)까지의 구간. 시연에서는 이 거리를 다 달리면 도착으로 처리한다.
    private var legStartKm = 0.0
    private var legKm = PetaConfig.DEMO_ROUTE_KM

    // C06 휴식 제안: 연속 운전이 이 값(분)을 넘으면 묻는다. "아니"면 운전자 상한까지 미룬다(규칙 17).
    private var restAskAtMinutes = PetaConfig.DRIVER_REST_AFTER_MINUTES
    private var restAsksLeft = true

    private var delayAnnounced = false
    private var destinationArrived = false
    private var visitedWaypoint = false
    // 목적지에서 운전석 문이 열린 횟수. 1번째 = 내림, 2번째 = 다시 탐 → 후기(규칙 19)
    private var destinationDoorOpens = 0
    private var doorWasOpen = false

    // 플로우 점검 페이지(/admin)에서 기어·속도를 대신 정할 때. Virtual Console로 실제 기어를 바꾸면 풀린다.
    private var vehicleOverride: VehicleState? = null
    private var overrideRealGear: Gear? = null
    /** 점검 페이지가 정한 여행을 폰 여행 조회가 덮어쓰지 않게 잠깐 멈춘다 */
    private var debugHoldSync = false

    private val ui get() = PetaStore.state.value
    private val now get() = System.currentTimeMillis()
    /** 운전/체류 시간 계산용 (데모 배속 적용) */
    private val appNow get() = PetaConfig.now()

    override fun onCreate() {
        super.onCreate()
        notifier = PetaNotifier(this)
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, PetaNotifier.ID_MONITOR, notifier.monitorNotification(), type)

        reviews = ReviewStore(this)
        voice = VoiceAssistant(this)
        vehicleSource = PleosVehicleStateSource(this).also { it.start() }
        navi = PleosNaviGateway(this).also { it.start() }
        PetaStore.actions = this
        PetaStore.update { it.copy(pendingReview = reviews.pending()) }
        // 내비 위치 이벤트가 오기 전에도(에뮬레이터는 오지 않는다) 기본 위치 기준으로 주변 장소를 받아 둔다
        syncPlaces()

        lifecycleScope.launch {
            // M03 "여행 계획 저장하기"로 올라간 여행을 홈에 있는 동안 계속 확인한다
            while (isActive) {
                if (ui.stage == TripStage.WELCOME && !debugHoldSync) syncTripPlan()
                delay(PetaConfig.TRIP_SYNC_INTERVAL_MILLIS)
            }
        }
        lifecycleScope.launch { vehicleSource.state.collect { onVehicle(effective(it)) } }
        if (PetaConfig.DEBUG_BRIDGE) lifecycleScope.launch {
            while (isActive) {
                DebugBridge.sync(PetaConfig.DEBUG_SYNC_URL, debugStatus())?.let { applyDebug(it) }
                delay(PetaConfig.DEBUG_SYNC_INTERVAL_MILLIS)
            }
        }
        lifecycleScope.launch { navi.events.collect { onNavi(it) } }
        lifecycleScope.launch { navi.connected.collect { c -> PetaStore.update { it.copy(naviConnected = c) } } }
        lifecycleScope.launch {
            while (isActive) {
                delay(PetaConfig.TICK_MILLIS)
                onTick()
            }
        }
    }

    private suspend fun syncTripPlan() {
        val fromPhone = try {
            api.fetchTripPlan()
        } catch (e: IOException) {
            Log.w(TAG, "trip sync failed: ${e.message}")
            // 연결이 끊겨도 이미 받은 여행은 유지한다. 계획이 없으면 샘플 여행으로 시작한다.
            PetaStore.update { it.copy(syncOffline = true) }
            useSampleTrip()
            return
        } catch (e: JSONException) {
            Log.w(TAG, "trip json invalid: ${e.message}")
            PetaStore.update { it.copy(syncOffline = true) }
            useSampleTrip()
            return
        }
        if (ui.syncOffline) PetaStore.update { it.copy(syncOffline = false) }
        val plan = fromPhone ?: SampleTrip.plan
        if (plan == ui.plan) return
        rest = restAdvisorFor(plan)
        PetaStore.update {
            it.copy(
                plan = plan,
                planFromPhone = fromPhone != null,
                syncedAt = now,
                restAfterMinutes = restAfterMinutes(plan),
            )
        }
        refreshNearby()
    }

    override fun onDestroy() {
        PetaStore.actions = null
        vehicleSource.stop()
        navi.stop()
        voice.release()
        super.onDestroy()
    }

    // ---- 차량 상태 (Pleos Vehicle SDK) ----

    private fun onVehicle(v: VehicleState) {
        stay.onVehicleState(v).forEach { handleStay(it) }

        if (v.gearKnown && wasParked != v.isParked) {
            val first = wasParked == null
            wasParked = v.isParked
            if (!first) {
                if (v.isParked) onStopped() else onDeparted()
            }
        }
        // 연속 운전 시간은 "실제로 움직이는 동안"만 센다 (신호 대기·정차 제외)
        if (v.isMoving != wasMoving) {
            wasMoving = v.isMoving
            rest.onParkedChanged(!v.isMoving)
        }
        // 목적지에서 내렸다가 다시 타면 후기를 묻는다 (규칙 19)
        val doorOpened = v.driverDoorOpen && !doorWasOpen
        doorWasOpen = v.driverDoorOpen
        if (doorOpened && ui.phase == TripPhase.AT_DESTINATION && v.isParked) {
            destinationDoorOpens++
            if (destinationDoorOpens >= 2) finishTrip()
        }
        publish(v)
    }

    private fun currentTripDrivingMillis(): Long = tripDrivingMillis

    private fun onStopped() {
        navi.refreshLocation()
        refreshNearby()
        if (PetaConfig.DEMO_MODE && ui.phase == TripPhase.AT_DESTINATION) finishTripForDemo()
    }

    private fun onDeparted() {
        when (ui.phase) {
            // 목적지에서 다시 출발 = 여행 종료. 후기는 다음 정차 때 묻는다.
            TripPhase.AT_DESTINATION -> finishTrip()
            TripPhase.AT_WAYPOINT -> {
                // 쉬고 다시 출발하면 내비에 목적지를 다시 찾아 안내한다
                val dest = ui.plan?.destination?.place
                legStartKm = tripKm
                legKm = dest?.routeKm ?: 30.0
                dest?.let { routeAndReturn(it) }
                PetaStore.update { it.copy(phase = TripPhase.DRIVING, currentStop = null, resting = false, petClimate = null, waypointVisited = true) }
                voice.speak("다시 출발할게요. ${ui.plan?.destination?.place?.name ?: "목적지"}까지 안전하게 갈게요.")
            }
            else -> if (ui.stage == TripStage.ON_TRIP) PetaStore.update { it.copy(phase = TripPhase.DRIVING) }
        }
        navi.refreshDestination()
    }

    private fun finishTrip() {
        if (ui.stage != TripStage.ON_TRIP) return
        navi.cancelRoute()
        resetTripState()
        PetaStore.update { it.copy(pendingReview = reviews.pending()) }
        // 경로를 취소하면 내비가 앞으로 나와 후기 화면(C10)을 가린다. 페타를 다시 앞으로 부른다.
        bringToFront()
        lifecycleScope.launch {
            delay(700)
            bringToFront()
        }
    }

    /** 시연: 다시 타는 동작 없이도 도착 안내(C09)를 잠깐 보여준 뒤 후기(C10)로 넘어간다. */
    private fun finishTripForDemo() {
        lifecycleScope.launch {
            delay(PetaConfig.DEMO_ARRIVAL_SCREEN_MILLIS)
            if (ui.phase != TripPhase.AT_DESTINATION) return@launch
            finishTrip()
        }
    }

    private fun handleStay(event: StayEvent) {
        when (event) {
            is StayEvent.Parked -> {
                delayAnnounced = false
                PetaStore.update { it.copy(leftVehicleAt = null) }
                lifecycleScope.launch { api.reportParked(event.at) }
            }
            is StayEvent.LongStay -> Unit // 체류 시간은 C08 화면과 지연 음성 안내로 알린다
            is StayEvent.LeftVehicle -> {
                PetaStore.update { it.copy(leftVehicleAt = now) }
                lifecycleScope.launch { api.reportLeftVehicle(event.parkedAt) }
            }
            is StayEvent.Departed -> {
                PetaStore.update { it.copy(leftVehicleAt = null) }
                lifecycleScope.launch { api.reportDeparted(event.stayedMinutes) }
            }
        }
    }

    // ---- 내비 (Pleos NaviHelper SDK) ----

    private fun onNavi(event: NaviEvent) {
        when (event) {
            is NaviEvent.RouteStarted -> PetaStore.update { it.copy(routeMinutes = event.durationSec / 60, routeFailed = false, routeStarted = true) }
            is NaviEvent.WaypointArrived -> {
                if (ui.stage != TripStage.ON_TRIP || ui.phase == TripPhase.AT_WAYPOINT) return
                val stop = ui.pendingStop?.takeIf { it.place.name == event.name || event.name.isBlank() }
                    ?: ui.plan?.waypoints?.getOrNull(event.index)
                    ?: ui.plan?.waypoints?.firstOrNull { it.place.name == event.name }
                    ?: adHocStop(event.name)
                arrive(stop, TripPhase.AT_WAYPOINT)
                voice.speak("${shortName(stop.place)}에 도착했어요. ${stop.nearbyInfo}")
            }
            is NaviEvent.DestinationArrived -> {
                if (ui.stage != TripStage.ON_TRIP || destinationArrived) return
                destinationArrived = true
                destinationDoorOpens = 0
                val stop = ui.plan?.destination ?: adHocStop(event.name)
                arrive(stop, TripPhase.AT_DESTINATION)
                voice.speak("${stop.place.name}에 도착했어요. 내리기 전 리드줄을 챙기세요.")
            }
            is NaviEvent.Location -> {
                PetaStore.update { it.copy(location = event.point) }
                refreshNearby()
            }
            is NaviEvent.DestinationRemain -> PetaStore.update { it.copy(remainMinutes = event.remainSec / 60) }
            is NaviEvent.Error -> {
                Log.w(TAG, "navi error: ${event.message}")
                if (event.message == "CAUSE_SEARCH_ROUTE" && !ui.routeFailed) {
                    PetaStore.update { it.copy(routeFailed = true) }
                    voice.speak("경로를 찾지 못했어요. 인터넷 연결을 확인해 주세요.")
                }
            }
        }
    }

    private fun arrive(stop: TripStop, phase: TripPhase) {
        bringToFront()
        val visit = Visit(ui.plan?.id.orEmpty(), stop.place.name, now)
        if (phase == TripPhase.AT_DESTINATION) reviews.save(visit)
        PetaStore.update { it.copy(phase = phase, currentStop = stop, resting = false, restPrompt = null, stopQuestion = null) }
        lifecycleScope.launch { api.reportVisit(visit) }
    }

    private fun adHocStop(name: String): TripStop {
        val place = ui.nearbyRestPlaces.firstOrNull { it.place.name == name }?.place
            ?: Place("adhoc", name.ifBlank { "경유지" }, ui.location, "", PlaceType.DOG_REST)
        return TripStop(place, plannedStayMinutes = 15, activity = "잠깐 쉬어가기", nearbyInfo = place.description)
    }

    /** 내비에 경로를 보내면 내비가 앞으로 나온다. 경로를 잠깐 보여 준 뒤 페타 화면으로 돌아온다(여행 시작과 같은 방식). */
    private fun routeAndReturn(place: Place) {
        navi.routeTo(place)
        lifecycleScope.launch {
            delay(3_000)
            bringToFront()
        }
    }

    /** 내비가 앞에 있어도 중요한 순간(휴식 제안, 도착)에는 페타 화면을 다시 띄운다 */
    private fun bringToFront() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        }.onFailure { Log.w(TAG, "bringToFront failed", it) }
    }

    // ---- C06 휴식 제안: 경유지가 없을 때만, 음성 한 문장 + "응"/"아니" ----

    /** 경유지 없이 달리는 중인지. 경유지를 이미 들렀어도 경유지 없음으로 본다 (규칙 16). */
    private fun noStopAhead() = ui.pendingStop == null || ui.waypointVisited

    private fun restCandidate(): TripStop? {
        val visited = ui.pendingStop?.takeIf { ui.waypointVisited }?.place?.id
        val planned = ui.plan?.plannedRest?.takeIf { it.place.id != visited }
        return planned ?: ui.nearbyRestPlaces.firstOrNull { it.place.id != visited }?.let { restStop(it.place) }
    }

    private fun promptRest() {
        val stop = restCandidate() ?: return
        bringToFront()
        val driven = rest.continuousDrivingMillis() / MINUTE
        val km = stop.place.routeKm ?: 5.0
        PetaStore.update { it.copy(restPrompt = RestPrompt(stop, km, etaMinutes(stop.place), driven)) }

        val pet = ui.plan?.pet?.name?.withRang().orEmpty()
        voice.speak("${driven}분째 달리고 있어요. $pet ${shortName(stop.place)}에서 쉬어갈까요? 응 또는 아니로 답해 주세요.") {
            listenRestAnswer()
        }
    }

    private fun listenRestAnswer() {
        PetaStore.update { s -> s.copy(restPrompt = s.restPrompt?.copy(listening = true)) }
        voice.listenYesNo(PetaConfig.VOICE_ANSWER_TIMEOUT_MILLIS) { answer ->
            val prompt = ui.restPrompt ?: return@listenYesNo
            when {
                answer != null -> answerRest(answer)
                !prompt.retrying -> {
                    // C06-E: 한 번 더 묻는다
                    PetaStore.update { it.copy(restPrompt = prompt.copy(listening = false, retrying = true)) }
                    voice.speak("잘 못 들었어요. 응 또는 아니로 다시 말해 주세요.") { listenRestAnswer() }
                }
                // 답이 없으면 계속 안내한다. 시연은 마이크가 없어 "응"으로 처리해 다음 장면으로 넘긴다.
                else -> answerRest(PetaConfig.DEMO_MODE)
            }
        }
    }

    override fun answerRest(yes: Boolean) {
        val prompt = ui.restPrompt ?: return
        if (prompt.answer != null) return
        PetaStore.update { it.copy(restPrompt = prompt.copy(listening = false, answer = yes)) }
        if (yes) {
            chooseStop(prompt.stop.place, announce = false)
            voice.speak("좋아요. ${shortName(prompt.stop.place).withRo()} 안내할게요.")
        } else {
            // "아니"면 운전자 상한(120분)까지 다시 묻지 않는다 (규칙 17, 제안)
            if (restAskAtMinutes < PetaConfig.DRIVER_REST_AFTER_MINUTES) restAskAtMinutes = PetaConfig.DRIVER_REST_AFTER_MINUTES
            else restAsksLeft = false
            voice.speak("알겠어요. ${ui.plan?.destination?.place?.name ?: "목적지"}까지 계속 갈게요.")
        }
        lifecycleScope.launch {
            delay(2_500)
            PetaStore.update { it.copy(restPrompt = null) }
        }
    }

    private fun restStop(place: Place) = TripStop(
        place = place,
        plannedStayMinutes = 15,
        activity = "${ui.plan?.pet?.name?.withRang().orEmpty()} 산책하고 물 마시기",
        nearbyInfo = "${ui.plan?.pet?.name?.withRang().orEmpty()} 물 한 모금 마시고 쉬어 가요.",
    )

    private fun stopFor(place: Place) = ui.plan?.plannedRest?.takeIf { it.place.id == place.id } ?: restStop(place)

    override fun chooseStop(place: Place) = chooseStop(place, announce = true)

    private fun chooseStop(place: Place, announce: Boolean) {
        val stop = stopFor(place)
        legStartKm = tripKm
        legKm = place.routeKm ?: 10.0
        visitedWaypoint = false
        routeAndReturn(place)
        PetaStore.update { it.copy(pendingStop = stop, restAccepted = true, waypointVisited = false) }
        if (announce) voice.speak("${shortName(place).withRo()} 안내할게요.")
    }

    override fun skipStop() {
        val dest = ui.plan?.destination?.place
        legStartKm = tripKm
        legKm = (PetaConfig.DEMO_ROUTE_KM - tripKm).coerceAtLeast(5.0)
        dest?.let { routeAndReturn(it) }
        PetaStore.update { it.copy(pendingStop = null) }
        voice.speak("알겠어요. 쉬지 않고 ${dest?.name ?: "목적지"}까지 갈게요.")
    }

    // ---- C05-V 쉬었다 갈 곳 변경/취소: 음성 질문 (규칙 13·14) ----

    override fun askStopChange() {
        val stop = ui.pendingStop?.takeIf { !ui.waypointVisited } ?: return
        if (ui.stopQuestion != null) return
        PetaStore.update { it.copy(stopQuestion = StopQuestion(stop)) }
        voice.speak("다른 곳으로 바꿀까요, 쉬지 않고 갈까요? 바꿔 줘 또는 쉬지 않고 가라고 말해 주세요.") {
            PetaStore.update { s -> s.copy(stopQuestion = s.stopQuestion?.copy(listening = true)) }
            voice.listenStopChange(PetaConfig.VOICE_ANSWER_TIMEOUT_MILLIS) { answer -> answerStopChange(stop, answer) }
        }
    }

    private fun answerStopChange(stop: TripStop, answer: StopChange?) {
        val reply = when (answer) {
            StopChange.CHANGE -> {
                val next = ui.nearbyRestPlaces.firstOrNull { it.place.id != stop.place.id }?.place
                if (next != null) {
                    chooseStop(next, announce = false)
                    "${shortName(next).withRo()} 바꿨어요"
                } else "바꿀 곳이 없어 ${shortName(stop.place).withRo()} 그대로 갈게요"
            }
            StopChange.SKIP -> {
                skipStop()
                "쉬지 않고 갈게요"
            }
            null -> "${shortName(stop.place).withRo()} 그대로 갈게요"
        }
        if (answer != StopChange.SKIP) voice.speak("$reply.")
        PetaStore.update { it.copy(stopQuestion = it.stopQuestion?.copy(listening = false, reply = reply)) }
        lifecycleScope.launch {
            delay(2_500)
            PetaStore.update { it.copy(stopQuestion = null) }
        }
    }

    private fun legRemainKm() = (legKm - (tripKm - legStartKm)).coerceAtLeast(0.0)

    // ---- 주기 처리 ----

    private fun onTick() {
        stay.tick()?.let { handleStay(it) }
        val v = vehicle()

        val nowApp = appNow
        val dt = nowApp - lastTickApp
        lastTickApp = nowApp
        if (ui.stage == TripStage.ON_TRIP && v.isMoving) {
            tripDrivingMillis += dt
            // 거리는 시간 배속과 따로 계산한다 (시연에서 도착이 너무 빨리 오지 않게)
            val realDt = dt / PetaConfig.TIME_SCALE.toDouble()
            tripKm += v.speedKph * realDt * PetaConfig.DISTANCE_SCALE / 3_600_000.0
        }

        if (ui.stage == TripStage.ON_TRIP && v.isDriving) {
            val continuous = rest.continuousDrivingMillis() / MINUTE
            if (restAsksLeft && ui.restPrompt == null && ui.stopQuestion == null && ui.phase == TripPhase.DRIVING &&
                noStopAhead() && continuous >= restAskAtMinutes
            ) promptRest()

            if (PetaConfig.DEMO_MODE && ui.phase == TripPhase.DRIVING && ui.currentStop == null && legRemainKm() <= 0.0) {
                // 에뮬레이터 내비는 지도 위 차가 움직이지 않아 도착 이벤트가 오지 않는다.
                // 대신 차량 속도로 잰 주행 거리가 지금 구간 거리에 닿으면 도착으로 처리한다.
                val stop = ui.pendingStop
                if (stop != null && !visitedWaypoint && !ui.waypointVisited) {
                    visitedWaypoint = true
                    onNavi(NaviEvent.WaypointArrived(0, stop.place.name))
                } else if (!destinationArrived) {
                    simulateDestinationArrival()
                }
            }
        }

        // C08: 계획보다 늦어지면 정차 중에 한 번 말로 알려준다
        if (ui.phase == TripPhase.AT_WAYPOINT && v.isParked && ui.delayMinutes > 0 && !delayAnnounced) {
            delayAnnounced = true
            voice.speak("계획보다 ${ui.delayMinutes}분 늦어지고 있어요. 천천히 쉬다 가도 괜찮아요.")
        }
        publish(v)
    }

    private fun publish(v: VehicleState) {
        val stayed = stay.elapsedMinutes()
        val planned = ui.currentStop?.plannedStayMinutes
        val delayMinutes = if (ui.phase == TripPhase.AT_WAYPOINT && planned != null) {
            (stayed - planned).coerceAtLeast(0)
        } else 0
        PetaStore.update {
            it.copy(
                vehicle = v,
                parkedAt = stay.parkedAt,
                stayedMinutes = stayed,
                delayMinutes = delayMinutes,
                // 차량이 Odometer 트립 시간을 주면 그 값을, 아니면(에뮬레이터) 앱이 잰 값을 쓴다
                tripDrivingMinutes = v.tripDrivingTime?.toLong() ?: (currentTripDrivingMillis() / MINUTE),
                tripDrivingMillis = currentTripDrivingMillis(),
                tripKm = tripKm,
                continuousDrivingMinutes = rest.continuousDrivingMillis() / MINUTE,
                restAfterMinutes = restAskAtMinutes,
                legRemainKm = if (ui.stage == TripStage.ON_TRIP) legRemainKm() else null,
                restDue = rest.isRestDue(),
            )
        }
    }

    // ---- PetaActions (화면에서 호출) ----

    override fun startTrip() {
        PetaStore.update { it.copy(emergencyPlace = null) }
        val plan = ui.plan ?: return
        // 출발 준비(C04)의 추천 경유지는 여행 계획의 첫 쉼터
        PetaStore.update { it.copy(stage = TripStage.BRIEFING, plannedStop = if (it.plannedStopDecided) it.plannedStop else plan.plannedRest) }
        voice.speak("출발 전에 준비물부터 같이 볼게요.")
        Log.i(TAG, "trip accepted for ${plan.pet.name}")
    }

    override fun confirmBriefing() {
        PetaStore.update { it.copy(stage = TripStage.SUMMARY) }
    }

    override fun planStop(place: Place) {
        PetaStore.update { it.copy(plannedStop = stopFor(place), plannedStopDecided = true) }
    }

    override fun planNoStop() {
        PetaStore.update { it.copy(plannedStop = null, plannedStopDecided = true) }
    }

    override fun planRecommendedStop() {
        PetaStore.update { it.copy(plannedStop = it.plan?.plannedRest, plannedStopDecided = true) }
    }

    override fun startGuidance() {
        val plan = ui.plan ?: return
        val stop = ui.plannedStop
        tripDrivingMillis = 0
        tripKm = 0.0
        legStartKm = 0.0
        lastTickApp = appNow
        visitedWaypoint = false
        destinationArrived = false
        destinationDoorOpens = 0
        restAskAtMinutes = restAfterMinutes(plan)
        restAsksLeft = true
        rest = restAdvisorFor(plan).also { it.onParkedChanged(!vehicle().isMoving) }
        if (stop != null) {
            // 쉬었다 갈 곳을 먼저 안내하고, 쉬고 나면 목적지로 다시 안내한다
            legKm = stop.place.routeKm ?: 10.0
            navi.routeTo(stop.place)
        } else {
            legKm = PetaConfig.DEMO_ROUTE_KM
            navi.startTrip(plan)
        }
        PetaStore.update {
            it.copy(
                stage = TripStage.ON_TRIP, phase = TripPhase.DRIVING, restAccepted = stop != null, restPrompt = null,
                waypointVisited = false, routeFailed = false, routeStarted = false, pendingStop = stop, stopQuestion = null,
                routeMinutes = it.routeMinutes ?: plan.estimatedMinutes,
            )
        }
        val via = stop?.let { " ${shortName(it.place)}에서 한 번 쉬어 가요." }.orEmpty()
        voice.speak("${plan.pet.name.withWa()} ${plan.destination.place.name.withRo()} 출발할게요.$via 안전 운전하세요.")
        // 내비로 경로를 보낸 뒤 잠깐 보여주고 페타 화면으로 돌아온다
        lifecycleScope.launch {
            delay(3_000)
            bringToFront()
        }
    }

    override fun retryRoute() {
        val target = ui.pendingStop?.takeIf { !ui.waypointVisited }?.place ?: ui.plan?.destination?.place ?: return
        PetaStore.update { it.copy(routeFailed = false, routeStarted = false) }
        navi.routeTo(target)
    }

    override fun backToSummary() {
        navi.cancelRoute()
        resetTripState(stage = TripStage.SUMMARY)
    }

    override fun startRest() {
        PetaStore.update { it.copy(resting = true, petClimate = "pending") }
        // 차량 「펫 케어 모드」는 SDK에 API가 없어 공조로 실내 온도를 맞춘다
        vehicleSource.setPetCareClimate(PetaConfig.PET_CARE_CELSIUS) { ok, why ->
            if (!ok) Log.w(TAG, "pet care climate failed: $why")
            PetaStore.update { it.copy(petClimate = if (ok) "on" else "failed") }
        }
        voice.speak("${ui.plan?.pet?.name?.withRang().orEmpty()} 푹 쉬다 가요. 머문 시간은 보호자 폰에도 보여드릴게요.")
    }

    override fun addWaypoint(place: Place) {
        navi.addWaypoint(place)
        voice.speak("${shortName(place).withEulReul()} 경유지로 추가했어요.")
    }

    override fun routeTo(place: Place) {
        // 응급 병원으로 안내하는 동안 주행 카드도 병원을 보여 준다 (원래 목적지가 그대로 보이면 내비 경로와 어긋난다)
        PetaStore.update { it.copy(emergencyPlace = place) }
        navi.routeTo(place)
        voice.speak("${place.name.withRo()} 바로 안내할게요.")
    }

    override fun changeDestination(place: Place) {
        val plan = ui.plan ?: return
        val stop = TripStop(
            place = place,
            plannedStayMinutes = plan.destination.plannedStayMinutes,
            activity = "",
            nearbyInfo = place.description,
        )
        PetaStore.update { it.copy(plan = plan.copy(destination = stop)) }
        // 휴게소를 아직 안 들렀으면 내비는 휴게소로 두고, 휴게소를 떠날 때 새 목적지로 안내한다(onDeparted)
        if (ui.pendingStop == null || ui.waypointVisited) {
            legStartKm = tripKm
            legKm = place.routeKm ?: 10.0
            routeAndReturn(place)
        }
        voice.speak("목적지를 ${place.name.withRo()} 바꿨어요.")
    }

    override fun submitReview(stars: Int) {
        val visit = ui.pendingReview ?: return
        lifecycleScope.launch { api.submitReview(visit, stars) }
        reviews.clear()
        PetaStore.update { it.copy(pendingReview = null) }
    }

    override fun dismissReview() {
        reviews.clear()
        PetaStore.update { it.copy(pendingReview = null) }
    }

    override fun refreshNearby() {
        applyNearby()
        syncPlaces()
    }

    private fun applyNearby() {
        val s = ui
        PetaStore.update {
            it.copy(
                nearbyRestPlaces = places.nearbyDogRest(s.location),
                hospitals = places.hospitalsFor(s.location, s.plan?.regularHospital),
                regularHospital = s.plan?.regularHospital?.let(places::withRoute),
            )
        }
    }

    /** 관광공사(쉼터·여행지)와 카카오(동물병원)에서 주변 장소를 받아 온다. 키가 없거나 실패하면 샘플로 동작한다. */
    private fun syncPlaces() {
        if (placesFetching || !(tour.isConfigured || kakao.isConfigured)) return
        val at = ui.location
        val last = placesAt
        val now = System.currentTimeMillis()
        val moved = last == null || distanceKm(last, at) >= PetaConfig.PLACES_REFRESH_KM
        val retry = placesFailed && now - placesAttemptMillis >= PLACES_RETRY_MILLIS
        if (!moved && !retry) return
        placesFetching = true
        if (moved) routeCache.clear()
        placesAt = at
        placesAttemptMillis = now
        lifecycleScope.launch {
            var failed = false
            try {
                if (tour.isConfigured) {
                    try {
                        val all = tour.nearby(at, radiusM = 20_000, limit = 40).map(DemoStats::fill)
                        places.updateNearby(
                            rest = all.filter { it.type == PlaceType.DOG_REST },
                            travel = all.filter { it.type == PlaceType.DESTINATION || it.category == "공원" },
                        )
                        Log.i(TAG, "tour nearby ok: ${all.size} places")
                        loadPetRules(all)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        failed = true
                        Log.w(TAG, "tour nearby failed: ${e.javaClass.simpleName} ${e.message}")
                    }
                }
                if (kakao.isConfigured) {
                    try {
                        val hospitals = kakao.animalHospitals(at)
                        places.updateHospitals(hospitals)
                        Log.i(TAG, "kakao hospitals ok: ${hospitals.size} places")
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        failed = true
                        Log.w(TAG, "kakao hospitals failed: ${e.javaClass.simpleName} ${e.message}")
                    }
                }
            } finally {
                placesFailed = failed
                placesFetching = false
            }
            applyNearby()
            PetaStore.update { it.copy(placesRev = it.placesRev + 1) }
            // 보이는 카드(쉼터·병원)부터 내비로 도로 거리를 다시 잰다. 내비가 답하지 않으면 직선 거리로 둔다.
            // 도로 거리를 재면 순위가 바뀌어 아직 직선 거리인 곳이 위로 올라온다. 보이는 카드가 모두 도로 거리가 될 때까지 다시 잰다.
            val visible = {
                ui.nearbyRestPlaces.take(ESTIMATE_NEARBY).map { it.place } +
                    ui.hospitals.take(ESTIMATE_HOSPITALS).map { it.place } +
                    listOfNotNull(ui.plan?.regularHospital)
            }
            estimateRoutes(visible(), refill = visible)
        }
    }

    /**
     * 내비(NaviHelper requestTrip)로 현재 위치에서 각 장소까지의 도로 거리·시간을 받아 카드에 반영한다.
     * [refill]이 있으면 반영 뒤 다시 보이는 카드 중 아직 안 잰 곳을 이어서 잰다(최대 [ESTIMATE_ROUNDS]번).
     */
    private fun estimateRoutes(candidates: List<Place>, refill: (() -> List<Place>)? = null) {
        if (candidates.isEmpty()) return
        lifecycleScope.launch {
            var batch = candidates
            repeat(ESTIMATE_ROUNDS) {
                var failures = 0
                var changed = false
                for (place in batch) {
                    val estimate = routeCache[place.id]
                        ?: navi.estimate(place)?.also { routeCache[place.id] = it }
                    if (estimate == null) {
                        // 내비가 답하지 않는 환경(에뮬레이터 등)에서는 오래 붙잡지 않는다
                        if (++failures >= 2) break
                        continue
                    }
                    places.applyRoute(place.id, estimate.distanceM / 1000.0, (estimate.etaSec + 59) / 60)
                    changed = true
                }
                if (changed) {
                    applyNearby()
                    PetaStore.update { it.copy(placesRev = it.placesRev + 1) }
                }
                if (failures >= 2 || refill == null) return@launch
                batch = refill().filter { routeCache[it.id] == null }
                if (batch.isEmpty()) return@launch
            }
        }
    }

    override fun searchPlaces(query: String) {
        val q = query.trim()
        if (q.isEmpty() || !tour.isConfigured) return
        val from = ui.location
        lifecycleScope.launch {
            try {
                val hits = tour.search(q)
                    .map(DemoStats::fill)
                    .map { it.copy(routeKm = it.routeKm ?: distanceKm(from, it.point)) }
                    .sortedBy { it.routeKm }
                places.updateSearchHits(q, hits)
                PetaStore.update { it.copy(placesRev = it.placesRev + 1) }
                estimateRoutes(hits.take(ESTIMATE_SEARCH_HITS))
                loadPetRules(hits)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "tour search failed: ${e.javaClass.simpleName} ${e.message}")
            }
        }
    }

    /**
     * 관광공사 반려동물 동반 조건(detailPetTour2)을 받아 둔다. 반려견 적합도 필터(PetFit)와 상세 팝업이 쓴다.
     * 장소마다 한 번만, 한 번에 [PET_RULE_PARALLEL]개씩 묻는다. 실패한 곳은 조건 미확인으로 남는다(거르지 않는다).
     */
    private fun loadPetRules(list: List<Place>) {
        if (!tour.isConfigured) return
        val todo = list.map { it.id }.filter { it.startsWith("tour-") && !places.hasPetRule(it) }.distinct().take(PET_RULE_LIMIT)
        if (todo.isEmpty()) return
        lifecycleScope.launch {
            var loaded = 0
            for (chunk in todo.chunked(PET_RULE_PARALLEL)) {
                chunk.map { id ->
                    async {
                        runCatching { tour.petRule(id) }
                            .onSuccess { places.putPetRule(id, it); loaded++ }
                            .onFailure { if (it is CancellationException) throw it }
                    }
                }.awaitAll()
            }
            Log.i(TAG, "pet rules loaded: $loaded / ${todo.size}")
            if (loaded > 0) PetaStore.update { it.copy(placesRev = it.placesRev + 1) }
        }
    }

    override fun widenNearby(radiusKm: Int) {
        val from = ui.location
        if (!tour.isConfigured) { places.updateWideNearby(radiusKm, emptyList()); return }
        places.setWideLoading(radiusKm)
        PetaStore.update { it.copy(placesRev = it.placesRev + 1) }
        lifecycleScope.launch {
            try {
                val list = tour.nearby(from, radiusM = radiusKm * 1000, limit = WIDE_NEARBY_LIMIT).map(DemoStats::fill)
                Log.i(TAG, "tour wide nearby ${radiusKm}km: ${list.size} places")
                places.updateWideNearby(radiusKm, list)
                estimateRoutes(list.take(ESTIMATE_SEARCH_HITS))
                loadPetRules(list)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "tour wide nearby failed: ${e.javaClass.simpleName} ${e.message}")
                places.setWideFailed(radiusKm)
            }
            PetaStore.update { it.copy(placesRev = it.placesRev + 1) }
        }
    }

    override fun widenSearch(keyword: String) {
        val key = keyword.trim().ifEmpty { "반려견" }
        val from = ui.location
        lifecycleScope.launch {
            val hits = if (!tour.isConfigured) emptyList() else try {
                tour.search(key, limit = WIDEN_LIMIT)
                    .map(DemoStats::fill)
                    .map { it.copy(routeKm = it.routeKm ?: distanceKm(from, it.point)) }
                    .sortedBy { it.routeKm }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "tour widen failed: ${e.javaClass.simpleName} ${e.message}")
                emptyList()
            }
            places.updateWidened(key, hits)
            PetaStore.update { it.copy(placesRev = it.placesRev + 1) }
            estimateRoutes(hits.take(ESTIMATE_SEARCH_HITS))
            loadPetRules(hits)
        }
    }

    override fun retrySync() {
        lifecycleScope.launch { syncTripPlan() }
    }

    override fun useSampleTrip() {
        if (ui.plan != null) return
        val plan = SampleTrip.plan
        rest = restAdvisorFor(plan)
        PetaStore.update { it.copy(plan = plan, planFromPhone = false, restAfterMinutes = restAfterMinutes(plan)) }
        refreshNearby()
    }

    override fun listenQuery(onResult: (String?) -> Unit) {
        voice.listenText(PetaConfig.VOICE_ANSWER_TIMEOUT_MILLIS, onResult)
    }

    override fun setVoiceEnabled(on: Boolean) {
        voice.enabled = on
        PetaStore.update { it.copy(voiceEnabled = on) }
    }

    override fun resetApp() {
        navi.cancelRoute()
        resetTripState()
        reviews.clear()
        PetaStore.update { it.copy(plan = null, planFromPhone = false, syncedAt = null, pendingReview = null) }
        retrySync()
    }

    override fun resetTrip() {
        resetTripState()
    }

    private fun resetTripState(stage: TripStage = TripStage.WELCOME) {
        tripDrivingMillis = 0
        tripKm = 0.0
        legStartKm = 0.0
        legKm = PetaConfig.DEMO_ROUTE_KM
        visitedWaypoint = false
        destinationArrived = false
        destinationDoorOpens = 0
        PetaStore.update {
            it.copy(
                stage = stage, phase = TripPhase.READY, currentStop = null, resting = false,
                restPrompt = null, restAccepted = false, waypointVisited = false, routeFailed = false, routeStarted = false,
                pendingStop = null, stopQuestion = null,
                plannedStop = if (stage == TripStage.WELCOME) null else it.plannedStop,
                plannedStopDecided = if (stage == TripStage.WELCOME) false else it.plannedStopDecided,
            )
        }
    }

    override fun simulateWaypointArrival() {
        val stop = ui.pendingStop ?: ui.plan?.plannedRest ?: return
        onNavi(NaviEvent.WaypointArrived(0, stop.place.name))
    }

    override fun simulateDestinationArrival() {
        val dest = ui.plan?.destination ?: return
        onNavi(NaviEvent.DestinationArrived(dest.place.name))
    }

    override fun simulateLongDrive() {
        rest.addDriving(restAskAtMinutes * MINUTE)
        tripDrivingMillis += restAskAtMinutes * MINUTE
        publish(vehicle())
    }

    // ---- 플로우 점검 페이지(/admin) 명령: 화면 바로 가기 · 기어 · 이벤트 ----

    /** 점검 페이지가 정한 기어·속도를 실제 값 위에 덮는다 */
    private fun effective(v: VehicleState): VehicleState {
        val o = vehicleOverride ?: return v
        if (v.gear != overrideRealGear) {
            // Virtual Console로 기어를 바꾸면 그 값을 따른다
            vehicleOverride = null
            return v
        }
        return v.copy(gear = o.gear, speedKph = o.speedKph)
    }

    private fun vehicle(): VehicleState = effective(vehicleSource.state.value)

    /**
     * @param transition false면 정차/출발 전환(도착 처리, 다시 출발 등)을 일으키지 않고 기어만 바꾼다. 화면 바로 가기용.
     */
    private fun overrideVehicle(gear: Gear?, speedKph: Float, transition: Boolean) {
        val real = vehicleSource.state.value
        vehicleOverride = gear?.let { VehicleState(gear = it, speedKph = speedKph) }
        overrideRealGear = real.gear
        val v = vehicle()
        if (!transition && v.gearKnown) wasParked = v.isParked
        onVehicle(v)
    }

    private fun debugStatus(): JSONObject {
        val s = ui
        val v = vehicle()
        return JSONObject()
            .put("screen", DebugBridge.screen)
            .put("stage", s.stage.name)
            .put("phase", s.phase.name)
            .put("gear", v.gear.name)
            .put("realGear", vehicleSource.state.value.gear.name)
            .put("gearOverride", vehicleOverride != null)
            .put("speedKph", v.speedKph.toDouble())
            .put("plan", s.plan?.title ?: JSONObject.NULL)
            .put("planFromPhone", s.planFromPhone)
            .put("pendingStop", s.pendingStop?.place?.name ?: JSONObject.NULL)
            .put("currentStop", s.currentStop?.place?.name ?: JSONObject.NULL)
            .put("tripKm", s.tripKm)
            .put("legRemainKm", s.legRemainKm ?: JSONObject.NULL)
            .put("continuousDrivingMinutes", s.continuousDrivingMinutes)
            .put("restAfterMinutes", s.restAfterMinutes)
            .put("syncHeld", debugHoldSync)
    }

    private fun applyDebug(cmd: DebugBridge.Command) {
        Log.i(TAG, "debug command ${cmd.action}")
        val jumped = runCatching { if (cmd.action.startsWith("C")) debugJump(cmd.action) else debugEvent(cmd.action) }
            .onFailure { Log.w(TAG, "debug command failed: ${cmd.action}", it) }
            .getOrDefault(false)
        DebugBridge.markApplied(cmd)
        if (jumped) bringToFront()
    }

    /** 기어·주행 이벤트. 실제 흐름(도착 처리, 휴식 제안 등)을 그대로 탄다. */
    private fun debugEvent(action: String): Boolean {
        when (action) {
            "gear:park" -> overrideVehicle(Gear.PARK, 0f, transition = true)
            "gear:hold" -> overrideVehicle(Gear.DRIVE, 0f, transition = true)
            "gear:drive" -> overrideVehicle(Gear.DRIVE, DEBUG_SPEED_KPH, transition = true)
            "gear:real" -> overrideVehicle(null, 0f, transition = true)
            "event:longDrive" -> simulateLongDrive()
            "event:waypoint" -> simulateWaypointArrival()
            "event:destination" -> simulateDestinationArrival()
            "event:askStop" -> askStopChange()
            "event:reset" -> {
                debugHoldSync = false
                navi.cancelRoute()
                resetTripState()
                overrideVehicle(Gear.PARK, 0f, transition = false)
            }
            "event:resetApp" -> {
                debugHoldSync = false
                overrideVehicle(Gear.PARK, 0f, transition = false)
                resetApp()
            }
            else -> Log.w(TAG, "unknown debug event $action")
        }
        return action.startsWith("event:")
    }

    /** 화면 바로 가기: 그 화면이 뜨는 상태를 만든다. 음성·내비 호출 없이 정지 화면을 보여 준다. */
    private fun debugJump(code: String): Boolean {
        val plan = ui.plan ?: SampleTrip.plan.also { p ->
            rest = restAdvisorFor(p)
            PetaStore.update { it.copy(plan = p, planFromPhone = false, restAfterMinutes = restAfterMinutes(p)) }
        }
        val stop = plan.plannedRest ?: SampleTrip.plan.plannedRest
        // C01(폰 여행 없음)만 폰 여행 조회를 멈춘다. 나머지는 폰 여행이 있으면 그대로 쓴다.
        debugHoldSync = code == "C01"
        fun park() = overrideVehicle(Gear.PARK, 0f, transition = false)
        fun hold() = overrideVehicle(Gear.DRIVE, 0f, transition = false)

        when (code) {
            "C00", "C11", "C12", "C13E", "C14" -> park() // 화면 안 값은 MainActivity가 맞춘다
            "C01" -> {
                park(); resetTripState()
                val sample = SampleTrip.plan
                rest = restAdvisorFor(sample)
                PetaStore.update {
                    it.copy(plan = sample, planFromPhone = false, pendingReview = null, restAfterMinutes = restAfterMinutes(sample))
                }
            }
            "C02" -> {
                park(); resetTripState()
                PetaStore.update { it.copy(pendingReview = null) }
            }
            "C03" -> {
                park(); resetTripState(TripStage.BRIEFING)
                PetaStore.update { it.copy(plannedStop = stop, plannedStopDecided = false, pendingReview = null) }
            }
            "C04" -> {
                park(); resetTripState(TripStage.SUMMARY)
                PetaStore.update { it.copy(plannedStop = stop, plannedStopDecided = false, pendingReview = null) }
            }
            "C04E" -> {
                park(); debugEnterTrip(plan, stop)
                PetaStore.update { it.copy(routeFailed = true) }
            }
            "C01R" -> { park(); debugEnterTrip(plan, stop) }
            "C05" -> { hold(); debugEnterTrip(plan, stop) }
            "C05V" -> {
                hold(); debugEnterTrip(plan, stop)
                if (stop != null) PetaStore.update { it.copy(stopQuestion = StopQuestion(stop)) }
            }
            "C06", "C06E" -> {
                hold(); debugEnterTrip(plan, null)
                restAsksLeft = false // 정지 화면을 보는 동안 실제 휴식 제안이 다시 뜨지 않게
                val candidate = restCandidate() ?: stop ?: return true
                rest.addDriving(restAskAtMinutes * MINUTE)
                PetaStore.update {
                    it.copy(
                        restPrompt = RestPrompt(
                            candidate, candidate.place.routeKm ?: 5.0, etaMinutes(candidate.place), restAskAtMinutes,
                            retrying = code == "C06E",
                        ),
                    )
                }
            }
            "C07", "C08" -> {
                park(); debugEnterTrip(plan, stop)
                visitedWaypoint = true
                PetaStore.update {
                    it.copy(phase = TripPhase.AT_WAYPOINT, currentStop = stop, resting = code == "C08")
                }
            }
            "C09" -> {
                park(); debugEnterTrip(plan, null)
                destinationArrived = true
                PetaStore.update {
                    it.copy(phase = TripPhase.AT_DESTINATION, currentStop = plan.destination, waypointVisited = true)
                }
            }
            "C10", "C10S" -> {
                park(); resetTripState()
                PetaStore.update { it.copy(pendingReview = Visit(plan.id, plan.destination.place.name, now)) }
            }
            else -> {
                Log.w(TAG, "unknown debug screen $code")
                return false
            }
        }
        if (code == "C11") refreshNearby()
        return true
    }

    /** [startGuidance]와 같은 상태로 여행을 시작하되 내비·음성은 부르지 않는다 */
    private fun debugEnterTrip(plan: TripPlan, stop: TripStop?) {
        resetTripState()
        tripDrivingMillis = 0
        tripKm = 0.0
        legStartKm = 0.0
        legKm = stop?.place?.routeKm ?: PetaConfig.DEMO_ROUTE_KM
        lastTickApp = appNow
        restAskAtMinutes = restAfterMinutes(plan)
        restAsksLeft = true
        rest = restAdvisorFor(plan).also { it.onParkedChanged(!vehicle().isMoving) }
        PetaStore.update {
            it.copy(
                stage = TripStage.ON_TRIP, phase = TripPhase.DRIVING, restAccepted = stop != null,
                plannedStop = stop, plannedStopDecided = true, pendingStop = stop, pendingReview = null,
                routeMinutes = it.routeMinutes ?: plan.estimatedMinutes,
            )
        }
    }

    private fun restAfterMinutes(plan: TripPlan?): Long {
        val pet = plan?.petRestIntervalMinutes?.toLong() ?: return PetaConfig.DRIVER_REST_AFTER_MINUTES
        return minOf(PetaConfig.DRIVER_REST_AFTER_MINUTES, pet)
    }

    private fun restAdvisorFor(plan: TripPlan?) =
        RestAdvisor(restAfterMinutes(plan) * MINUTE, PetaConfig.REST_RESET_PARKED_MINUTES * MINUTE, PetaConfig::now)

    companion object {
        private const val TAG = "PetaService"
        private const val MINUTE = 60_000L
        private const val PLACES_RETRY_MILLIS = 30_000L
        private const val ESTIMATE_NEARBY = 5
        private const val ESTIMATE_HOSPITALS = 3
        private const val ESTIMATE_SEARCH_HITS = 4
        private const val ESTIMATE_ROUNDS = 15
        private const val WIDE_NEARBY_LIMIT = 100
        private const val PET_RULE_LIMIT = 60
        private const val PET_RULE_PARALLEL = 6
        private const val WIDEN_LIMIT = 100
        /** 점검 페이지 [주행]의 속도 */
        private const val DEBUG_SPEED_KPH = 80f

        /** "가평휴게소 · 반려견 쉼터" → "가평휴게소" */
        fun shortName(place: Place): String = place.name.substringBefore(" · ")

        /** 경로상 예상 시간(분). 서버 값이 없으면 평균 60km/h로 어림한다. */
        fun etaMinutes(place: Place): Int = place.routeMinutes ?: ((place.routeKm ?: 5.0) / 60.0 * 60).toInt().coerceAtLeast(1)

        fun start(context: Context) {
            context.startForegroundService(Intent(context, PetaService::class.java))
        }
    }
}
