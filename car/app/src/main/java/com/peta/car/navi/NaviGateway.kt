package com.peta.car.navi

import com.peta.car.place.GeoPoint
import com.peta.car.place.Place
import com.peta.car.trip.TripPlan
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface NaviEvent {
    data class RouteStarted(val durationSec: Int, val distanceM: Int, val name: String) : NaviEvent
    /** index: 0 = 첫 번째 경유지, 1 = 두 번째 경유지 */
    data class WaypointArrived(val index: Int, val name: String) : NaviEvent
    data class DestinationArrived(val name: String) : NaviEvent
    data class Location(val point: GeoPoint, val address: String) : NaviEvent
    data class DestinationRemain(val remainSec: Int, val remainM: Int) : NaviEvent
    data class Error(val message: String) : NaviEvent
}

/** 안내를 시작하지 않고 계산만 한 도로 거리(m)·소요 시간(초) */
data class RouteEstimate(val distanceM: Int, val etaSec: Int)

/** 내비 연동 지점. 실제 구현은 Pleos NaviHelper SDK([PleosNaviGateway]). */
interface NaviGateway {
    /** 진행 중인 경로 안내를 끝낸다 */
    fun cancelRoute()
    val events: SharedFlow<NaviEvent>
    val connected: StateFlow<Boolean>

    fun start()
    fun stop()

    /** 여행 계획 그대로 목적지 + 경유지 경로 설정 */
    fun startTrip(plan: TripPlan)
    fun addWaypoint(place: Place)
    fun removeWaypoints()
    /** 목적지를 바꿔서 바로 안내 (위급 시 병원 등) */
    fun routeTo(place: Place)
    /** 현재 위치에서 이 장소까지의 도로 거리·시간. 내비가 답하지 않으면 null (호출자는 직선 거리로 둔다) */
    suspend fun estimate(place: Place): RouteEstimate?
    fun refreshLocation()
    fun refreshDestination()
}
