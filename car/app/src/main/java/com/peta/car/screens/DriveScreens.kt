package com.peta.car.screens

import com.peta.car.PetaUiState
import com.peta.car.trip.TripPlan
import com.peta.car.trip.withIGa
import com.peta.car.trip.withRo
import com.peta.car.ui.Align
import com.peta.car.ui.Bg
import com.peta.car.ui.ButtonStyle
import com.peta.car.ui.Ink
import com.peta.car.ui.Lotties
import com.peta.car.ui.Weight
import kotlin.math.roundToInt

// 주행 중 화면은 정적이다. 로띠는 첫 프레임에 멈추고(c.motion=false), 닫기·탭은 흐리게 둔다.
// 누를 수 있는 것은 응급과 C05 「경유지 변경/취소」뿐이다 (규칙 13).

/** C05에 보여 줄 숫자: 큰 숫자(휴식까지 분), 달린 분, 진행 비율 */
private data class DriveNumbers(val untilRest: Int, val driven: Int, val ratio: Float)

private fun driveNumbers(s: PetaUiState): DriveNumbers {
    val driven = s.continuousDrivingMinutes.toInt()
    val stop = s.pendingStop?.takeIf { !s.waypointVisited }
    return if (stop != null) {
        // 쉬었다 갈 곳까지 남은 거리로 시간을 어림한다
        val remain = s.legRemainKm ?: stop.place.routeKm ?: 0.0
        val until = if (remain <= 0.0) 0 else etaForKm(remain)
        DriveNumbers(until, driven, driven / (driven + until).coerceAtLeast(1).toFloat())
    } else {
        val limit = s.restAfterMinutes.toInt().coerceAtLeast(1)
        DriveNumbers((limit - driven).coerceAtLeast(0), driven, (driven / limit.toFloat()).coerceIn(0f, 1f))
    }
}

// ---------------- C05 주행 중 ----------------

