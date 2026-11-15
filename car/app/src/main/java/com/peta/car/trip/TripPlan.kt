package com.peta.car.trip

import com.peta.car.place.Place

data class PetProfile(
    val name: String,
    val breed: String,
    val age: Int,
    val sizeLabel: String,
    /** 온보딩 "반려견과 차량 탑승 시 얼마나 자주 쉬나요?" (M02) */
    val restIntervalMinutes: Int,
    val regularHospital: Place?,
)

data class TripStop(
    val place: Place,
    val plannedStayMinutes: Int,
    /** "여기서 OOO 하기로 했어요" */
    val activity: String,
    /** "강아지 쉼터가 OO에 있어요" */
    val nearbyInfo: String,
)

/** 모바일 앱에서 저장한 여행(M03 "이 여행지 저장하기"). 서버를 거쳐 차량으로 내려온다. */
data class TripPlan(
    val id: String,
    val title: String,
    val dateLabel: String,
    val guardianCount: Int,
    val pets: List<PetProfile>,
    val waypoints: List<TripStop>,   // NaviHelper 경유지 최대 2개
    val destination: TripStop,
    val checklist: List<String>,
    val estimatedMinutes: Int,
    /** 브리핑에 안내하는 "N분 무렵 한 번 쉬어요" */
    val restAtMinutes: Int,
    /** 모바일에서 입력한 출발지 (C03) */
    val origin: String = "",
) {
    val pet: PetProfile get() = pets.first()

    /** "보리 · 3살 대형견" */
    val companionsLabel: String
        get() = pets.joinToString(", ") { "${it.name} · ${it.age}살 ${it.sizeLabel}" }

    /** 계획에 들어 있는 첫 쉼터 */
    val plannedRest: TripStop? get() = waypoints.firstOrNull()

    /** 반려견 휴식 주기 중 가장 짧은 값 */
    val petRestIntervalMinutes: Int?
        get() = pets.minOfOrNull { it.restIntervalMinutes }

    val regularHospital: Place?
        get() = pets.firstNotNullOfOrNull { it.regularHospital }
}

/** 차량에서 여행을 이어받는 단계 (C01 → C02 → C03 → 길 안내) */
enum class TripStage { WELCOME, BRIEFING, SUMMARY, ON_TRIP }

enum class TripPhase { READY, DRIVING, AT_WAYPOINT, AT_DESTINATION }

data class Visit(
    val tripId: String,
    val placeName: String,
    val arrivedAt: Long,
)
