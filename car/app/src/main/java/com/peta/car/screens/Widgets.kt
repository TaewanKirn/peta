package com.peta.car.screens

import android.widget.ImageView
import com.peta.car.R
import com.peta.car.place.Place
import com.peta.car.ui.Align
import com.peta.car.ui.ButtonStyle
import com.peta.car.ui.Ink
import com.peta.car.ui.Lotties
import com.peta.car.ui.PanelCanvas
import com.peta.car.ui.Part
import com.peta.car.ui.Weight

// 화면 여러 곳에서 쓰는 조각. 좌표는 Figma 1/3(840) 기준이고, 2/3(1700)은 각 화면에서 따로 넘긴다.

/** 화면 제목 48 Bold. 1/3은 가운데, 2/3은 왼쪽 정렬 (규칙 27) */
fun Scr.title(y: Int, value: CharSequence, size: Int = 48, color: Int = Ink.TITLE, weight: Weight = Weight.BOLD) =
    if (wide) c.text(left, y, value, size, color, weight, w = 1464)
    else c.centered(y, value, size, color, weight, x = 40, w = 760)

/** 제목 아래 부제 32 */
fun Scr.subtitle(y: Int, value: CharSequence, size: Int = 32, color: Int = Ink.SUB, weight: Weight = Weight.REGULAR) =
    if (wide) c.text(left, y, value, size, color, weight, w = 1000, lineHeight = 48)
    else c.centered(y, value, size, color, weight, x = 40, w = 760, lineHeight = 48)

/** 아래 고정 버튼 한 개 (규칙 30) */
fun Scr.footer(label: String, style: ButtonStyle = ButtonStyle.PRIMARY, enabled: Boolean = true, icon: Int? = null, onClick: () -> Unit) {
    if (wide) c.button(178, 1040, 1464, 96, label, style, enabled, icon, onClick = onClick)
    else c.button(58, 952, 724, 96, label, style, enabled, icon, onClick = onClick)
}

/** 아래 고정 버튼 두 개 (주 버튼 + 보조 버튼) */
fun Scr.footer2(
    primary: String, secondary: String,
    primaryEnabled: Boolean = true,
    onPrimary: () -> Unit, onSecondary: () -> Unit,
) {
    if (wide) {
        c.button(178, 1040, 899, 96, primary, ButtonStyle.PRIMARY, primaryEnabled, onClick = onPrimary)
        c.button(1091, 1040, 551, 96, secondary, ButtonStyle.SECONDARY, onClick = onSecondary)
    } else {
        val secondW = maxOf(269, c.measure(secondary, 32, Weight.BOLD) + 84)
        val primaryW = 724 - 14 - secondW
        c.button(58, 952, primaryW, 96, primary, ButtonStyle.PRIMARY, primaryEnabled, onClick = onPrimary)
        c.button(58 + primaryW + 14, 952, secondW, 96, secondary, ButtonStyle.SECONDARY, onClick = onSecondary)
    }
}

/** 흰 카드 (Card/…) */
fun PanelCanvas.card(x: Int, y: Int, w: Int, h: Int, stroke: Int = Ink.CARD_LINE, strokeW: Float = 2f, fill: Int = Ink.WHITE) =
    box(x, y, w, h, fill, 32f, stroke, strokeW)

/** 카드 머리말 28 Bold 주황 */
fun PanelCanvas.cardLabel(x: Int, y: Int, value: String, size: Int = 28) =
    text(x, y, value, size, Ink.ORANGE_TEXT, Weight.BOLD)

/** "12분 거리 | 4.2km" — 앞은 굵게, 뒤는 회색 (규칙 11) */
fun PanelCanvas.distanceText(
    x: Int, y: Int, minutes: Int, distance: Double, size: Int = 28,
    leadColor: Int = Ink.TITLE, leadWeight: Weight = Weight.BOLD, tailSize: Int = size, tailWeight: Weight = Weight.REGULAR,
    suffix: String = "", w: Int = 0,
) = text(x, y, rich(
    Part("${hm(minutes)} 거리", leadColor, leadWeight, size),
    Part(" | ${km(distance)}$suffix", Ink.GRAY, tailWeight, tailSize),
    size = size,
), size, w = w)

/** 1일차 · 2일차 칩 */
fun Scr.dayChips(x: Int, y: Int) {
    val w1 = c.chip(x, y, "1일차", on = ui.tripDay == 1) { ui.tripDay = 1; host.rerender() }
    c.chip(x + w1 + 12, y, "2일차", on = ui.tripDay == 2) { ui.tripDay = 2; host.rerender() }
}

