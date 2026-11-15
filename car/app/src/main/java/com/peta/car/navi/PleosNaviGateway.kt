package com.peta.car.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import com.peta.car.place.GeoPoint
import com.peta.car.place.Place
import com.peta.car.trip.TripPlan
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class PleosNaviGateway(context: Context) : NaviGateway {

    private val naviHelper = NaviHelper(context.applicationContext)

    private val _events = MutableSharedFlow<NaviEvent>(extraBufferCapacity = 32)
    override val events: SharedFlow<NaviEvent> = _events.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** 경유지를 추가할 때 다음 빈 슬롯 (NaviHelper는 최대 2개) */
    private var waypointCount = 0

    /** requestTrip 응답(onTripInfo)에는 요청 식별자가 없다. 한 번에 하나만 보내고 그 응답을 기다린다. */
    private val estimateLock = Mutex()
    @Volatile private var pendingEstimate: CompletableDeferred<RouteEstimate>? = null

    private val listener = object : NaviHelperEventListener {
        override fun onTripInfo(tripInfo: TripInfo) {
            Log.i(TAG, "tripInfo distance=${tripInfo.distance}m eta=${tripInfo.eta}s")
            pendingEstimate?.complete(RouteEstimate(tripInfo.distance, tripInfo.eta))
        }

        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            emit(NaviEvent.RouteStarted(routeStartInfo.duration, routeStartInfo.distance, routeStartInfo.name.orEmpty()))
        }

        override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) {
            val index = if (waypointArrivedInfo.index == WaypointIndex.SECOND) 1 else 0
            emit(NaviEvent.WaypointArrived(index, waypointArrivedInfo.name.orEmpty()))
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            emit(NaviEvent.DestinationArrived(destinationArrivedInfo.name.orEmpty()))
        }

        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            emit(
                NaviEvent.Location(
                    GeoPoint(currentLocationInfo.latitude, currentLocationInfo.longitude),
                    currentLocationInfo.address.orEmpty(),
                )
            )
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            emit(NaviEvent.DestinationRemain(destinationInfo.remainTime, destinationInfo.remainDistance))
        }

        override fun onError(errorCode: NaviErrorCode) {
            emit(NaviEvent.Error(errorCode.name))
        }
    }

    override fun start() {
        naviHelper.initialize(
            {
                _connected.value = true
                naviHelper.addListener(listener)
                refreshLocation()
                refreshDestination()
            },
            { t: Throwable ->
                _connected.value = false
                Log.w(TAG, "NaviHelper 연결 실패: ${t.message}", t)
            },
        )
    }

    override fun stop() {
        runCatching { naviHelper.removeListener(listener) }
        naviHelper.release()
        _connected.value = false
    }

    /** 처음에는 목적지만 안내한다. 쉼터는 휴식 제안(C05)에서 "응"이라고 답할 때 경유지로 넣는다. */
    override fun startTrip(plan: TripPlan) = whenConnected {
        waypointCount = 0
        naviHelper.requestRoute(plan.destination.place.toRouteInfo())
    }

    override fun addWaypoint(place: Place) = whenConnected {
        // 슬롯이 다 찼으면 가장 가까운(첫 번째) 경유지를 교체
        val index = if (waypointCount == 0) WaypointIndex.FIRST else if (waypointCount == 1) WaypointIndex.SECOND else WaypointIndex.FIRST
        naviHelper.addWaypoint(place.toWaypoint(index))
        waypointCount = minOf(waypointCount + 1, 2)
    }

    override fun removeWaypoints() = whenConnected {
        naviHelper.removeWaypoint(WaypointIndex.ALL)
        waypointCount = 0
    }

    override fun routeTo(place: Place) = whenConnected {
        waypointCount = 0
        naviHelper.requestRoute(place.toRouteInfo())
    }

    override fun cancelRoute() = whenConnected {
        waypointCount = 0
        naviHelper.cancelRoute()
    }

    override suspend fun estimate(place: Place): RouteEstimate? {
        if (!_connected.value) return null
        return estimateLock.withLock {
            val answer = CompletableDeferred<RouteEstimate>()
            pendingEstimate = answer
            try {
                naviHelper.requestTrip(TripRequest(place.point.lat, place.point.lng, place.id, place.name, "0"))
                withTimeoutOrNull(ESTIMATE_TIMEOUT_MILLIS) { answer.await() }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "경로 추정 요청 실패", e)
                null
            } finally {
                pendingEstimate = null
            }
        }
    }

    override fun refreshLocation() = whenConnected { naviHelper.getCurrentLocationInfo() }

    override fun refreshDestination() = whenConnected { naviHelper.getDestinationInfo() }

    private inline fun whenConnected(block: () -> Unit) {
        if (!_connected.value) {
            Log.w(TAG, "NaviHelper 미연결 상태 — 요청 무시")
            return
        }
        runCatching(block).onFailure { Log.w(TAG, "NaviHelper 요청 실패", it) }
    }

    private fun emit(event: NaviEvent) {
        Log.i(TAG, "event: $event")
        _events.tryEmit(event)
    }

    private fun Place.toRouteInfo() = RouteInfo(
        point.lat, point.lng,
        null, null,                 // displayLatitude, displayLongitude
        name, id, address, "0",     // poiName, poiId, address, poiSubId
        RouteOption.RECOMMENDED,
        "",                         // poiProvider (SDK 기본값)
        false,                      // isMobileRequest
    )

    private fun Place.toWaypoint(index: WaypointIndex) = RequestWaypointInfo(
        point.lat, point.lng,
        name, index,
        id, address, "0",           // poiId, address, poiSubId
        "",                         // poiProvider (SDK 기본값)
    )

    private companion object {
        const val TAG = "PleosNavi"
        const val ESTIMATE_TIMEOUT_MILLIS = 5_000L
    }
}
