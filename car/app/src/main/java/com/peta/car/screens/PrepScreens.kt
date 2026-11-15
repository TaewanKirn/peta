package com.peta.car.screens

import com.peta.car.R
import com.peta.car.trip.TripPlan
import com.peta.car.trip.TripStage
import com.peta.car.trip.TripStop
import com.peta.car.ui.Bg
import com.peta.car.ui.ButtonStyle
import com.peta.car.ui.Ink
import com.peta.car.ui.Lotties
import com.peta.car.ui.NavTab
import com.peta.car.ui.Part
import com.peta.car.ui.Weight

/** 지금 정해진 쉬었다 갈 곳. 아직 고르지 않았으면 추천 경유지(여행 계획의 첫 쉼터) */
fun Scr.chosenStop(plan: TripPlan): TripStop? =
    if (s.plannedStopDecided || s.stage != TripStage.WELCOME) s.plannedStop else plan.plannedRest

/** 「다른 곳 고르기」: 검색 탭(쉬어가기 좋은 곳)에서 고르면 경유지가 된다 */
fun Scr.pickOtherStop() {
    ui.searchKind = SearchKind.REST
    ui.query = ""
    ui.category = null
    ui.selectedId = null
    host.goTab(NavTab.SEARCH)
}

// ---------------- C02 여행 확인 ----------------

fun Scr.c02Confirm(plan: TripPlan) {
    c.background(Bg.WARM)
    val dest = plan.destination.place
    val stop = chosenStop(plan)
    val info = "${plan.dateLabel} · 보호자 ${plan.guardianCount}명"
    if (wide) {
        c.text(178, 160, "오늘 일정을 확인해주세요.", 36, Ink.DARK, Weight.BOLD)
        c.text(178, 262, plan.title, 48, Ink.DARK, Weight.BOLD, w = 700)
        c.text(178, 338, info, 36, Ink.GRAY, w = 700)
        c.text(178, 440, "목적지", 36, Ink.GRAY, Weight.BOLD)
        c.text(325, 440, "${dest.name} · ${dest.address}", 36, Ink.DARK, w = 560)
        stop?.let {
            c.text(178, 508, "경유지", 36, Ink.GRAY, Weight.BOLD)
            c.text(325, 508, "${shortName(it.place)} (반려견 쉼터)", 36, Ink.DARK, w = 560)
        }
        c.lottie(178, 734, 320, 212, Lotties.OBJ_CAR, alpha = 0.8f)
        c.lottie(374, 606, 262, 340, Lotties.BORI_SIT)
        // 오른쪽 칸: 경로와 추천 경유지 (규칙 28)
        routePlan(plan, 940, 202, 646, chosenStop(plan))
    } else {
        c.centered(168, "오늘 일정을 확인해주세요.", 36, Ink.DARK, Weight.BOLD)
        c.lottie(219, 371, 258, 171, Lotties.OBJ_CAR, alpha = 0.8f)
        c.lottie(377, 268, 211, 274, Lotties.BORI_SIT)
        c.centered(566, plan.title, 48, Ink.DARK, Weight.BOLD)
        c.centered(640, info, 36, Ink.GRAY)
        // 목적지 · 경유지는 C01-R 여정과 같은 흰 박스에 담는다 (예전 주황 타원 번짐은 앱에서 블러 없이 단색 원으로 보여 뺐다)
        c.card(58, 734, 724, if (stop != null) 176 else 108)
        c.text(98, 766, "목적지", 36, Ink.GRAY, Weight.BOLD)
        c.text(245, 766, "${dest.name} · ${dest.address}", 36, Ink.DARK, w = 500, maxLines = 1)
        stop?.let {
            c.text(98, 834, "경유지", 36, Ink.GRAY, Weight.BOLD)
            c.text(245, 834, "${shortName(it.place)} (반려견 쉼터)", 36, Ink.DARK, w = 500, maxLines = 1)
        }
    }
    footer2(
        "이 여행으로 시작", "다른 여행 보기",
        onPrimary = {
            ui.checked.clear()
            actions?.startTrip()
        },
        onSecondary = { host.toast("폰에 저장된 다른 여행이 없어요") },
    )
}

// ---------------- C03 준비물 ----------------

/** 준비물 카드 안 3D 오브젝트 상자 (카드 왼쪽 위 기준, Figma 값) */
private val ITEM_ART_NARROW = listOf(intArrayOf(122, 30, 108, 140), intArrayOf(94, 30, 164, 140), intArrayOf(76, 57, 180, 99), intArrayOf(66, 37, 220, 125))
private val ITEM_ART_WIDE = listOf(intArrayOf(288, 33, 147, 190), intArrayOf(249, 33, 223, 190), intArrayOf(249, 67, 224, 122), intArrayOf(211, 43, 300, 171))

