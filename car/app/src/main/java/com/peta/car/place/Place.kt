package com.peta.car.place

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(val lat: Double, val lng: Double)

enum class PlaceType { DOG_REST, HOSPITAL, DESTINATION }

data class Place(
    val id: String,
    val name: String,
    val point: GeoPoint,
    val address: String,
    val type: PlaceType,
    val description: String = "",
    /** 페타 사용자 방문 수 (6-2. 여기 페타 사용자 몇 명이 다녀갔어요) */
    val petaVisitors: Int = 0,
    val reviewOneLine: String = "",
    /** 경로상 남은 거리(km). 서버가 경로 기준으로 계산해 준 값이 있으면 직선 거리 대신 쓴다. */
    val routeKm: Double? = null,
    val icon: String = "📍",
    /** 경로상 예상 소요 시간(분). 서버 값이 없으면 거리로 어림한다. */
    val routeMinutes: Int? = null,
    /** 검색 칩 분류 (휴게소 · 카페 · 식당 · 공원 · 산책로 · 숙소 …) */
    val category: String = "",
    /** 반려견 쉼터 위치 (C05 "입구 동쪽 끝부분") */
    val shelterSpot: String = "",
    /** 전화번호. 없으면 빈 문자열 (전화 버튼은 번호가 있을 때만 연결한다) */
    val phone: String = "",
    /** 대표 이미지 URL (https). 없으면 빈 문자열이고 카드는 placeholder를 쓴다. */
    val imageUrl: String = "",
)

data class PlaceDistance(val place: Place, val km: Double)

/** 두 좌표 사이 직선 거리(km). 실제 도로 거리/시간은 NaviHelper 경로 요청으로 얻는다. */
fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
    val r = 6371.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val h = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
    return 2 * r * asin(sqrt(h))
}
