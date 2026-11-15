package com.peta.car.place

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceRepositoryTest {
    private val here = GeoPoint(37.75, 128.87)
    private fun rest(id: String, km: Double) =
        Place(id, id, GeoPoint(37.75, 128.87), "", PlaceType.DOG_REST, routeKm = km, category = "카페")

    @Test fun usesSamplesUntilServerAnswers() {
        val repo = PlaceRepository()
        assertTrue(repo.nearbyDogRest(here).isNotEmpty())
        assertTrue(repo.hospitalsFor(here, null).all { it.place.id.startsWith("h-") })
    }

    @Test fun serverValuesAreShownAsIsEvenWhenFew() {
        val repo = PlaceRepository()
        repo.updateNearby(rest = listOf(rest("a", 2.0)), travel = emptyList())
        repo.updateHospitals(emptyList())
        assertEquals(listOf("a"), repo.nearbyDogRest(here).map { it.place.id })
        assertEquals(listOf("a"), repo.restCandidates("").map { it.id })
        assertTrue(repo.travelCandidates("").isEmpty()) // 받았는데 없으면 샘플로 채우지 않는다
        assertTrue(repo.hospitalsFor(here, null).isEmpty())
    }

    @Test fun homeChipsMatchCategoryOrSynonymsInName() {
        fun p(name: String, category: String = "관광지") =
            Place(name, name, here, "", PlaceType.DESTINATION, category = category)
        assertTrue(PlaceRepository.matchesChip(p("강문해변"), "바다"))
        assertTrue(PlaceRepository.matchesChip(p("아무 곳", "카페"), "카페"))
        assertTrue(PlaceRepository.matchesChip(p("양주캠핑", "공원"), "캠핑장"))
        assertTrue(!PlaceRepository.matchesChip(p("강문해변"), "계곡"))
        assertTrue(PlaceRepository.matchesChip(p("강문해변"), "전체"))
    }

    @Test fun sampleRestStopKeepsShelterSpotAndCategory() {
        // 2026-11-13: 거리 주석이 같은 줄의 shelterSpot · category를 삼켜 C05 쉼터 위치가 사라진 적이 있다
        assertEquals("입구 동쪽 끝부분", SamplePlaces.gapyeongRest.shelterSpot)
        assertEquals("휴게소", SamplePlaces.gapyeongRest.category)
    }

    @Test fun wideNearbyAccumulatesAndFailureKeepsEarlierResults() {
        val repo = PlaceRepository()
        repo.updateWideNearby(50, listOf(rest("a", 30.0), rest("b", 45.0)))
        repo.setWideLoading(100)
        assertEquals(PlaceRepository.WideState.LOADING, repo.wideState(100))
        repo.setWideFailed(100) // 100km 조회가 실패해도 50km 결과는 남는다
        assertEquals(listOf("a", "b"), repo.wideNearby(100).map { it.id })
        assertEquals(PlaceRepository.WideState.FAILED, repo.wideState(100))
        repo.updateWideNearby(100, listOf(rest("b", 45.0), rest("c", 80.0)))
        assertEquals(listOf("a", "b", "c"), repo.wideNearby(100).map { it.id })
        assertEquals(listOf("a", "b"), repo.wideNearby(50).map { it.id })
    }

    @Test fun withRouteAddsRoadDistanceToPlacesOutsideTheLists() {
        val repo = PlaceRepository()
        val regular = Place("h-regular", "단골", here, "", PlaceType.HOSPITAL, routeKm = 120.0, routeMinutes = 160)
        assertEquals(160, repo.withRoute(regular).routeMinutes) // 못 쟀으면 그대로
        repo.applyRoute("h-regular", 16.0, 22)
        assertEquals(22, repo.withRoute(regular).routeMinutes)
        assertEquals(16.0, repo.withRoute(regular).routeKm!!, 1e-9)
    }

    @Test fun applyRouteReplacesStraightLineDistance() {
        val repo = PlaceRepository()
        repo.updateNearby(rest = listOf(rest("a", 2.0), rest("b", 3.0)), travel = emptyList())
        repo.applyRoute("a", km = 9.5, minutes = 17)
        val ranked = repo.nearbyDogRest(here)
        assertEquals(listOf("b", "a"), ranked.map { it.place.id }) // 도로 거리 9.5km가 직선 3km보다 멀다
        assertEquals(17, ranked.last().place.routeMinutes)
    }

    @Test fun searchHitsAreOnlyReturnedForTheSameQuery() {
        val repo = PlaceRepository()
        repo.updateNearby(rest = emptyList(), travel = emptyList())
        repo.updateSearchHits("카페", listOf(rest("x", 1.0)))
        assertEquals(listOf("x"), repo.restCandidates("카페").map { it.id })
        assertTrue(repo.restCandidates("식당").isEmpty())
    }

    @Test fun widenedHitsAddFarPlacesOnlyForTheSameKey() {
        val repo = PlaceRepository()
        repo.updateNearby(rest = emptyList(), travel = emptyList())
        assertTrue(repo.widenedHits("카페") == null) // 아직 못 받음
        repo.updateWidened("카페", listOf(rest("far", 150.0)))
        assertEquals(listOf("far"), repo.restCandidates("", "카페").map { it.id })
        assertTrue(repo.restCandidates("", "식당").isEmpty()) // 다른 낱말로 넓힌 결과는 섞이지 않는다
        assertTrue(repo.restCandidates("", null).isEmpty()) // 넓히지 않았으면 그대로
        // 서버가 낱말로 골라 준 곳은 이름에 낱말이 없어도 남는다
        assertEquals(listOf("far"), repo.search(repo.restCandidates("", "카페"), "", null, "카페").map { it.id })
    }

    @Test fun widenedEmptyIsDifferentFromNotYetReceived() {
        val repo = PlaceRepository()
        repo.updateWidened("없는곳", emptyList())
        assertTrue(repo.widenedHits("없는곳") == emptyList<Place>())
        assertTrue(repo.widenedHits("다른곳") == null)
    }
}
