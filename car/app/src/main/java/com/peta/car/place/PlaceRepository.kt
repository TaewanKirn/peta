package com.peta.car.place

/**
 * 반려견 쉼터 / 동물병원 검색.
 * 서버(한국관광공사 반려동물 동반여행 API, 카카오 로컬)에서 받은 값이 있으면 **그것만** 쓴다(적어도 그대로 보여 준다).
 * 서버 값을 아직 못 받았거나 키가 없을 때만 샘플(장소명은 가명 포함)로 동작한다.
 */
class PlaceRepository(
    private val dogRestPlaces: List<Place> = SamplePlaces.dogRest,
    private val hospitals: List<Place> = SamplePlaces.hospitals,
) {
    // null = 서버에서 아직 받지 못함(샘플 사용). 빈 목록 = 받았는데 없음(그대로 비워 둔다).
    @Volatile private var remoteRest: List<Place>? = null
    @Volatile private var remoteTravel: List<Place>? = null
    @Volatile private var remoteHospitals: List<Place>? = null
    @Volatile private var hits: Pair<String, List<Place>>? = null
    // 범위를 넓혀(전국 키워드 조회) 받아 둔 결과. 키는 그때 쓴 낱말.
    @Volatile private var widened: Pair<String, List<Place>>? = null
    /** 내비로 잰 도로 거리 · 시간. 목록에 없는 장소(단골 병원)도 [withRoute]로 붙인다. */
    private val routes = java.util.concurrent.ConcurrentHashMap<String, Pair<Double, Int>>()

    fun updateNearby(rest: List<Place>, travel: List<Place>) {
        remoteRest = rest
        remoteTravel = travel
    }

    fun updateHospitals(list: List<Place>) {
        remoteHospitals = list
    }

    fun updateSearchHits(query: String, list: List<Place>) {
        hits = query to list
    }

    /** 반려동물 동반 조건. 키가 없으면 아직 안 받았다. */
    private val petRules = java.util.concurrent.ConcurrentHashMap<String, PetRule>()

    fun putPetRule(id: String, rule: PetRule) { petRules[id] = rule }
    fun petRule(id: String): PetRule? = petRules[id]
    fun hasPetRule(id: String): Boolean = petRules.containsKey(id)

    /** 이 반려견이 갈 수 없다고 확인된 곳을 뺀다. 조건을 모르는 곳은 남긴다. */
    fun fitting(list: List<Place>, pet: com.peta.car.trip.PetProfile?): List<Place> =
        list.filter { PetFit.of(petRules[it.id], pet) != Fit.NO }

    /** 홈 「범위 넓히기」: 반경(km)을 넓혀 받은 주변 장소. 주행 중 쉼터 추천(remoteRest)과는 따로 둔다. */
    private val wide = java.util.concurrent.ConcurrentHashMap<Int, List<Place>>()
    private val wideStates = java.util.concurrent.ConcurrentHashMap<Int, WideState>()

    enum class WideState { NONE, LOADING, FAILED, DONE }

    fun wideState(radiusKm: Int): WideState = wideStates[radiusKm] ?: WideState.NONE
    fun setWideLoading(radiusKm: Int) { wideStates[radiusKm] = WideState.LOADING }
    /** 실패하면 빈 목록으로 두지 않는다 (넓혔는데 줄어드는 일을 막는다). 화면은 [다시 시도]를 보여 준다. */
    fun setWideFailed(radiusKm: Int) { wideStates[radiusKm] = WideState.FAILED }

    fun updateWideNearby(radiusKm: Int, list: List<Place>) {
        wide[radiusKm] = list
        wideStates[radiusKm] = WideState.DONE
    }

    /** 이 반경 이하로 받아 둔 목록을 모두 합친다. 넓힐수록 늘기만 한다. */
    fun wideNearby(radiusKm: Int): List<Place> =
        wide.filterKeys { it <= radiusKm }.toSortedMap().values.flatten().distinctBy { it.id }

    fun updateWidened(key: String, list: List<Place>) {
        widened = key to list
    }

    /** 범위를 넓혀 받아 둔 결과. null이면 아직 못 받았다(받았는데 없으면 빈 목록). */
    fun widenedHits(key: String?): List<Place>? = key?.let { widened?.takeIf { w -> w.first == it }?.second }

    /** 이 검색어로 서버에서 받아 둔 결과. 다른 검색어의 결과는 돌려주지 않는다. */
    fun searchHits(query: String): List<Place> = hits?.takeIf { it.first == query }?.second.orEmpty()

    /** 내비가 계산한 도로 거리·시간으로 바꾼다. 서버 장소 어디에 있든 id가 같으면 갱신한다. */
    fun applyRoute(id: String, km: Double, minutes: Int) {
        routes[id] = km to minutes
        fun List<Place>.patch() = map { if (it.id == id) it.copy(routeKm = km, routeMinutes = minutes) else it }
        remoteRest = remoteRest?.patch()
        remoteTravel = remoteTravel?.patch()
        remoteHospitals = remoteHospitals?.patch()
        hits = hits?.let { (q, list) -> q to list.patch() }
        wide.replaceAll { _, list -> list.patch() }
        widened = widened?.let { (k, list) -> k to list.patch() }
    }

    /** 잰 도로 거리가 있으면 붙여서 돌려준다. */
    fun withRoute(place: Place): Place =
        routes[place.id]?.let { (km, min) -> place.copy(routeKm = km, routeMinutes = min) } ?: place

    /** 가까운 쉼터 후보 */
    fun nearbyDogRest(from: GeoPoint, limit: Int = 3): List<PlaceDistance> =
        rank(remoteRest ?: dogRestPlaces, from).take(limit)

    /** C11은 가까운 순으로 병원을 넘겨 본다. 단골 병원은 아래 고정 박스에 따로 보여 준다(규칙 24). */
    fun hospitalsFor(from: GeoPoint, regular: Place?, limit: Int = 3): List<PlaceDistance> =
        rank((remoteHospitals ?: hospitals).filter { it.id != regular?.id }, from).take(limit)

    /** 검색 탭 「쉬어가기 좋은 곳」 후보. 서버 값이 있으면 서버(가까운 곳 + 검색 결과)만. */
    fun restCandidates(query: String, widenKey: String? = null): List<Place> =
        (remoteRest?.let { (it + searchHits(query).filter { p -> p.type == PlaceType.DOG_REST }).distinctBy { p -> p.id } }
            ?: (listOf(SamplePlaces.gapyeongRest) + dogRestPlaces))
            .withWidened(widenKey)

    fun travelCandidates(query: String, widenKey: String? = null): List<Place> =
        (remoteTravel?.let { (it + searchHits(query).filter { p -> p.type == PlaceType.DESTINATION }).distinctBy { p -> p.id } }
            ?: SamplePlaces.travel)
            .withWidened(widenKey)

    /** 사용자가 낱말로 직접 넓힌 결과는 분류(쉼터 · 여행지)와 상관없이 보여 준다. */
    private fun List<Place>.withWidened(key: String?): List<Place> =
        (this + widenedHits(key).orEmpty()).distinctBy { it.id }

    /** 경로상 거리가 있으면 그 순서로, 없으면 직선 거리 순 */
    private fun rank(list: List<Place>, from: GeoPoint) =
        list.map { PlaceDistance(it, it.routeKm ?: distanceKm(from, it.point)) }.sortedBy { it.km }

    /**
     * 검색. "애견", "반려견" 같은 말은 빼고 남은 낱말이 이름·분류·설명에 들어 있는 곳만 돌려준다.
     * 낱말이 남지 않으면 전체.
     */
    fun search(places: List<Place>, query: String, category: String?, widenKey: String? = null): List<Place> {
        val words = query.split(' ', ',').map { it.trim() }.filter { it.isNotEmpty() && it !in STOP_WORDS }
        // 서버가 이 검색어(또는 넓힌 낱말)로 이미 골라 준 곳은 낱말 비교를 건너뛴다
        val served = (searchHits(query) + widenedHits(widenKey).orEmpty()).map { it.id }.toSet()
        return places.filter { p ->
            (category == null || p.category == category) &&
                (words.isEmpty() || p.id in served || words.any { w -> p.name.contains(w) || p.category.contains(w) || p.description.contains(w) })
        }
    }

    companion object {
        /** 서비스와 화면이 같은 서버 값을 보도록 하나만 둔다. */
        val shared = PlaceRepository()

        private val STOP_WORDS = setOf("애견", "반려견", "강아지", "펫", "애완견", "댕댕이")

        /** 홈 칩 → 같은 뜻으로 보는 낱말. 서버 분류(카페 · 식당 · 숙소 · 공원 · 관광지)에 없는 칩은 이름 · 설명으로 맞춘다. */
        private val CHIP_WORDS = mapOf(
            "공원" to listOf("공원"),
            "카페" to listOf("카페"),
            "식당" to listOf("식당"),
            "휴게소" to listOf("휴게소", "쉼터"),
            "숙소" to listOf("숙소", "펜션", "호텔", "스테이", "글램핑", "리조트"),
            "산책로" to listOf("산책", "둘레길", "숲길", "올레"),
            "캠핑장" to listOf("캠핑"),
            "바다" to listOf("바다", "해변", "해수욕장", "해안"),
            "계곡" to listOf("계곡"),
        )

        /** 칩에 맞는 곳인지. 「전체」나 모르는 칩은 모두 통과 */
        fun matchesChip(place: Place, chip: String): Boolean {
            val words = CHIP_WORDS[chip] ?: return true
            return words.any { w -> place.category.contains(w) || place.name.contains(w) || place.description.contains(w) }
        }
    }
}

