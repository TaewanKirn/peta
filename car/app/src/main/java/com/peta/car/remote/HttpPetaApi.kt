package com.peta.car.remote

import com.peta.car.trip.TripPlan
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 여행 계획만 실제 HTTP로 받고, 나머지 보고는 서버가 생길 때까지 [reports]에 맡긴다.
 */
class HttpPetaApi(
    private val tripUrl: String,
    private val reports: PetaApi = FakePetaApi(),
) : PetaApi by reports {

    override suspend fun fetchTripPlan(): TripPlan? = withContext(Dispatchers.IO) {
        val connection = URL(tripUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK ->
                    TripJson.parse(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw IOException("unexpected status $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 2_000
    }
}