fun Scr.c05Driving(plan: TripPlan) {
    c.background(Bg.WARM)
    val stop = s.pendingStop?.takeIf { !s.waypointVisited }
    val n = driveNumbers(s)
    val barX = left + if (wide) 0 else 32
    val barW = if (wide) 1464 else 660
    val barY = if (wide) 470 else 540

    if (wide) c.text(178, 160, "${plan.pet.name} 휴식까지", 36, Ink.SUB, Weight.MEDIUM)
    else c.centered(156, "${plan.pet.name} 휴식까지", 36, Ink.SUB, Weight.MEDIUM)
    val big = if (wide) c.text(178, 200, "${n.untilRest}분", 112, Ink.ORANGE_DEEP, Weight.BOLD)
    else c.centered(196, "${n.untilRest}분", 112, Ink.ORANGE_DEEP, Weight.BOLD)

    c.box(barX, barY, barW, 16, Ink.TRACK, 8f)
    val fill = c.box(barX, barY, barW, 16, Ink.ORANGE, 8f)
    c.box(barX + barW - 22, barY - 14, 44, 44, Ink.WHITE, oval = true, stroke = Ink.ORANGE, strokeW = 5f)
    // 강아지는 막대 위를 처음부터 따라 달린다. 큰 숫자를 가리지 않게 작게 (2/3 198 → 128, 1/3 132 → 100)
    val (runW, runH) = if (wide) 128 to 136 else 100 to 106
    val run = c.lottie(barX - runW / 2, barY - runH + 10, runW, runH, Lotties.BORI_RUN)
    val drivenText = c.text(barX, barY + 34, "${n.driven}분 달렸어요", 32, Ink.BODY, Weight.MEDIUM)
    c.text(barX + barW - 300, barY + 34, if (stop != null) "쉼터" else "휴식", 32, Ink.SUB, Weight.MEDIUM, w = 300, align = Align.RIGHT)

    fun apply(v: DriveNumbers) {
        big.text = "${v.untilRest}분"
        drivenText.text = "${v.driven}분 달렸어요"
        val filled = (barW * v.ratio).roundToInt().coerceIn(16, barW)
        if (fill.layoutParams.width != c.px(filled)) fill.layoutParams = fill.layoutParams.apply { width = c.px(filled) }
        // 진행이 끝에 가까워도 보리가 패널 오른쪽 밖으로 잘리지 않게 오른쪽 여백 24를 남긴다
        val maxRunAt = c.panelW - 24 - barX - runW / 2 + 13
        val runAt = filled.coerceAtMost(maxRunAt)
        run.translationX = c.px(runAt - 13).toFloat()
    }
    apply(n)
    host.live { apply(driveNumbers(it)) }

    // 쉬었다 갈 곳 카드 (경유지가 없으면 목적지)
    val cardY = if (wide) 596 else 668
    val cardW = if (wide) 1464 else 724
    val tx = left + if (wide) 40 else 32
    val (labelSize, nameSize, keySize, valueSize) = if (wide) listOf(32, 64, 32, 48) else listOf(28, 48, 28, 36)
    val dy = if (wide) intArrayOf(40, 92, 196, 244, 428) else intArrayOf(30, 78, 164, 204, 282)
    val hospital = s.emergencyPlace
    if (hospital != null) {
        // 응급 길 안내 중: 내비가 향하는 병원을 보여 준다
        c.card(left, cardY, cardW, if (wide) 420 else 300, Ink.PINK_STROKE, 3f)
        c.cardLabel(tx, cardY + dy[0], "응급 병원으로 안내 중", labelSize)
        c.text(tx, cardY + dy[1], hospital.name, nameSize, Ink.TITLE, Weight.BOLD, w = cardW - 80, maxLines = 1)
        c.text(tx, cardY + dy[2], "남은 거리", keySize, Ink.GRAY, Weight.MEDIUM)
        val hKm = hospital.routeKm
        val hText = if (hKm != null) "${km(hKm)} | 약 ${hm(hospital.routeMinutes ?: etaForKm(hKm))}" else "계산 중"
        c.text(tx, cardY + dy[3], hText, valueSize, Ink.BODY, Weight.MEDIUM)
    } else if (stop != null) {
        c.card(left, cardY, cardW, if (wide) 540 else 386)
        c.cardLabel(tx, cardY + dy[0], "페타 추천 경유지", labelSize)
        c.text(tx, cardY + dy[1], shortName(stop.place), nameSize, Ink.TITLE, Weight.BOLD, w = cardW - 80)
        // 남은 거리 · 시간과 반려견 쉼터 위치를 두 칸으로
        val col2 = tx + if (wide) 640 else 340
        c.text(tx, cardY + dy[2], "남은 거리", keySize, Ink.GRAY, Weight.MEDIUM)
        val remain = c.text(tx, cardY + dy[3], stopRemainText(s), valueSize, Ink.BODY, Weight.MEDIUM, w = col2 - tx - 24, maxLines = 1)
        host.live { remain.text = stopRemainText(it) }
        val spot = stop.place.shelterSpot
        if (spot.isNotBlank()) {
            c.text(col2, cardY + dy[2], "반려견 쉼터 위치", keySize, Ink.GRAY, Weight.MEDIUM)
            c.text(col2, cardY + dy[3], spot, valueSize, Ink.BODY, Weight.MEDIUM, w = tx + cardW - 80 - col2, maxLines = 1)
        } else {
            c.text(col2, cardY + dy[2], blurb(stop.place), keySize, Ink.GRAY, Weight.MEDIUM, w = tx + cardW - 80 - col2)
        }
        // 주행 중에도 누를 수 있는 유일한 본문 버튼 → 음성 질문(C05-V)으로 이어진다
        c.button(tx, cardY + dy[4], cardW - 80, 72, "경유지 변경/취소", ButtonStyle.MUTED) { actions?.askStopChange() }
    } else {
        c.card(left, cardY, cardW, if (wide) 420 else 300)
        c.cardLabel(tx, cardY + dy[0], "목적지", labelSize)
        c.text(tx, cardY + dy[1], plan.destination.place.name, nameSize, Ink.TITLE, Weight.BOLD, w = cardW - 80)
        c.text(tx, cardY + dy[2], "남은 거리", keySize, Ink.GRAY, Weight.MEDIUM)
        val remain = c.text(tx, cardY + dy[3], remainText(s), valueSize, Ink.BODY, Weight.MEDIUM)
        host.live { remain.text = remainText(it) }
    }
}

/** 경유지까지 "59km | 약 46분" */
private fun stopRemainText(s: PetaUiState): String {
    val stop = s.pendingStop ?: return "계산 중"
    val remain = s.legRemainKm ?: stop.place.routeKm ?: return "계산 중"
    // 경로 시간이 있으면 남은 거리 비율로 줄인다 (59.8km · 46분 경로면 30km 남았을 때 약 23분)
    val total = stop.place.routeKm
    val minutes = stop.place.routeMinutes?.takeIf { total != null && total > 0 }?.let { (it * remain / total!!).roundToInt().coerceAtLeast(1) }
        ?: etaForKm(remain)
    return "${km(remain)} | 약 ${hm(minutes)}"
}

private fun remainText(s: PetaUiState): String {
    val remain = s.legRemainKm ?: return "계산 중"
    return "${km(remain)} | 약 ${hm(s.remainMinutes?.takeIf { it > 0 } ?: etaForKm(remain))}"
}

// ---------------- C05-V 쉬었다 갈 곳 변경 · 음성 질문 ----------------

