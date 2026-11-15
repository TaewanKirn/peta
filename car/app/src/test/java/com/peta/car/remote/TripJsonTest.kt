package com.peta.car.remote

import com.peta.car.trip.withRang
import com.peta.car.trip.withWa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripJsonTest {
    private fun json(rest: Int = 30, hospital: String = "null") = """
        {
          "id": "trip-양평-2026-11-18",
          "title": "설이와 양평 여행",
          "dateLabel": "11.18 – 11.19 · 1박 2일",
          "guardianCount": 3,
          "origin": "서울 강남구",
          "theme": "자연 속 휴식",
          "pets": [{
            "name": "설이", "species": "dog", "breed": "말티즈", "age": 2, "sizeLabel": "소형견",
            "weightKg": 4, "restIntervalMinutes": $rest, "regularHospital": $hospital
          }],
          "destination": {
            "place": { "id": "river-stay", "name": "강변 스테이", "address": "경기 양평", "lat": 37.5215, "lng": 127.5304 },
            "activity": "설이랑 강변 산책로 걷기",
            "nearbyInfo": "산책로 입구는 주차장 옆에 있어요."
          },
          "savedAt": "2026-11-04T10:00:00.000Z"
        }
    """.trimIndent()

    @Test
    fun `모바일에서 등록한 반려동물과 목적지가 반영된다`() {
        val plan = TripJson.parse(json())

        assertEquals("설이", plan.pet.name)
        assertEquals("말티즈", plan.pet.breed)
        assertEquals(2, plan.pet.age)
        assertEquals("소형견", plan.pet.sizeLabel)
        assertEquals(3, plan.guardianCount)
        assertEquals("강변 스테이", plan.destination.place.name)
        assertEquals(37.5215, plan.destination.place.point.lat, 0.0)
        assertEquals(127.5304, plan.destination.place.point.lng, 0.0)
        assertEquals("설이랑 강변 산책로 걷기", plan.destination.activity)
    }

    @Test
    fun `휴식 주기가 휴식 안내 시점이 되고 샘플 이름이 남지 않는다`() {
        val plan = TripJson.parse(json(rest = 45))

        assertEquals(45, plan.restAtMinutes)
        assertEquals(45, plan.petRestIntervalMinutes)
        assertEquals("설이랑 산책하고 물 마시기", plan.plannedRest?.activity)
    }

    @Test
    fun `휴식 주기가 길어도 안내 시점은 운전자 상한을 넘지 않는다`() {
        val plan = TripJson.parse(json(rest = 180))

        assertEquals(120, plan.restAtMinutes) // 운전자 휴식 상한
        assertEquals(190, plan.estimatedMinutes)
    }

    @Test
    fun `병원은 있을 때만 들어간다`() {
        assertNull(TripJson.parse(json()).regularHospital)

        val hospital = """{ "id": "h-1", "name": "해피 동물병원", "address": "서울", "lat": 37.5, "lng": 127.0 }"""
        assertEquals("해피 동물병원", TripJson.parse(json(hospital = hospital)).regularHospital?.name)
    }

    @Test
    fun `받침에 맞는 조사를 붙인다`() {
        assertEquals("보리와", "보리".withWa())
        assertEquals("단풍과", "단풍".withWa())
        assertEquals("보리랑", "보리".withRang())
        assertEquals("단풍이랑", "단풍".withRang())
    }
}