/**
 * 샘플 장소. 이름·거리·후기 한 줄은 Figma 스타일프레임(2026-11-12) 값이고, 지명은 강릉으로 통일한다(차량 화면 규칙 12).
 * 서버 값을 받기 전이나 키가 없을 때만 쓰는 시안 값이다. 병원은 카카오 로컬 검색으로 받는다(영업 상태 검증은 하지 않는다).
 */
object SamplePlaces {
    val regularHospital = Place(
        id = "h-regular", name = "우리동네 동물병원", point = GeoPoint(37.4979, 127.0276),
        address = "서울 (샘플)", type = PlaceType.HOSPITAL, icon = "🏥", routeKm = 12.5, routeMinutes = 20, // 성남 수정구에서 (카카오 길찾기 2026-11-13)
    )

    val hospitals = listOf(
        Place("h-1", "강릉 포레동물병원", GeoPoint(37.7600, 128.8900), "강원 강릉 (샘플)", PlaceType.HOSPITAL,
            icon = "🏥", routeKm = 4.2, routeMinutes = 12),
        Place("h-2", "온유동물병원", GeoPoint(37.7700, 128.9000), "강원 강릉 (샘플)", PlaceType.HOSPITAL,
            icon = "🏥", routeKm = 6.8, routeMinutes = 15),
        Place("h-3", "강릉 24시 동물의료센터", GeoPoint(37.7500, 128.8800), "강원 강릉 (샘플)", PlaceType.HOSPITAL,
            icon = "🏥", routeKm = 9.5, routeMinutes = 21),
    )

