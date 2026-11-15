package com.peta.car.rest

/**
 * 연속 운전 시간 추적 → "쉬어갈 시간이에요" 판단.
 * 기준 = min(운전자 한도, 반려견 휴식 주기). 충분히 정차하면 초기화.
 * 기어(P/D) 변화만으로 계산하므로 에뮬레이터에서도 재현 가능하다.
 */
class RestAdvisor(
    private val restAfterMillis: Long,
    private val resetAfterParkedMillis: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var accumulated = 0L
    private var drivingSince: Long? = null
    private var parkedSince: Long? = null

    fun onParkedChanged(parked: Boolean) {
        val now = clock()
        if (parked) {
            drivingSince?.let { accumulated += now - it }
            drivingSince = null
            if (parkedSince == null) parkedSince = now
        } else {
            parkedSince?.let { if (now - it >= resetAfterParkedMillis) accumulated = 0 }
            parkedSince = null
            if (drivingSince == null) drivingSince = now
        }
    }

    fun continuousDrivingMillis(): Long {
        val rested = parkedSince?.let { clock() - it >= resetAfterParkedMillis } ?: false
        if (rested) return 0
        return accumulated + (drivingSince?.let { clock() - it } ?: 0)
    }

    fun isRestDue(): Boolean = continuousDrivingMillis() >= restAfterMillis

    /** 데모용: 운전 시간을 강제로 더한다. */
    fun addDriving(millis: Long) {
        accumulated += millis
    }
}