fun Scr.c05vStopVoice() {
    c.background(Bg.WARM)
    val q = s.stopQuestion ?: return
    val stop = shortName(q.stop.place)
    val bottom = q.reply ?: "답이 없으면 ${stop.withRo()} 그대로 갈게요"
    if (wide) {
        c.text(178, 160, "다른 곳으로 바꿀까요,\n쉬지 않고 갈까요?", 48, Ink.TITLE, Weight.BOLD, lineHeight = 72)
        c.mic(178, 457, 231)
        c.text(449, 494, "“바꿔 줘” 또는\n“쉬지 않고 가”", 52, Ink.ORANGE_DEEP, Weight.BOLD, lineHeight = 78)
        c.text(178, 728, bottom, 44, if (q.reply != null) Ink.ORANGE_TEXT else Ink.BUTTON_TEXT,
            if (q.reply != null) Weight.BOLD else Weight.REGULAR, w = 880)
        c.lottie(1065, 372, 470, 560, Lotties.BORI_LISTENING)
    } else {
        c.centered(160, "다른 곳으로 바꿀까요,\n쉬지 않고 갈까요?", 48, Ink.TITLE, Weight.BOLD, lineHeight = 72)
        c.mic(112, 461, 231)
        c.text(92, 702, "“바꿔 줘” 또는\n“쉬지 않고 가”", 32, Ink.ORANGE_DEEP, Weight.BOLD, w = 271, align = Align.CENTER)
        c.lottie(430, 471, 286, 340, Lotties.BORI_LISTENING)
        c.centered(868, bottom, 32, if (q.reply != null) Ink.ORANGE_TEXT else Ink.SUB,
            if (q.reply != null) Weight.BOLD else Weight.REGULAR, x = 40, w = 760)
    }
}

// ---------------- C06 휴식 제안 (경유지가 없을 때만) ----------------

fun Scr.c06Rest(plan: TripPlan) {
    c.background(Bg.WARM)
    val p = s.restPrompt ?: return
    val stop = shortName(p.stop.place)
    val prompt = when (p.answer) {
        true -> "좋아요!\n${stop.withRo()} 안내할게요"
        false -> "알겠어요.\n계속 갈게요"
        null -> "“응” 또는 “아니”로\n답해 주세요"
    }
    val driven = "${p.drivenMinutes}분 달렸어요"
    if (wide) {
        c.text(178, 160, "${plan.pet.name.withIGa()} 쉴 시간이에요", 36, Ink.SUB, Weight.MEDIUM)
        c.text(178, 210, "${stop}에서 쉬어갈까요?", 48, Ink.TITLE, Weight.BOLD, w = 1200)
        c.box(178, 430, 1464, 16, Ink.ORANGE, 8f)
        c.box(1620, 416, 44, 44, Ink.ORANGE, oval = true, stroke = Ink.WHITE, strokeW = 5f)
        c.lottie(1482, 228, 176, 210, Lotties.BORI_LISTENING)
        c.text(178, 462, driven, 32, Ink.BODY, Weight.MEDIUM)
        c.mic(178, 571, 210)
        c.text(428, 600, prompt.replace("\n", " "), 56, Ink.ORANGE_DEEP, Weight.BOLD, w = 1200, maxLines = 2, lineHeight = 84)
        c.box(178, 843, 1464, 293, Ink.WHITE, 23f, Ink.CARD_LINE, 1f)
        c.cardLabel(218, 883, "가까운 쉴 곳", 32)
        c.text(218, 935, stop, 64, Ink.TITLE, Weight.BOLD, w = 1380)
        c.text(218, 1032, "${hm(p.minutesAway)} 거리 | ${km(p.kmAhead)} 남음", 44, Ink.ORANGE_TEXT, Weight.BOLD)
    } else {
        c.centered(156, "${plan.pet.name.withIGa()} 쉴 시간이에요", 36, Ink.SUB, Weight.MEDIUM)
        c.centered(206, "${stop}에서 쉬어갈까요?", 48, Ink.TITLE, Weight.BOLD, x = 30, w = 780)
        c.mic(58, 318, 210)
        c.text(300, 357, prompt, 44, Ink.ORANGE_DEEP, Weight.BOLD, w = 500, lineHeight = 66)
        c.box(90, 650, 660, 16, Ink.ORANGE, 8f)
        c.box(728, 636, 44, 44, Ink.ORANGE, oval = true, stroke = Ink.WHITE, strokeW = 5f)
        c.lottie(634, 506, 126, 150, Lotties.BORI_LISTENING)
        c.text(90, 684, driven, 32, Ink.BODY, Weight.MEDIUM)
        c.card(58, 770, 724, 232)
        c.cardLabel(90, 800, "가까운 쉴 곳")
        c.text(90, 848, stop, 48, Ink.TITLE, Weight.BOLD, w = 660)
        c.text(90, 924, "${hm(p.minutesAway)} 거리 | ${km(p.kmAhead)} 남음", 36, Ink.ORANGE_TEXT, Weight.BOLD)
    }
}

// ---------------- C06-E 음성 인식 실패 ----------------

fun Scr.c06eRestRetry() {
    c.background(Bg.GRAY)
    title(160, "잘 못 들었어요")
    subtitle(236, "“응” 또는 “아니”로 다시 말해 주세요\n답이 없으면 계속 안내할게요")
    if (wide) c.lottie(675, 478, 470, 560, Lotties.BORI_LISTENING)
    else c.lottie(235, 490, 370, 440, Lotties.BORI_LISTENING)
}