/** 일차 칩 두 개의 폭 (가운데 정렬용) */
fun Scr.dayChipsWidth(): Int =
    listOf(1, 2).sumOf { d ->
        val on = ui.tripDay == d
        c.measure("${d}일차", 32, if (on) Weight.BOLD else Weight.MEDIUM) + (if (on) 28 else 30) * 2
    } + 12

/** 2일차 샘플 일정 [가정]. 모바일 여행 계획에 2일차가 아직 없어 시연 값을 쓴다. 장소가 null이면 집(출발지) */
data class DayStop(val place: String?, val time: String, val what: String)

val DAY2_STOPS = listOf(
    DayStop("숲속 스테이", "11:00", "체크아웃"),
    DayStop("세이지 해안길", "12:30", "산책"),
    DayStop(null, "15:00", "집으로"),
)

/** C09 일정 표용: "11:00" to "숲속 스테이 체크아웃" */
val DAY2_SCHEDULE = DAY2_STOPS.map { it.time to (if (it.place == null) "집으로 출발" else "${it.place} ${it.what}") }

/** 둥근 넘김 버튼 (C01 · C11) */
fun PanelCanvas.pagerButton(x: Int, y: Int, size: Int, next: Boolean, enabled: Boolean, label: String, onClick: () -> Unit) {
    val a = if (enabled) 1f else 0.4f
    box(x, y, size, size, Ink.WHITE, size / 2f, Ink.BUTTON_LINE, 3f, alpha = a)
    val ic = size / 2
    icon(x + (size - ic) / 2, y + (size - ic) / 2, ic, R.drawable.ic_chevron_right, Ink.ORANGE, alpha = a, rotation = if (next) 0f else 180f)
    tap(x, y, size, size, label, size / 2f, enabled, onClick)
}

/** 여정 점 (C04 · C01-R · C02-W). on = 주황 채움, done = 지난 곳(회색) */
fun PanelCanvas.routeDot(x: Int, y: Int, on: Boolean = false, done: Boolean = false) = when {
    on -> box(x, y, 28, 28, Ink.ORANGE, oval = true, stroke = Ink.ORANGE, strokeW = 5f)
    done -> box(x, y, 28, 28, Ink.WHITE, oval = true, stroke = Ink.ROUTE_DONE_DOT, strokeW = 5f)
    else -> box(x, y, 28, 28, Ink.WHITE, oval = true, stroke = Ink.ORANGE, strokeW = 5f)
}

/** 목걸이 + "내리기 전 리드줄을 챙기세요" */
fun Scr.leashReminder(y: Int) {
    if (wide) {
        c.image(left, y - 112, 64, 64, R.drawable.img_collar)
        c.text(left, y, "내리기 전 리드줄을 챙기세요", 34, Ink.TITLE, Weight.BOLD)
    } else {
        c.image(388, y - 74, 64, 64, R.drawable.img_collar)
        c.centered(y, "내리기 전 리드줄을 챙기세요", 34, Ink.TITLE, Weight.BOLD)
    }
}

/**
 * 장소 카드 (검색 · 추천). Figma Sheet: 사진 220 + 이름 · 거리 · 후기 한 줄 · 방문 수.
 * 사진이 없는 곳은 크림 바탕에 3D 집 오브젝트.
 */
