package com.peta.car.rest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestAdvisorTest {

    private var now = 0L
    private val minute = 60_000L
    private val advisor = RestAdvisor(restAfterMillis = 60 * minute, resetAfterParkedMillis = 15 * minute, clock = { now })

    @Test
    fun dueAfterContinuousDriving() {
        advisor.onParkedChanged(false)
        now = 59 * minute
        assertFalse(advisor.isRestDue())
        now = 60 * minute
        assertTrue(advisor.isRestDue())
    }

    @Test
    fun shortStopKeepsAccumulatedTime() {
        advisor.onParkedChanged(false)
        now = 40 * minute
        advisor.onParkedChanged(true)   // 신호 대기 등 짧은 정차
        now = 45 * minute
        advisor.onParkedChanged(false)
        now = 65 * minute
        assertEquals(60 * minute, advisor.continuousDrivingMillis())
        assertTrue(advisor.isRestDue())
    }

    @Test
    fun longStopResetsAndHidesRestCard() {
        advisor.onParkedChanged(false)
        now = 70 * minute
        advisor.onParkedChanged(true)
        assertTrue(advisor.isRestDue())  // 정차 직후: 휴식 카드 표시
        now = 85 * minute
        assertFalse(advisor.isRestDue()) // 15분 쉬었으면 숨김
        advisor.onParkedChanged(false)
        now = 95 * minute
        assertEquals(10 * minute, advisor.continuousDrivingMillis())
    }
}
