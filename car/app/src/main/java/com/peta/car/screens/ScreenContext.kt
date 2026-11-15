package com.peta.car.screens

import android.view.View
import com.peta.car.PetaService
import com.peta.car.PetaStore
import com.peta.car.PetaUiState
import com.peta.car.R
import com.peta.car.place.Place
import com.peta.car.ui.Lotties
import com.peta.car.ui.NavTab
import com.peta.car.ui.PanelCanvas
import java.util.Locale

/**
 * 화면 번호 (차량 화면 규칙 25, 2026-11-12 다시 매김)
 * C00 스플래시 / C01 홈 (-R 재탑승, -E 연결 실패) / C02~C04 출발 준비 (C04-E 경로 실패)
 * C05~C06 주행 중 (C05-V 음성 질문, C06-E 음성 인식 실패) / C07~C10 도착·휴식·후기 (C10-S 저장 확인)
 * C11 응급 / C12~C13 검색 (C13-E 결과 없음) / C14 설정. 2/3 확장(-W)은 같은 함수에서 c.wide로 나눈다.
 */
enum class Screen(val title: String) {
    C00_SPLASH(""),
    C01_HOME("홈"),
    C01R_HOME_TRIP("홈"),
    C02_CONFIRM("출발 준비"),
    C03_CHECKLIST("출발 준비"),
    C04_ROUTE("출발 준비"),
    C04E_ROUTE_FAIL("출발 준비"),
    C05_DRIVING("주행 중"),
    C05V_STOP_VOICE("주행 중"),
    C06_REST("휴식 제안"),
    C06E_REST_RETRY("휴식 제안"),
    C07_ARRIVED_STOP("경유지 도착"),
    C08_RESTING("휴식 중"),
    C09_ARRIVED_DEST("목적지 도착"),
    C10_REVIEW("여행 후기"),
    C11_EMERGENCY("응급"),
    C12_SEARCH("검색"),
    C13E_SEARCH_EMPTY("검색"),
    C14_SETTINGS("설정");

    /** 주행 중에도 본문을 누를 수 있는 화면 (규칙 13: 응급, C05 변경/취소) */
    val usableWhileDriving get() = this == C05_DRIVING || this == C11_EMERGENCY
}

enum class SearchKind { REST, TRAVEL }

/** 화면 안에서만 쓰는 상태 (탭, 체크, 평점, 검색어 …) */
class UiLocal {
    var tab = NavTab.HOME
    var emergency = false
    val checked = mutableSetOf<Int>()
    var rating = 0
    var bounceBone = -1
    /** C10-S 팝업을 띄운 장소 이름 */
    var savedReviewPlace: String? = null
    var savedRating = 0
    var sampleAccepted = false
    var homeIndex = 0
    var homeChip = "전체"
    /** 홈 · 검색이 함께 쓰는 추천 반경(km). [범위 넓히기]로 20 → 50 → 100 → 200 */
    var radiusKm = 20
    /** 홈 장소 상세 팝업에 띄운 장소 id */
    var homeDetail: String? = null
    /** C01-R · C09 일정의 일차 (1 · 2) */
    var tripDay = 1
    var searchKind = SearchKind.REST
    var query = ""
    var category: String? = null
    var selectedId: String? = null
    var focusSearch = false
    /** C13-E [범위 넓혀 찾기]를 누른 뒤 쓰는 낱말. null이면 넓히지 않은 상태. */
    var widenKey: String? = null
    var hospitalIndex = 0
    var splashUntil = 0L
}

interface ScreenHost {
    fun rerender()
    fun toast(message: String)
    fun closePanel()
    fun goTab(tab: NavTab)
    /** 화면을 다시 그리지 않고 매 상태마다 바꿀 값 (주행 시간, 체류 시간) */
    fun live(update: (PetaUiState) -> Unit)
    /** 헤더·탭 위에 덮는 팝업 */
    fun overlay(draw: () -> Unit)
    fun later(delayMillis: Long, block: () -> Unit)
    fun showKeyboard(view: View)
    /** 차량에 연결된 폰의 전화 앱으로 번호를 넘긴다 */
    fun dial(phone: String)
}

/** 화면 함수가 받는 묶음 */
class Scr(val c: PanelCanvas, val s: PetaUiState, val ui: UiLocal, val host: ScreenHost) {
    val wide get() = c.wide
    val actions get() = PetaStore.actions
    val driving get() = s.vehicle.isDriving
    /** 2/3 확장 화면의 본문 왼쪽 시작점 (세로 메뉴 120 + 여백 58) */
    val left get() = if (wide) 178 else 58
    val right get() = c.panelW - 58
}

// ---------------- 문구 ----------------

/** 100 → "1시간 40분" */
fun hm(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "${m}분"
        m == 0 -> "${h}시간"
        else -> "${h}시간 ${m}분"
    }
}

/** 4.2km / 62km */
fun km(value: Double): String =
    if (value < 10) String.format(Locale.US, "%.1fkm", value).replace(".0km", "km") else "${value.toInt()}km"

fun shortName(place: Place) = PetaService.shortName(place)

fun eta(place: Place) = PetaService.etaMinutes(place)

/** 거리(km)만 있을 때 예상 시간 (평균 60km/h) */
fun etaForKm(distance: Double) = (distance / 60.0 * 60).toInt().coerceAtLeast(1)

/** 장소 한 줄 설명. 설명이 짧은 분류(식당 · 카페)면 "반려견과 갈 수 있는 식당" */
fun blurb(place: Place): String = when {
    place.description.length > 5 -> place.description
    place.description.isNotBlank() -> "반려견과 갈 수 있는 ${place.description}"
    else -> "반려견과 쉴 수 있어요"
}

// ---------------- 그림 ----------------

/** 장소 사진 (Figma 스타일프레임에서 내보낸 샘플 사진). 없으면 null */
fun photoOf(place: Place): Int? = when (place.id) {
    "r-2", "r-5" -> R.drawable.photo_seaside
    "r-3" -> R.drawable.photo_cafe
    "r-4", "t-1" -> R.drawable.photo_sage
    else -> null
}

/** 카드 태그의 작은 아이콘 */
fun categoryIcon(place: Place): Int = when (place.description) {
    "식당" -> R.drawable.img_cat_restaurant
    "카페" -> R.drawable.img_cat_cup
    else -> R.drawable.img_cat_mountain
}

/** 준비물 순서대로 3D 오브젝트 (C03) */
val CHECKLIST_ART = listOf(Lotties.OBJ_POOP_BAG, Lotties.OBJ_BOWL_BOTTLE, Lotties.OBJ_BONE, Lotties.OBJ_LEASH)

/** 뼈다귀 개수별 말 (규칙 20) */
val RATING_WORDS = listOf("아쉬웠어요", "그저 그랬어요", "괜찮았어요", "좋았어요", "최고였어요")
