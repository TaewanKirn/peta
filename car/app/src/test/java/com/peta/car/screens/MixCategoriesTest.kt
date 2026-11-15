package com.peta.car.screens

import com.peta.car.place.GeoPoint
import com.peta.car.place.Place
import com.peta.car.place.PlaceType
import org.junit.Assert.assertEquals
import org.junit.Test

class MixCategoriesTest {
    private fun p(name: String, category: String) =
        Place(name, name, GeoPoint(37.55, 126.92), "", PlaceType.DESTINATION, category = category)

    // 2026-11-13 홍익대 반경 20km 실제 순서(가까운 순): 숙소가 앞에 몰려 있다
    @Test fun interleavesByCategoryKeepingDistanceOrderInside() {
        val near = listOf(
            p("준하우스3", "숙소"), p("소담스테이", "숙소"), p("통인 1939", "숙소"),
            p("익선동 한옥거리", "관광지"), p("북악하늘길", "공원"), p("DDP", "관광지"), p("헤세의 정원", "식당"),
        )
        assertEquals(
            listOf("준하우스3", "익선동 한옥거리", "북악하늘길", "헤세의 정원", "소담스테이", "DDP", "통인 1939"),
            mixCategories(near).map { it.name },
        )
    }

    @Test fun emptyStaysEmpty() = assertEquals(emptyList<Place>(), mixCategories(emptyList()))
}
