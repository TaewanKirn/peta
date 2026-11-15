package com.peta.car.remote

import com.peta.car.PetaConfig
import com.peta.car.place.GeoPoint
import com.peta.car.place.Place
import com.peta.car.place.PlaceType
import com.peta.car.trip.PetProfile
import com.peta.car.trip.TripPlan
import com.peta.car.trip.TripStop
import com.peta.car.trip.withRang
import org.json.JSONObject

/**
 * 모바일 앱이 PUT /api/trip으로 저장한 JSON을 TripPlan으로 바꾼다.
 * 형식은 모바일 `mobile/src/domain/types.ts`의 CarTrip과 같다.
 *
 * 모바일에서 오는 값: 반려동물 정보, 목적지, 날짜, 보호자 수, 휴식 주기.
 * 아직 모바일에 없는 값은 샘플을 쓴다: 경유 휴게소, 준비물, 예상 이동 시간, 경로상 거리.
 */
object TripJson {
    /** 쉬고 나서 목적지까지 남은 시간(분). 예상 이동 시간이 휴식 시점보다 짧아지지 않게 한다. */
    private const val MIN_MINUTES_AFTER_REST = 70

    fun parse(json: String, sample: TripPlan = SampleTrip.plan): TripPlan {
        val root = JSONObject(json)

        val petsJson = root.getJSONArray("pets")
        val pets = List(petsJson.length()) { i ->
            val pet = petsJson.getJSONObject(i)
            PetProfile(
                name = pet.getString("name"),
                breed = pet.optString("breed"),
                age = pet.getInt("age"),
                sizeLabel = pet.optString("sizeLabel"),
                restIntervalMinutes = pet.getInt("restIntervalMinutes"),
                regularHospital = pet.optJSONObject("regularHospital")?.let(::hospital),
            )
        }
        require(pets.isNotEmpty()) { "pets is empty" }

        val destination = root.getJSONObject("destination")
        val place = destination.getJSONObject("place")
        // 운전자 휴식 상한을 넘겨 안내하면 실제 제안 시점과 달라진다
        val restAt = minOf(pets.minOf { it.restIntervalMinutes }, PetaConfig.DRIVER_REST_AFTER_MINUTES.toInt())

        return sample.copy(
            id = root.getString("id"),
            title = root.getString("title"),
            dateLabel = root.getString("dateLabel"),
            guardianCount = root.getInt("guardianCount"),
            pets = pets,
            waypoints = sample.waypoints.map { stop ->
                stop.copy(activity = "${pets.first().name.withRang()} 산책하고 물 마시기")
            },
            destination = sample.destination.copy(
                place = sample.destination.place.copy(
                    id = place.getString("id"),
                    name = place.getString("name"),
                    address = place.getString("address"),
                    point = GeoPoint(place.getDouble("lat"), place.getDouble("lng")),
                ),
                activity = destination.getString("activity"),
                nearbyInfo = destination.getString("nearbyInfo"),
            ),
            estimatedMinutes = maxOf(sample.estimatedMinutes, restAt + MIN_MINUTES_AFTER_REST),
            restAtMinutes = restAt,
            origin = root.optString("origin").ifBlank { sample.origin },
        )
    }

    private fun hospital(json: JSONObject) = Place(
        id = json.getString("id"),
        name = json.getString("name"),
        point = GeoPoint(json.getDouble("lat"), json.getDouble("lng")),
        address = json.getString("address"),
        type = PlaceType.HOSPITAL,
        icon = "🏥",
        phone = json.optString("phone").trim(),
    )
}
