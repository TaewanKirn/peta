package com.peta.car.place

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 카카오 로컬 키워드 검색(dapi.kakao.com/v2/local/search/keyword). 동물병원을 현재 위치 기준 가까운 순으로 찾는다.
 * 응답의 distance는 검색 중심에서의 직선 거리(m)다. 경로 거리가 아니다.
 */
class KakaoLocalApi(
    private val restKey: String,
    private val baseUrl: String = BASE_URL,
) {
    val isConfigured: Boolean get() = restKey.isNotBlank()

    suspend fun animalHospitals(center: GeoPoint, radiusM: Int = 20_000, limit: Int = 15): List<Place> =
        fetch(keywordUrl("동물병원", center, radiusM, limit)).let(KakaoJson::parseHospitals)

    internal fun keywordUrl(query: String, center: GeoPoint, radiusM: Int, limit: Int): String {
        val params = listOf(
            "query" to query, "x" to center.lng.toString(), "y" to center.lat.toString(),
            "radius" to radiusM.coerceIn(0, 20_000).toString(), "sort" to "distance",
            "size" to limit.coerceIn(1, 15).toString(),
        )
        return baseUrl + "?" + params.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
    }

    private suspend fun fetch(requestUrl: String): String = withContext(Dispatchers.IO) {
        val connection = URL(requestUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("Authorization", "KakaoAK $restKey")
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("KakaoLocal status $code")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val BASE_URL = "https://dapi.kakao.com/v2/local/search/keyword.json"
        private const val TIMEOUT_MILLIS = 4_000
    }
}

object KakaoJson {
    /**
     * category_name이 "… > 반려동물 > 동물병원"인 곳만. 사람 병원·동물약국 등은 거른다.
     * 페타는 반려견 여행 앱이라 이름에 고양이 전문(고양이 · 캣)이 드러나는 병원도 뺀다.
     */
    fun parseHospitals(json: String): List<Place> {
        val docs = JSONObject(json).optJSONArray("documents") ?: return emptyList()
        return (0 until docs.length()).mapNotNull { i ->
            val d = docs.getJSONObject(i)
            if ("동물병원" !in d.optString("category_name")) return@mapNotNull null
            val lng = d.optString("x").toDoubleOrNull() ?: return@mapNotNull null
            val lat = d.optString("y").toDoubleOrNull() ?: return@mapNotNull null
            val id = d.optString("id").ifEmpty { return@mapNotNull null }
            val name = d.optString("place_name").trim().ifEmpty { return@mapNotNull null }
            if (isCatOnly(name)) return@mapNotNull null
            Place(
                id = "kakao-$id",
                name = name,
                point = GeoPoint(lat, lng),
                address = d.optString("road_address_name").ifBlank { d.optString("address_name") },
                type = PlaceType.HOSPITAL,
                icon = "🏥",
                routeKm = d.optString("distance").toDoubleOrNull()?.div(1000.0),
                phone = d.optString("phone").trim(),
            )
        }
    }

    private val CAT_ONLY_WORDS = listOf("고양이", "캣", "cat")

    internal fun isCatOnly(name: String): Boolean =
        CAT_ONLY_WORDS.any { name.contains(it, ignoreCase = true) }
}
