package com.peta.car.stay

import com.peta.car.vehicle.VehicleState

sealed interface StayEvent {
    /** 기어 P 진입. 서버에 기록해 두면 차를 두고 나가도 모바일 알림을 보낼 수 있다. */
    data class Parked(val at: Long) : StayEvent

    /** 정차가 기준 시간을 넘김 → 차량 화면/알림 "N분째 머물고 있어요" */
    data class LongStay(val minutes: Long) : StayEvent

    /** 정차 중 운전석 도어 열림 → 차에서 내림. 이후 알림은 서버가 모바일로 보낸다. */
    data class LeftVehicle(val parkedAt: Long) : StayEvent

    /** P 해제(출발). 서버의 예약 알림을 취소한다. */
    data class Departed(val stayedMinutes: Long) : StayEvent
}

/**
 * 차량 상태 변화를 받아 "얼마나 머물렀는지" 이벤트로 바꾸는 순수 로직.
 * Android 의존성이 없어서 단위 테스트로 검증한다.
 */
class StayTracker(
    private val thresholdMillis: Long,
    private val repeatMillis: Long = thresholdMillis,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    var parkedAt: Long? = null
        private set

    private var lastAlertAt: Long? = null
    private var doorOpen = false
    private var leftReported = false

    fun elapsedMinutes(): Long = parkedAt?.let { (clock() - it) / 60_000 } ?: 0

    fun onVehicleState(state: VehicleState): List<StayEvent> {
        if (!state.gearKnown) return emptyList()
        val events = mutableListOf<StayEvent>()
        val now = clock()
        val start = parkedAt

        if (state.isParked && start == null) {
            parkedAt = now
            lastAlertAt = null
            leftReported = false
            events += StayEvent.Parked(now)
        } else if (!state.isParked && start != null) {
            events += StayEvent.Departed((now - start) / 60_000)
            parkedAt = null
            lastAlertAt = null
        }

        val opened = !doorOpen && state.driverDoorOpen
        doorOpen = state.driverDoorOpen
        val parkedSince = parkedAt
        if (opened && parkedSince != null && !leftReported) {
            leftReported = true
            events += StayEvent.LeftVehicle(parkedSince)
        }
        return events
    }

    /** 주기적으로 호출. 기준 시간을 넘겼고 직전 알림 이후 repeatMillis가 지났으면 LongStay. */
    fun tick(): StayEvent? {
        val start = parkedAt ?: return null
        val now = clock()
        if (now - start < thresholdMillis) return null
        val last = lastAlertAt
        if (last != null && now - last < repeatMillis) return null
        lastAlertAt = now
        return StayEvent.LongStay((now - start) / 60_000)
    }
}
