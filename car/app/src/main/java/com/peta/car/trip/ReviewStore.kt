package com.peta.car.trip

import android.content.Context

/**
 * 목적지에 도착한 여행을 기억해 두었다가, 다음에 차에 탔을 때 후기를 요청한다.
 * (모바일 쪽 후기 요청은 서버가 방문 기록을 보고 푸시로 처리)
 */
class ReviewStore(context: Context) {
    private val prefs = context.getSharedPreferences("peta_review", Context.MODE_PRIVATE)

    fun pending(): Visit? {
        val name = prefs.getString(KEY_PLACE, null) ?: return null
        return Visit(
            tripId = prefs.getString(KEY_TRIP, "").orEmpty(),
            placeName = name,
            arrivedAt = prefs.getLong(KEY_AT, 0),
        )
    }

    fun save(visit: Visit) {
        prefs.edit()
            .putString(KEY_TRIP, visit.tripId)
            .putString(KEY_PLACE, visit.placeName)
            .putLong(KEY_AT, visit.arrivedAt)
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_TRIP = "trip"
        const val KEY_PLACE = "place"
        const val KEY_AT = "at"
    }
}
