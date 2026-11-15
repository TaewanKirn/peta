package com.peta.car.remote

import android.util.Log
import com.peta.car.trip.TripPlan
import com.peta.car.trip.Visit

/**
 * 페타 서버 연동 지점 (Firebase 등으로 구현 예정).
 * - 여행 계획: 모바일에서 저장 → 차량이 조회
 * - 정차/하차/방문 기록: 차량이 보고 → 서버가 모바일 푸시(FCM), 후기 요청
 */
interface PetaApi {
    /** 모바일에서 저장한 여행. 저장된 여행이 없으면 null, 연결 실패는 예외. */
    suspend fun fetchTripPlan(): TripPlan?
    suspend fun reportParked(parkedAt: Long)
    suspend fun reportLeftVehicle(parkedAt: Long)
    suspend fun reportDeparted(stayedMinutes: Long)
    suspend fun reportVisit(visit: Visit)
    suspend fun submitReview(visit: Visit, stars: Int)
}

/** 서버가 준비되기 전까지 쓰는 구현: 샘플 여행 계획을 돌려주고 나머지는 로그만 남긴다. */
class FakePetaApi : PetaApi {
    override suspend fun fetchTripPlan(): TripPlan? = SampleTrip.plan

    override suspend fun reportParked(parkedAt: Long) = log("reportParked parkedAt=$parkedAt")

    override suspend fun reportLeftVehicle(parkedAt: Long) =
        log("reportLeftVehicle parkedAt=$parkedAt → 서버가 N분 뒤 모바일 알림 예약")

    override suspend fun reportDeparted(stayedMinutes: Long) =
        log("reportDeparted stayed=${stayedMinutes}min → 예약 알림 취소")

    override suspend fun reportVisit(visit: Visit) = log("reportVisit $visit")

    override suspend fun submitReview(visit: Visit, stars: Int) = log("submitReview ${visit.placeName} ★$stars")

    private fun log(message: String) {
        Log.i("PetaApi", message)
    }
}
