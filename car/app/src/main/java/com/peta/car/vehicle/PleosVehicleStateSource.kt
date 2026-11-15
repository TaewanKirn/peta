package com.peta.car.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.DrivingTimeListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 차량 상태 소스.
 * - Pleos Vehicle SDK: Gear(기어), Door(운전석 도어), Odometer(트립 주행 시간), EvBattery(배터리 잔량)
 * - AAOS CarUxRestrictionsManager: 주행 규제 상태 (Pleos 주행 규제 개발 가이드 방식)
 * - AAOS PERF_VEHICLE_SPEED: 차량 속도 (Pleos FAQ: 클러스터 속도와 같은 값, Vehicle SDK에 속도 API가 없어 AAOS에 align)
 *
 * 에뮬레이터 Virtual Console의 Gear Selection / Door Position 카드로 값을 바꿀 수 있다.
 */
class PleosVehicleStateSource(context: Context) : VehicleStateSource {

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(VehicleState())
    override val state: StateFlow<VehicleState> = _state.asStateFlow()

    private var vehicle: Vehicle? = null
    private var car: Car? = null
    private var uxManager: CarUxRestrictionsManager? = null
    private var propertyManager: CarPropertyManager? = null

    private val speedCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val mps = (value.value as? Float) ?: return
            _state.update { it.copy(speedKph = mps * 3.6f) }
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            Log.w(TAG, "speed property error $propertyId")
        }
    }
    private val driverDoor = DoorArea(DoorValue.ROW_1_LEFT)

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            _state.update { it.copy(gear = toGear(gear)) }
        }

        override fun onFailed(e: Exception) = logFailure("gear", e)
    }

    private val doorListener = object : DoorPositionListener {
        override fun onPositionUpdated(pos: Int?) {
            // 0 = 닫힘, 0보다 크면 열림 (checkDoorPositionCapabilities의 범위 기준)
            _state.update { it.copy(driverDoorOpen = (pos ?: 0) > 0) }
        }

        override fun onFailed(e: Exception) = logFailure("door", e)
    }

    private val drivingTimeListener = object : DrivingTimeListener {
        override fun onDrivingTimeUpdated(time: Int?) {
            _state.update { it.copy(tripDrivingTime = time) }
        }

        override fun onFailed(e: Exception) = logFailure("tripDrivingTime", e)
    }

    private val socListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(soc: Int?) {
            _state.update { it.copy(evSoc = soc) }
        }

        override fun onFailed(e: Exception) = logFailure("evSoc", e)
    }

    private val uxListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { r ->
        _state.update { it.copy(drivingRestricted = r.isRequiresDistractionOptimization) }
    }

    override fun start() {
        val v = Vehicle(appContext).also { vehicle = it }
        v.initialize()

        // 도메인마다 권한/지원 여부가 달라서, 하나가 실패해도 나머지는 동작하게 따로 등록한다
        safely("gear") {
            v.getGear().apply {
                getCurrentGearSelectionState({ gear -> gearListener.onSelected(gear) }, { e -> logFailure("gear get", e) })
                registerGearSelection(gearListener)
            }
        }
        safely("door") {
            v.getDoor().apply {
                getCurrentDoorPosition(driverDoor, { pos -> doorListener.onPositionUpdated(pos) }, { e -> logFailure("door get", e) })
                registerDoorPosition(driverDoor, doorListener)
            }
        }
        safely("tripDrivingTime") { v.getOdometer().registerTripCurrentDrivingTime(drivingTimeListener) }
        safely("evSoc") {
            v.getEvBattery().apply {
                getEvBatteryStateOfCharge({ soc -> socListener.onEvBatteryStateOfChargeUpdated(soc) }, { e -> logFailure("evSoc get", e) })
                registerEvBatteryStateOfCharge(socListener)
            }
        }

        startUxRestrictions()
    }

    override fun stop() {
        vehicle?.let { v ->
            runCatching {
                v.getGear().unregisterGearSelection(gearListener)
                v.getDoor().unregisterDoorPosition(driverDoor, doorListener)
                v.getOdometer().unregisterTripCurrentDrivingTime(drivingTimeListener)
                v.getEvBattery().unregisterEvBatteryStateOfCharge(socListener)
            }
            v.release()
        }
        vehicle = null
        uxManager?.unregisterListener()
        runCatching { propertyManager?.unregisterCallback(speedCallback) }
        propertyManager = null
        car?.disconnect()
        car = null
        uxManager = null
    }

    private fun startUxRestrictions() {
        val c = Car.createCar(appContext) ?: run {
            Log.e(TAG, "Car service unavailable")
            return
        }
        car = c
        uxManager = (c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager)?.also { ux ->
            ux.registerListener(uxListener)
            ux.currentCarUxRestrictions?.let { uxListener.onUxRestrictionsChanged(it) }
        }
        safely("speed") {
            propertyManager = (c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager)?.also { pm ->
                pm.registerCallback(speedCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, 2f) // 2Hz
            }
        }
    }

    private inline fun safely(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            logFailure(what, e)
        }
    }

    override fun setPetCareClimate(celsius: Float, done: (Boolean, String?) -> Unit) {
        val hvac = runCatching { vehicle?.getHVAC() }.getOrNull() ?: return done(false, "차량 연결 전")
        // 속성마다 받는 구역(area)이 다르다. 에뮬레이터는 HVAC_POWER_ON에 운전석(0x1)을 받지 않았다(2026-11-13).
        // 흔한 조합을 차례로 시도해 처음 성공한 구역을 쓴다.
        val areas = listOf(
            SeatArea(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_CENTER, SeatValue.ROW_2_RIGHT),
            SeatArea(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_RIGHT),
            SeatArea(SeatValue.ROW_1_LEFT, SeatValue.ROW_2_LEFT),
            SeatArea(SeatValue.ROW_1_LEFT),
        )
        // optional = 이 차가 지원하지 않으면 건너뛰고 다음 단계로 (에뮬레이터는 HVAC_AUTO_ON을 어떤 구역으로도 받지 않았다)
        fun attempt(what: String, i: Int, call: (SeatArea, () -> Unit, (Exception) -> Unit) -> Unit, optional: Boolean = false, next: () -> Unit) {
            if (i >= areas.size) {
                if (optional) { Log.i(TAG, "hvac $what not supported, skipped"); return next() }
                return done(false, "$what: 지원하는 구역이 없어요")
            }
            runCatching {
                call(areas[i], {
                    Log.i(TAG, "hvac $what ok (area #$i)")
                    next()
                }, { e ->
                    logFailure("hvac $what area #$i", e)
                    attempt(what, i + 1, call, optional, next)
                })
            }.onFailure { attempt(what, i + 1, call, optional, next) }
        }
        attempt("power", 0, { a, ok, fail -> hvac.setHvacPower(a, true, { _, _ -> ok() }, fail) }) {
            attempt("auto", 0, { a, ok, fail -> hvac.setHvacAuto(a, true, { _, _ -> ok() }, fail) }, optional = true) {
                attempt("ac", 0, { a, ok, fail -> hvac.setHvacAc(a, true, { _, _ -> ok() }, fail) }, optional = true) {
                    attempt("temperature", 0, { a, ok, fail ->
                        hvac.setHvacTemperature(a, celsius, TemperatureUnit.CELSIUS, { _, _ -> ok() }, fail)
                    }) {
                        Log.i(TAG, "hvac pet care on: ${celsius}C")
                        done(true, null)
                    }
                }
            }
        }
    }

    private fun logFailure(what: String, e: Exception) {
        Log.w(TAG, "Vehicle SDK $what failed: ${e.message}", e)
    }

    companion object {
        private const val TAG = "PleosVehicle"

        fun toGear(gear: VehicleGear?): Gear = when (gear) {
            VehicleGear.GEAR_PARK -> Gear.PARK
            VehicleGear.GEAR_REVERSE -> Gear.REVERSE
            VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
            null, VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
            else -> Gear.DRIVE // GEAR_DRIVE, GEAR_1~9
        }
    }
}
