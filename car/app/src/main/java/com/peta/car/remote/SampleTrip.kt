package com.peta.car.remote

import com.peta.car.place.GeoPoint
import com.peta.car.place.Place
import com.peta.car.place.PlaceType
import com.peta.car.place.SamplePlaces
import com.peta.car.trip.PetProfile
import com.peta.car.trip.TripPlan
import com.peta.car.trip.TripStop

/** 모바일 앱(M01~M03)에서 저장했다고 가정한 샘플 여행 */
object SampleTrip {
    val plan = TripPlan(
        id = "trip-gangneung-1118",
        title = "리치와 강릉 여행",
        dateLabel = "11.18 - 11.19 · 1박 2일",
        guardianCount = 2,
        pets = listOf(
            PetProfile(
                name = "리치", breed = "말티푸", age = 1, sizeLabel = "소형견", // 시연 영상용(퍼피). 나이는 [가정]
                restIntervalMinutes = 70,
                regularHospital = SamplePlaces.regularHospital,
            ),
        ),
        waypoints = listOf(
            TripStop(
                place = SamplePlaces.gapyeongRest,
                plannedStayMinutes = 15,
                activity = "리치 산책하고 물 마시기",
                nearbyInfo = "강아지 쉼터가 입구 동쪽 끝부분에 있어요.",
            ),
        ),
        destination = TripStop(
            place = Place(
                "d-1", "숲속 스테이", GeoPoint(37.7400, 128.8600), // 강릉 구정면 일대. 숙소 이름은 [가정]
                "강원도 강릉", PlaceType.DESTINATION, icon = "🏡",
                // 가평휴게소(춘천 방향)에서 남은 도로 거리 · 시간. 카카오 길찾기 2026-11-13 측정
                routeKm = 161.5, routeMinutes = 109,
                category = "숙소",
            ),
            plannedStayMinutes = 24 * 60,
            activity = "리치랑 울타리 운동장에서 신나게 뛰어놀기",
            nearbyInfo = "반려견 샤워실은 별관 1층에 있어요.",
        ),
        checklist = listOf("배변봉투", "물 · 물그릇", "간식", "리드줄 · 하네스"),
        origin = "성남 수정구",
        estimatedMinutes = 155, // 성남 수정구 → 가평휴게소 46분 + 강릉 109분
        restAtMinutes = 70,
    )
}
