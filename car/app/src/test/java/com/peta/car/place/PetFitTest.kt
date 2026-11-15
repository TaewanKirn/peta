package com.peta.car.place

import com.peta.car.trip.PetProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class PetFitTest {
    private fun pet(size: String, breed: String = "말티푸") =
        PetProfile(name = "리치", breed = breed, age = 1, sizeLabel = size, restIntervalMinutes = 70, regularHospital = null)

    private val small = pet("소형견")
    private val large = pet("대형견", "골든 리트리버")

    // 2026-11-13 detailPetTour2 실제 값
    @Test fun allBreedsOk() = assertEquals(Fit.OK, PetFit.of(PetRule(area = "전구역 동반가능", allowed = "전 견종 동반 가능"), large))
    @Test fun guideDogOnlyIsNo() = assertEquals(Fit.NO, PetFit.of(PetRule(allowed = "시각 장애인 안내견"), small))
    @Test fun trainedSmallDog() {
        assertEquals(Fit.OK, PetFit.of(PetRule(allowed = "훈련된 소형견"), small))
        assertEquals(Fit.NO, PetFit.of(PetRule(allowed = "훈련된 소형견"), large))
    }
    @Test fun kgLimit() {
        assertEquals(Fit.OK, PetFit.of(PetRule(allowed = "5kg 이하 숙박가능한 견종"), small))
        assertEquals(Fit.NO, PetFit.of(PetRule(allowed = "5kg 이하 숙박가능한 견종"), large))
    }
    @Test fun dangerousBreedExcluded() {
        assertEquals(Fit.OK, PetFit.of(PetRule(allowed = "맹견 제외"), small))
        assertEquals(Fit.NO, PetFit.of(PetRule(allowed = "맹견 제외"), pet("대형견", "핏불테리어")))
    }
    @Test fun unknownConditionsAreChecked() {
        assertEquals(Fit.CHECK, PetFit.of(PetRule(allowed = "털날림 많은 종은 불가"), small))
        assertEquals(Fit.CHECK, PetFit.of(PetRule(area = "전구역 동반가능"), small)) // 견종 조건 없음
        assertEquals(Fit.CHECK, PetFit.of(null, small))
    }
    @Test fun areaNotAllowed() = assertEquals(Fit.NO, PetFit.of(PetRule(area = "동반불가"), small))
    @Test fun summaryJoinsAreaAndNeeds() =
        assertEquals("일부구역 동반가능 · 목줄 착용", PetFit.summary(PetRule(area = "일부구역 동반가능", needs = "목줄 착용")))
}
