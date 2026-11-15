package com.peta.car.place

import java.io.IOException
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail

class TourApiTest {
    // TourAPI 공통 응답 구조를 따른 샘플. 실제 응답과 대조하기 전까지는 가정이다.
    private val ok = """
        {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":{"item":[
          {"contentid":"1","contenttypeid":"39","title":"바다앞식당","addr1":"강원 강릉시","mapx":"128.9","mapy":"37.75","dist":"812"},
          {"contentid":"2","contenttypeid":"39","title":"멍냥 카페","addr1":"강원 강릉시","mapx":"128.8","mapy":"37.7"},
          {"contentid":"3","contenttypeid":"12","title":"좌표 없음","addr1":"","mapx":"","mapy":""}
        ]},"numOfRows":3,"pageNo":1,"totalCount":3}}}
    """.trimIndent()

    @Test fun parsesItemsAndDropsRowsWithoutCoordinates() {
        val places = TourJson.parsePlaces(ok)
        assertEquals(listOf("바다앞식당", "멍냥 카페"), places.map { it.name })
        assertEquals(GeoPoint(37.75, 128.9), places[0].point)
        assertEquals("식당", places[0].category)
        assertEquals("카페", places[1].category) // 제목에 "카페"가 있으면 음식점 코드보다 우선
        assertEquals("tour-1", places[0].id)
    }

    @Test fun parsesImageUrlAndUpgradesHttp() {
        val json = """{"response":{"header":{"resultCode":"0000"},"body":{"items":{"item":[
          {"contentid":"1","contenttypeid":"39","title":"A","mapx":"128.9","mapy":"37.7","firstimage":"http://tong.visitkorea.or.kr/a.jpg"},
          {"contentid":"2","contenttypeid":"39","title":"B","mapx":"128.9","mapy":"37.7","firstimage":"","firstimage2":"https://tong.visitkorea.or.kr/b.jpg"},
          {"contentid":"3","contenttypeid":"39","title":"C","mapx":"128.9","mapy":"37.7"}
        ]}}}}"""
        val places = TourJson.parsePlaces(json)
        assertEquals("https://tong.visitkorea.or.kr/a.jpg", places[0].imageUrl)
        assertEquals("https://tong.visitkorea.or.kr/b.jpg", places[1].imageUrl) // firstimage가 비면 firstimage2
        assertEquals("", places[2].imageUrl)
    }

    @Test fun emptyItemsIsEmptyList() {
        val empty = """{"response":{"header":{"resultCode":"0000"},"body":{"items":"","totalCount":0}}}"""
        assertTrue(TourJson.parsePlaces(empty).isEmpty())
    }

    @Test fun nonZeroResultCodeThrows() {
        val bad = """{"response":{"header":{"resultCode":"0030","resultMsg":"x"}}}"""
        try { TourJson.parsePlaces(bad); fail() } catch (e: IOException) { assertTrue(e.message!!.contains("0030")) }
    }

    @Test fun xmlErrorBodyThrowsWithoutEchoingBody() {
        val xml = "<OpenAPI_ServiceResponse><cmmMsgHeader><returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>"
        try { TourJson.parsePlaces(xml); fail() } catch (e: IOException) { assertTrue(!e.message!!.contains("SERVICE_KEY")) }
    }

    @Test fun urlEncodesKeyOnceAndClampsRadius() {
        val api = TourApi("a+b/c==")
        val url = api.locationUrl(GeoPoint(37.5, 127.0), 999_999, 5)
        assertTrue(url.contains("serviceKey=a%2Bb%2Fc%3D%3D"))
        assertTrue(url.contains("radius=200000"))
        assertTrue(api.locationUrl(GeoPoint(37.5, 127.0), 50_000, 5).contains("radius=50000"))
        assertTrue(url.contains("locationBasedList2?"))
        assertTrue(url.contains("mapX=127.0&mapY=37.5"))
    }

    // 2026-11-12 locationBasedList2 실제 응답(강릉, 일부 필드 생략)
    private val real = """
        {"response": {"header":{"resultCode":"0000","resultMsg":"OK"},"body": {"items": {"item":[
        {"addr1":"강원특별자치도 강릉시 금성로13번길 17 (성남동)","cat3":"A05020900","contentid":"3537206","contenttypeid":"39","dist":"2146.0997604510117","mapx":"128.9001719187","mapy":"37.7533574475","title":"강릉 중화짬뽕빵"},
        {"addr1":"강원특별자치도 강릉시 사천면 해안로 902","cat3":"B02010700","contentid":"3536898","contenttypeid":"32","dist":"8823.6","mapx":"128.8769188198","mapy":"37.8313896308","title":"페리아902"}
        ]},"numOfRows":2,"pageNo":1,"totalCount":2}}}
    """.trimIndent()

    @Test fun parsesRealResponseAndClassifiesByCat3() {
        val places = TourJson.parsePlaces(real)
        assertEquals(listOf("카페", "숙소"), places.map { it.category })
        assertEquals(GeoPoint(37.7533574475, 128.9001719187), places[0].point)
    }

    @Test fun shopsNamedCafeAreNotCafes() {
        assertEquals("기타", TourJson.categoryOf("38", "", "올리브영 방배카페골목점"))
        assertEquals("카페", TourJson.categoryOf("39", "", "카페 아래로"))
        assertEquals("카페", TourJson.categoryOf("39", "A05020900", "어느 찻집"))
    }
}
