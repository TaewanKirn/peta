package com.peta.car.place

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KakaoLocalTest {
    // 2026-11-12 실제 응답(강릉, 일부 필드 생략) + 동물병원이 아닌 행
    private val json = """
        {"documents":[
         {"address_name":"강원특별자치도 강릉시 홍제동 1026-5","category_name":"가정,생활 > 반려동물 > 동물병원","distance":"891","id":"1163670563","phone":"033-645-5575","place_name":"위드동물병원","road_address_name":"강원특별자치도 강릉시 선수촌로63번길 18-7","x":"128.868337843353","y":"37.7570406578513"},
         {"address_name":"강릉시 어딘가","category_name":"의료,건강 > 병원 > 내과","distance":"900","id":"2","place_name":"사람병원","road_address_name":"","x":"128.8","y":"37.7"},
         {"address_name":"주소만 있음","category_name":"가정,생활 > 반려동물 > 동물병원","distance":"","id":"3","place_name":"곰곰동물병원","road_address_name":"","x":"128.88","y":"37.74"}
        ],"meta":{"total_count":3}}
    """.trimIndent()

    @Test fun keepsOnlyAnimalHospitals() {
        val places = KakaoJson.parseHospitals(json)
        assertEquals(listOf("위드동물병원", "곰곰동물병원"), places.map { it.name })
        assertEquals(PlaceType.HOSPITAL, places[0].type)
        assertEquals(0.891, places[0].routeKm!!, 1e-9)
        assertEquals("강원특별자치도 강릉시 선수촌로63번길 18-7", places[0].address)
        assertEquals("주소만 있음", places[1].address) // 도로명이 없으면 지번 주소
    }

    @Test fun dropsCatOnlyHospitals() {
        val cats = """
            {"documents":[
             {"category_name":"가정,생활 > 반려동물 > 동물병원","distance":"400","id":"10","place_name":"고양이병원 소설","x":"127.0","y":"37.5"},
             {"category_name":"가정,생활 > 반려동물 > 동물병원","distance":"500","id":"11","place_name":"캣닥터 동물병원","x":"127.0","y":"37.5"},
             {"category_name":"가정,생활 > 반려동물 > 동물병원","distance":"600","id":"12","place_name":"Cat Friendly Clinic","x":"127.0","y":"37.5"},
             {"category_name":"가정,생활 > 반려동물 > 동물병원","distance":"1800","id":"13","place_name":"고등동물병원","x":"127.0","y":"37.5"}
            ]}
        """.trimIndent()
        assertEquals(listOf("고등동물병원"), KakaoJson.parseHospitals(cats).map { it.name })
    }

    @Test fun emptyDocumentsIsEmpty() {
        assertTrue(KakaoJson.parseHospitals("""{"documents":[]}""").isEmpty())
    }

    @Test fun urlClampsRadiusAndSize() {
        val url = KakaoLocalApi("k").keywordUrl("동물병원", GeoPoint(37.5, 127.0), 50_000, 99)
        assertTrue(url.contains("radius=20000"))
        assertTrue(url.contains("size=15"))
        assertTrue(url.contains("sort=distance"))
        assertTrue(url.contains("x=127.0&y=37.5"))
    }
}