fun Scr.c03Checklist(plan: TripPlan) {
    c.background(Bg.WARM)
    val items = plan.checklist.take(4)
    val done = ui.checked.count { it < items.size }
    val progress = c.rich(
        Part("${items.size}개 중 ", Ink.SUB, Weight.REGULAR),
        Part("${done}개", Ink.ORANGE_TEXT, Weight.BOLD),
        Part(" 챙겼어요", Ink.SUB, Weight.REGULAR),
        size = 32,
    )
    title(160, "준비물 다 챙기셨나요?", color = Ink.DARK)
    subtitle(237, progress)
    val (cw, ch) = if (wide) 722 to 300 else 352 to 270
    val xs = if (wide) listOf(178, 920) else listOf(58, 430)
    val ys = if (wide) listOf(326, 646) else listOf(330, 620)
    items.forEachIndexed { i, label ->
        val x = xs[i % 2]
        val y = ys[i / 2]
        val on = i in ui.checked
        if (on) c.card(x, y, cw, ch, Ink.ORANGE, 5f) else c.card(x, y, cw, ch, Ink.CHIP_LINE, 2f)
        val art = (if (wide) ITEM_ART_WIDE else ITEM_ART_NARROW)[i]
        c.lottie(x + art[0], y + art[1], art[2], art[3], CHECKLIST_ART[i])
        c.centerIn(x, cw, y + if (wide) 224 else 196, label, 32, Ink.BODY, Weight.BOLD)
        c.check(x + cw - (if (wide) 72 else 68), y + if (wide) 24 else 20, 48, on)
        c.tap(x, y, cw, ch, label, 32f) {
            if (!ui.checked.add(i)) ui.checked.remove(i)
            host.rerender()
        }
    }
    footer2("출발하기", "건너뛰기",
        onPrimary = { actions?.confirmBriefing() },
        onSecondary = { actions?.confirmBriefing() })
}

// ---------------- C04 경로 요약 ----------------

fun Scr.c04Route(plan: TripPlan) {
    c.background(Bg.PLAIN)
    title(160, "경로를 요약했어요")
    dayChips(if (wide) 178 else 280, if (wide) 250 else 238)
    val stop = chosenStop(plan)
    if (wide) {
        routePlan(plan, 178, 384, 392, stop)
        c.lottie(1091, 372, 419, 560, Lotties.BORI_SUNGLASSES, rotation = -5f)
    } else {
        routePlan(plan, 80, 371, 392, stop)
        c.lottie(601, 275, 175, 234, Lotties.BORI_SUNGLASSES, rotation = -5f)
    }
    footer("길 안내 시작", icon = R.drawable.ic_navigate) { actions?.startGuidance() }
}

/**
 * 출발 → 쉬었다 갈 곳 카드 → 도착 (C04, C02-W 오른쪽).
 * x0·y0 = 출발 점의 왼쪽 위. 카드 안 버튼은 카드와 함께 둔다 (규칙 30).
 */
private fun Scr.routePlan(plan: TripPlan, x0: Int, y0: Int, cardH: Int, stop: TripStop?) {
    val cardX = x0 + 56
    val cardY = y0 + 68
    val endY = cardY + cardH + 46
    c.box(x0 + 12, y0 + 14, 4, endY - y0 - 14, Ink.ROUTE_LINE)
    c.routeDot(x0, y0)
    c.text(x0 + 56, y0 - 16, "출발", 28, Ink.GRAY, Weight.MEDIUM)
    c.text(x0 + 146, y0 - 22, "현재 위치", 36, Ink.TITLE, Weight.BOLD)

    c.card(cardX, cardY, 646, cardH, Ink.ORANGE, 4f, fill = Ink.PEACH)
    c.routeDot(x0, y0 + 102, on = stop != null)
    val tx = cardX + 42
    if (stop != null) {
        val recommended = stop.place.id == plan.plannedRest?.place?.id
        c.cardLabel(tx, cardY + 38, if (recommended) "페타 추천 경유지" else "고른 경유지")
        c.text(tx, cardY + 94, shortName(stop.place), 48, Ink.TITLE, Weight.BOLD, w = 560)
        c.text(tx, cardY + 164, blurb(stop.place), 32, Ink.BODY, w = 560)
        val distance = stop.place.routeKm ?: 0.0
        c.text(tx, cardY + 218, "출발 ${hm(eta(stop.place))} 뒤, ${km(distance)}", 32, Ink.ORANGE_TEXT, Weight.BOLD)
    } else {
        c.cardLabel(tx, cardY + 38, "쉬었다 갈 곳")
        c.text(tx, cardY + 94, "쉬지 않고 가요", 48, Ink.TITLE, Weight.BOLD)
        c.text(tx, cardY + 164, "쉴 시간이 되면 한 번 물어볼게요", 32, Ink.BODY, w = 560)
    }
    // 「다른 곳 고르기」와 「쉬지 않고 가기」는 같은 위계 (규칙 15)
    val by = cardY + cardH - 100
    c.button(cardX + 28, by, 289, 72, "다른 곳 고르기", ButtonStyle.MUTED) { pickOtherStop() }
    if (stop != null) {
        c.button(cardX + 329, by, 289, 72, "쉬지 않고 가기", ButtonStyle.MUTED) { actions?.planNoStop() }
    } else if (plan.plannedRest != null) {
        c.button(cardX + 329, by, 289, 72, "추천 경유지로", ButtonStyle.SECONDARY) { actions?.planRecommendedStop() }
    }

    c.routeDot(x0, endY)
    c.text(x0 + 56, endY - 16, "도착", 28, Ink.GRAY, Weight.MEDIUM)
    c.text(x0 + 146, endY - 22, plan.destination.place.name, 36, Ink.TITLE, Weight.BOLD, w = 500)
}

// ---------------- C04-E 경로 탐색 실패 ----------------

fun Scr.c04eRouteFail() {
    c.background(Bg.GRAY)
    title(160, "경로를 찾지 못했어요")
    subtitle(236, "인터넷 연결을 확인해 주세요")
    if (wide) worriedBori(706, 397, 408, 544) else worriedBori(270, 417, 300, 400)
    footer2("다시 찾기", "이전으로",
        onPrimary = { actions?.retryRoute() },
        onSecondary = { actions?.backToSummary() })
}