fun PanelCanvas.placeSheet(x: Int, y: Int, w: Int, place: Place, selected: Boolean, onClick: () -> Unit) {
    val h = 440
    box(x, y, w, h, Ink.WHITE, 30f).elevation = 8f * scale
    val photo = photoOf(place)
    if (photo != null) {
        image(x, y, w, 220, photo, ImageView.ScaleType.CENTER_CROP, radius = 30f, topRadiusOnly = true).elevation = 8f * scale
    } else {
        // 사진을 받아 오는 동안(또는 못 받았을 때)은 크림 바탕에 3D 집
        box(x, y, w, 220, Ink.PEACH, 30f).elevation = 8f * scale
        lottie(x + w / 2 - 70, y + 40, 140, 140, Lotties.OBJ_HOUSE, play = false).elevation = 8f * scale
        if (place.imageUrl.isNotBlank()) {
            remoteImage(x, y, w, 220, place.imageUrl, radius = 30f, topRadiusOnly = true).elevation = 8f * scale
        }
    }
    // 분류 태그 (사진 위)
    // 태그는 짧은 분류 (식당 · 카페 · 산책로 …). 설명이 문장이면 검색 분류를 쓴다.
    val tagLabel = place.description.takeIf { it.isNotBlank() && it.length <= 5 } ?: place.category
    val tagW = 37 + 8 + measure(tagLabel, 24, Weight.MEDIUM) + 18
    // 사진 위 반투명 바탕: 카드색 80% (라이트 흰색 · 다크 #26201C) — 다크에서 밝은 주황 글자와 대비를 지킨다
    box(x + 18, y + 160, tagW, 46, (Ink.WHITE and 0x00FFFFFF) or 0xCC000000.toInt(), 23f).elevation = 9f * scale
    box(x + 22, y + 165, 37, 37, 0x33F2711C, oval = true).elevation = 9f * scale
    image(x + 30, y + 172, 22, 22, categoryIcon(place)).elevation = 9f * scale
    text(x + 66, y + 165, tagLabel, 24, Ink.ORANGE_TAG, Weight.MEDIUM).elevation = 9f * scale
    val e = 9f * scale
    text(x + 23, y + 232, shortName(place), 36, Ink.TITLE, Weight.BOLD, w = w - 40).elevation = e
    text(x + 23, y + 290, rich(
        Part("${hm(eta(place))} 거리", Ink.ORANGE_TEXT, Weight.BOLD),
        Part(" | ${km(place.routeKm ?: 0.0)}", Ink.GRAY, Weight.REGULAR),
        size = 28,
    ), 28).elevation = e
    if (place.reviewOneLine.isNotBlank()) {
        text(x + 23, y + 338, "“${place.reviewOneLine}”", 28, Ink.SUB, Weight.MEDIUM, w = w - 40).elevation = e
    }
    if (place.petaVisitors > 0) text(x + 23, y + 380, "${place.petaVisitors}명 다녀감", 28, Ink.GRAY).elevation = e
    if (selected) {
        box(x, y, w, h, 0, 30f, Ink.ORANGE, 5f).elevation = 10f * scale
        val check = 56
        box(x + w - check - 16, y + 16, check, check, Ink.ORANGE, 26f).elevation = 10f * scale
        icon(x + w - check - 16 + 14, y + 30, 28, R.drawable.ic_check, Ink.ON_PRIMARY).elevation = 10f * scale
    }
    tap(x, y, w, h, place.name, 30f, onClick = onClick).elevation = 11f * scale
}

/** 칩 여러 개를 한 줄에. 넘치면 가로로 넘긴다 */
fun Scr.chipRow(x: Int, y: Int, w: Int, labels: List<String>, selected: String, onSelect: (String) -> Unit) {
    val widths = labels.map { c.measure(it, 32, if (it == selected) Weight.BOLD else Weight.MEDIUM) + if (it == selected) 56 else 60 }
    val contentW = widths.sum() + 12 * (labels.size - 1)
    val row = if (contentW > w) c.hScroll(x, y, w, 68, contentW + 20) else null
    var cx = if (row != null) 0 else x
    val cy = if (row != null) 0 else y
    labels.forEachIndexed { i, label ->
        val target = row ?: c
        target.chip(cx, cy, label, label == selected) { onSelect(label) }
        cx += widths[i] + 12
    }
}

/** 칩 두 줄 (C01). 각 줄을 양 끝에 맞춘다 */
fun Scr.chipRows(x: Int, y: Int, w: Int, rows: List<List<String>>, selected: String, onSelect: (String) -> Unit) {
    rows.forEachIndexed { r, labels ->
        val widths = labels.map { c.measure(it, 32, if (it == selected) Weight.BOLD else Weight.MEDIUM) + if (it == selected) 56 else 60 }
        val gap = if (labels.size > 1) (w - widths.sum()) / (labels.size - 1) else 0
        var cx = x
        labels.forEachIndexed { i, label ->
            c.chip(cx, y + r * 80, label, label == selected) { onSelect(label) }
            cx += widths[i] + gap.coerceAtLeast(12)
        }
    }
}

/** 불러오는 중: 두리번거리는 보리 (반복 재생) */
fun Scr.loadingBori(x: Int, y: Int, w: Int, h: Int) {
    c.lottie(x, y, w, h, Lotties.BORI_LOADING, loop = true)
}

/** 실패 화면의 걱정하는 보리 + 좌절 선 */
fun Scr.worriedBori(x: Int, y: Int, w: Int, h: Int) {
    c.lottie(x, y, w, h, Lotties.BORI_WORRIED)
    val unit = w / 300f
    c.sadLines(x + (105 * unit).toInt(), y - (12 * unit).toInt(), unit)
}

/** 본문 글자 상자 높이에 맞춘 가운데 정렬 (x~x+w) */
fun PanelCanvas.centerIn(x: Int, w: Int, y: Int, value: CharSequence, size: Int, color: Int, weight: Weight) =
    text(x, y, value, size, color, weight, w = w, align = Align.CENTER)
