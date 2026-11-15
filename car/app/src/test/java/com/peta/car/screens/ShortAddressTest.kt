package com.peta.car.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortAddressTest {
    @Test fun keepsRegionAndRoadDropsNumbers() {
        assertEquals("서울특별시 마포구 월드컵북로6길", shortAddress("서울특별시 마포구 월드컵북로6길 84 (동교동)"))
        assertEquals("강원도 강릉", shortAddress("강원도 강릉"))
        assertEquals("", shortAddress(""))
    }
}
