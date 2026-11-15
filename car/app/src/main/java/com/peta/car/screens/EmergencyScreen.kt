package com.peta.car.screens

import com.peta.car.place.Place
import com.peta.car.place.PlaceDistance
import com.peta.car.trip.TripPlan
import com.peta.car.ui.Bg
import com.peta.car.ui.ButtonStyle
import com.peta.car.ui.Ink
import com.peta.car.ui.Lotties
import com.peta.car.ui.Part
import com.peta.car.ui.Weight

// ---------------- C11 응급 ----------------
// 언제든(주행 중 포함) 헤더·세로 메뉴의 응급으로 연다. 가까운 병원을 한 장씩 넘겨 보고,
// 단골 병원은 아래 연분홍 박스에 고정한다 (규칙 24). 진료 시간은 데이터가 없어 전화로 확인하도록 안내한다 (규칙 23).

fun Scr.c11Emergency(plan: TripPlan) {
    c.background(Bg.PLAIN)
    val list = s.hospitals
    title(if (wide) 160 else 180, if (list.isEmpty()) "병원을 찾고 있어요" else "가까운 병원을 찾았어요")
    subtitle(if (wide) 236 else 256, "출발 전에 전화로 진료 중인지 확인하세요")
    if (list.isEmpty()) {
        // 병원을 받아 오는 동안 두리번거리는 보리
        if (wide) loadingBori(c.panelW / 2 + 60 - 150, 380, 300, 400) else loadingBori(c.panelW / 2 - 105, 400, 210, 280)
    }
    if (list.isNotEmpty()) {
        val index = ui.hospitalIndex.coerceIn(0, list.lastIndex)
        if (wide) {
            // 2/3: 두 장을 나란히
            hospitalCard(178, 346, 512, list[index], nearest = index == 0)
            list.getOrNull(index + 1)?.let { hospitalCard(920, 346, 512, it, nearest = false) }
            pager(709, 922, index, list.size, step = 2)
        } else {
            hospitalCard(58, 362, 408, list[index], nearest = index == 0)
            // 다음 병원 카드가 오른쪽에 살짝 보인다 (옆으로 넘김)
            list.getOrNull(index + 1)?.let { c.card(798, 362, 724, 408, Ink.CARD_LINE, 2f) }
            pager(219, 830, index, list.size, step = 1)
        }
    }
    (s.regularHospital?.takeIf { it.id == plan.regularHospital?.id } ?: plan.regularHospital)?.let { regular ->
        val x0 = if (wide) 120 else 0
        val w = c.panelW - x0
        val y0 = if (wide) 1066 else 970
        c.box(x0, y0, w, 118, Ink.PINK_BOX)
        c.box(x0, y0, w, 2, Ink.PINK_LINE)
        val bx = right - 168
        // 길 안내는 전화 왼쪽. 위 병원 카드와 같은 빨간 버튼이다.
        val nx = bx - 16 - 168
        c.text(left, y0 + 16, "단골 병원 | ${regular.name}", 28, Ink.PINK_INK, w = nx - 16 - left)
        val minutes = regular.routeMinutes ?: etaForKm(regular.routeKm ?: 0.0)
        c.text(left, y0 + 54, "${hm(minutes)} 거리", 36, Ink.PINK_INK_DARK, Weight.BOLD)
        c.button(nx, y0 + 21, 168, 76, "길 안내", ButtonStyle.DANGER) {
            actions?.routeTo(regular)
            ui.emergency = false
            host.rerender()
        }
        c.box(bx, y0 + 21, 168, 76, Ink.WHITE, 38f, Ink.PINK_BUTTON, 3f)
        c.centerIn(bx, 168, y0 + 36, "전화", 32, Ink.BUTTON_TEXT, Weight.BOLD)
        c.tap(bx, y0 + 21, 168, 76, "단골 병원 전화", 38f) { call(regular) }
    }
}

/** 번호가 있으면 연결된 폰의 전화 앱으로 넘기고, 없으면 안내만 한다. */
private fun Scr.call(place: Place) =
    if (place.phone.isBlank()) host.toast("이 병원은 전화번호 정보가 없어요") else host.dial(place.phone)

private fun Scr.hospitalCard(x: Int, y: Int, h: Int, item: PlaceDistance, nearest: Boolean) {
    if (nearest) c.card(x, y, 724, h, Ink.PINK_STROKE, 3f) else c.card(x, y, 724, h)
    val top = y + if (wide) 111 else 58
    c.lottie(x + 36, top, 160, 140, Lotties.OBJ_HOSPITAL)
    c.text(x + 224, top + 8, item.place.name, 48, Ink.TITLE, Weight.BOLD, w = 470)
    val minutes = item.place.routeMinutes ?: etaForKm(item.km)
    c.text(x + 224, top + 86, c.rich(
        Part("${hm(minutes)} 거리", Ink.TITLE, Weight.BOLD, 32),
        Part(" | ${km(item.km)}", Ink.GRAY, Weight.REGULAR, 28),
        size = 32,
    ), 32)
    val by = top + if (wide) 251 else 198
    c.button(x + 36, by, 320, 104, "길 안내", ButtonStyle.DANGER) {
        actions?.routeTo(item.place)
        ui.emergency = false
        host.rerender()
    }
    c.button(x + 368, by, 320, 104, "전화", ButtonStyle.SECONDARY) { call(item.place) }
}

private fun Scr.pager(x: Int, y: Int, index: Int, total: Int, step: Int) {
    val pages = (total + step - 1) / step
    val page = index / step
    c.pagerButton(x, y, 80, next = false, enabled = page > 0, label = "이전 병원") {
        ui.hospitalIndex = (index - step).coerceAtLeast(0)
        host.rerender()
    }
    c.centerIn(x + 80, 242, y + 17, c.rich(
        Part("가까운 순 ", Ink.SUB, Weight.REGULAR),
        Part("${page + 1}", Ink.TITLE, Weight.BOLD),
        Part(" / $pages", Ink.SUB, Weight.REGULAR),
        size = 32,
    ), 32, Ink.SUB, Weight.REGULAR)
    c.pagerButton(x + 322, y, 80, next = true, enabled = page < pages - 1, label = "다음 병원") {
        ui.hospitalIndex = (index + step).coerceAtMost(total - 1)
        host.rerender()
    }
}