    val gapyeongRest = Place(
        "r-1", "가평휴게소 · 반려견 쉼터", GeoPoint(37.7019, 127.5468), "경기 가평군 · 춘천 방향 (샘플)", PlaceType.DOG_REST,
        description = "반려견 쉼터가 있어요", petaVisitors = 128, reviewOneLine = "잔디가 넓어서 좋아요",
        // 거리 · 시간은 성남 수정구(내비 위치)에서. 카카오 길찾기 2026-11-13 측정
        routeKm = 59.8, routeMinutes = 46, icon = "⛽", category = "휴게소", shelterSpot = "입구 동쪽 끝부분",
    )

    /** 검색 탭 「쉬어가기 좋은 곳」. description은 카드 태그다. */
    val dogRest = listOf(
        Place(
            "r-2", "바다앞식당", GeoPoint(37.7350, 127.4250), "강원 강릉 (샘플)", PlaceType.DOG_REST,
            description = "식당", petaVisitors = 20, reviewOneLine = "테라스가 넓어요.", routeKm = 8.0, routeMinutes = 12,
            icon = "🍽", category = "식당",
        ),
        Place(
            "r-3", "멍냥 카페", GeoPoint(37.7100, 127.4600), "강원 강릉 (샘플)", PlaceType.DOG_REST,
            description = "카페", petaVisitors = 12, reviewOneLine = "멍푸치노도 팔아요!", routeKm = 11.0, routeMinutes = 15,
            icon = "☕", category = "카페",
        ),
        Place(
            "r-4", "세이지 해안길", GeoPoint(37.6900, 127.4800), "강원 강릉 (샘플)", PlaceType.DOG_REST,
            description = "산책로", petaVisitors = 61, reviewOneLine = "산책로가 길어요.", routeKm = 14.0, routeMinutes = 18,
            icon = "🌳", category = "공원",
        ),
        Place(
            "r-5", "솔숲 쉼터", GeoPoint(37.6800, 127.5000), "강원 강릉 (샘플)", PlaceType.DOG_REST,
            description = "식당", petaVisitors = 20, reviewOneLine = "테라스가 넓어요.", routeKm = 8.0, routeMinutes = 12,
            icon = "🌲", category = "식당",
        ),
    )

    /** 검색 탭 「여행하기 좋은 곳」 · C01 추천 여행 */
    val travel = listOf(
        Place(
            "t-1", "세이지 해안길", GeoPoint(37.8000, 128.9100), "강원 강릉 (샘플)", PlaceType.DESTINATION,
            description = "산책로", petaVisitors = 61, reviewOneLine = "산책로가 길어요.", routeKm = 202.3, routeMinutes = 147, // 성남 수정구(내비 위치)에서 (카카오 길찾기 2026-11-13)
            icon = "🌊", category = "바다",
        ),
        Place(
            "t-2", "숲속 스테이", GeoPoint(37.7400, 128.8600), "강원 강릉 (샘플)", PlaceType.DESTINATION,
            description = "숙소", petaVisitors = 34, reviewOneLine = "울타리 운동장이 있어요.", routeKm = 195.2, routeMinutes = 139, // 성남 수정구(내비 위치)에서 (카카오 길찾기 2026-11-13)
            icon = "🏡", category = "숙소",
        ),
    )
}
