package com.peta.car.vehicle

import kotlinx.coroutines.flow.StateFlow

enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

data class VehicleState(
    val gear: Gear = Gear.UNKNOWN,
    val driverDoorOpen: Boolean = false,
    /** 플랫폼이 주행 규제(CarUxRestrictions)를 걸었는지 */
    val drivingRestricted: Boolean = false,
    /** Pleos Odometer 트립 주행 시간(분). 차량이 지원하지 않으면 null (에뮬레이터 미지원) */
    val tripDrivingTime: Int? = null,
    /** Pleos EvBattery 배터리 잔량(%). 전기차가 아니거나 미지원이면 null */
    val evSoc: Int? = null,
    /** 차량 속도(km/h). 클러스터와 같은 PERF_VEHICLE_SPEED */
    val speedKph: Float = 0f,
) {
    /** 실제로 달리고 있는지 (기어 D + 속도) */
    val isMoving: Boolean get() = isDriving && speedKph > 1f
    val isParked: Boolean get() = gear == Gear.PARK || gear == Gear.UNKNOWN
    val isDriving: Boolean get() = gear == Gear.DRIVE || gear == Gear.REVERSE || gear == Gear.NEUTRAL
    val gearKnown: Boolean get() = gear != Gear.UNKNOWN
}

interface VehicleStateSource {
    val state: StateFlow<VehicleState>
    fun start()
    fun stop()

    /**
     * C07 [휴식 시작] 펫 케어 공조: 공조 전원 → 오토 → 온도. 차량 설정의 「펫 케어 모드」 자체는 공개 SDK에 API가 없어
     * 공조(HVAC)로 실내 온도만 맞춘다. 결과는 [done](성공 여부, 실패 이유).
     */
    fun setPetCareClimate(celsius: Float, done: (Boolean, String?) -> Unit) = done(false, "지원하지 않는 차량")
}
