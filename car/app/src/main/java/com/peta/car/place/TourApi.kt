package com.peta.car.place

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 한국관광공사 반려동물 동반여행 API (data.go.kr 15135102) 클라이언트.
 * 엔드포인트·오퍼레이션 이름은 포털 활용신청 화면 기준(2026-11-12). 응답 필드는 실제 호출로 검증 전이다.
 *
 * 인증키는 Decoding(일반) 키를 쓴다. 파라미터를 여기서 한 번만 URL 인코딩한다.
 */
class TourApi(
    private val serviceKey: String,
    private val baseUrl: String = BASE_URL,
) {
    val isConfigured: Boolean get() = serviceKey.isNotBlank()

    /** 위치 기반 조회: 가까운 순. 반경은 200km까지 받는다(2026-11-13 확인: 20km 13곳 · 50km 27곳 · 100km 43곳, 홍익대 기준). */
    suspend fun nearby(center: GeoPoint, radiusM: Int = 10_000, limit: Int = 20): List<Place> =
        fetch(locationUrl(center, radiusM, limit))

    /** 반려동물 동반 조건 (detailPetTour2). [placeId]는 "tour-{contentid}". 정보가 없으면 빈 [PetRule]. */
    suspend fun petRule(placeId: String): PetRule = withContext(Dispatchers.IO) {
        val contentId = placeId.removePrefix("tour-")
        val connection = URL(url("detailPetTour2", "contentId" to contentId)).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("TourApi status ${connection.responseCode}")
            TourJson.parsePetRule(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    /** 키워드 조회. */
    suspend fun search(keyword: String, limit: Int = 20): List<Place> =
        fetch(keywordUrl(keyword, limit))

    internal fun locationUrl(center: GeoPoint, radiusM: Int, limit: Int): String =
        url("locationBasedList2", "mapX" to center.lng.toString(), "mapY" to center.lat.toString(),
            "radius" to radiusM.coerceIn(1, MAX_RADIUS_M).toString(), "numOfRows" to limit.toString(), "arrange" to "E")

    internal fun keywordUrl(keyword: String, limit: Int): String =
        url("searchKeyword2", "keyword" to keyword, "numOfRows" to limit.toString())

    private fun url(operation: String, vararg params: Pair<String, String>): String {
        val common = listOf(
            "serviceKey" to serviceKey, "MobileOS" to "AND", "MobileApp" to "PETA", "_type" to "json",
        )
        val query = (common + params).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        return "$baseUrl/$operation?$query"
    }

    private suspend fun fetch(requestUrl: String): List<Place> = withContext(Dispatchers.IO) {
        val connection = URL(requestUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("TourApi status $code")
            TourJson.parsePlaces(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val BASE_URL = "https://apis.data.go.kr/B551011/KorPetTourService2"
        const val MAX_RADIUS_M = 200_000
        private const val TIMEOUT_MILLIS = 8_000 // 반경 100km 이상 조회는 4초를 넘기기도 한다 (2026-11-13)
    }
}

/** TourAPI 공통 응답 구조: response.header.resultCode, response.body.items.item[]. */
object TourJson {
    fun parsePlaces(json: String): List<Place> {
        val text = json.trim()
        // 인증 오류 등은 _type=json이어도 XML로 온다. 키 값이 섞일 수 있는 본문은 오류 문구에 담지 않는다.
        if (!text.startsWith("{")) throw IOException("TourApi non-JSON response")
        val response = JSONObject(text).optJSONObject("response") ?: throw IOException("TourApi no response")
        val code = response.optJSONObject("header")?.optString("resultCode").orEmpty()
        if (code != "0000") throw IOException("TourApi resultCode=$code")
        // 결과가 없으면 items가 객체가 아니라 빈 문자열이다.
        val items = response.optJSONObject("body")?.optJSONObject("items")?.optJSONArray("item") ?: return emptyList()
        return (0 until items.length()).mapNotNull { toPlace(items.getJSONObject(it)) }
    }

    fun parsePetRule(json: String): PetRule {
        val text = json.trim()
        if (!text.startsWith("{")) throw IOException("TourApi non-JSON response")
        val response = JSONObject(text).optJSONObject("response") ?: throw IOException("TourApi no response")
        val code = response.optJSONObject("header")?.optString("resultCode").orEmpty()
        if (code != "0000") throw IOException("TourApi resultCode=$code")
        val item = response.optJSONObject("body")?.optJSONObject("items")?.optJSONArray("item")?.optJSONObject(0)
            ?: return PetRule()
        fun f(key: String) = item.optString(key).trim()
        return PetRule(
            area = f("acmpyTypeCd"), allowed = f("acmpyPsblCpam"), needs = f("acmpyNeedMtr"),
            extra = f("etcAcmpyInfo"), facilities = f("relaPosesFclty"),
        )
    }

    private fun toPlace(item: JSONObject): Place? {
        val lat = item.optString("mapy").toDoubleOrNull() ?: return null
        val lng = item.optString("mapx").toDoubleOrNull() ?: return null
        val title = item.optString("title").trim().ifEmpty { return null }
        val contentId = item.optString("contentid").ifEmpty { return null }
        val category = categoryOf(item.optString("contenttypeid"), item.optString("cat3"), title)
        return Place(
            id = "tour-$contentId",
            name = title,
            point = GeoPoint(lat, lng),
            address = item.optString("addr1").trim(),
            type = if (category == "숙소" || category == "관광지") PlaceType.DESTINATION else PlaceType.DOG_REST,
            description = category,
            // dist는 검색 중심에서의 직선 거리(m). 위치 기반 조회에만 있다.
            routeKm = item.optString("dist").toDoubleOrNull()?.div(1000.0),
            icon = iconOf(category),
            category = category,
            phone = item.optString("tel").trim(),
            imageUrl = httpsImage(item.optString("firstimage").ifBlank { item.optString("firstimage2") }),
        )
    }

    /** 관광공사 이미지는 http로 오는 경우가 있다. 앱은 평문 통신을 막으므로 https로 바꾼다. */
    internal fun httpsImage(url: String): String {
        val u = url.trim()
        return if (u.startsWith("http://")) "https://" + u.removePrefix("http://") else u
    }

    /** contentTypeId: 12 관광지 · 14 문화시설 · 15 행사 · 25 여행코스 · 28 레포츠 · 32 숙박 · 38 쇼핑 · 39 음식점 */
    internal fun categoryOf(contentTypeId: String, cat3: String, title: String): String = when {
        cat3 == "A05020900" -> "카페" // A05020900 = 카페/전통찻집
        contentTypeId == "38" -> "기타" // 쇼핑. 이름에 「카페」가 있어도 매장이다 (예: 올리브영 ○○카페거리점)
        "카페" in title -> "카페"
        contentTypeId == "39" -> "식당"
        contentTypeId == "32" -> "숙소"
        contentTypeId == "28" -> "공원"
        contentTypeId == "12" || contentTypeId == "14" -> "관광지"
        else -> "기타"
    }

    private fun iconOf(category: String) = when (category) {
        "카페" -> "☕"
        "식당" -> "🍽"
        "숙소" -> "🏨"
        "공원" -> "🌳"
        "관광지" -> "📍"
        else -> "📍"
    }
}
