package com.peta.car.screens

import android.view.View
import com.peta.car.PetaConfig
import com.peta.car.PetaUiState
import com.peta.car.R
import com.peta.car.trip.TripPlan
import com.peta.car.trip.withEunNeun
import com.peta.car.ui.Align
import com.peta.car.ui.Bg
import com.peta.car.ui.ButtonStyle
import com.peta.car.ui.Ink
import com.peta.car.ui.Lotties
import com.peta.car.ui.Part
import com.peta.car.ui.Weight

/** 목적지 일정. 모바일에서 아직 오지 않아 스타일프레임 시안 값을 쓴다. */
private val SAMPLE_SCHEDULE = listOf("16:00" to "체크인", "16:30" to "운동장에서 산책", "18:30" to "함께 저녁 먹기")

/** 쉬고 나서 목적지까지 "1시간 40분 | 148km 남음" */
private fun Scr.toDestinationText(plan: TripPlan): String {
    val distance = plan.destination.place.routeKm ?: 0.0
    // 경유지에 있는 동안 내비의 남은 시간(remainMinutes)은 지금 경로(경유지까지) 값이라 쓰지 않는다
    val minutes = plan.destination.place.routeMinutes ?: etaForKm(distance)
    return "${hm(minutes)} | ${km(distance)} 남음"
}

// ---------------- C07 쉬었다 갈 곳 도착 ----------------

fun Scr.c07ArrivedStop(plan: TripPlan) {
    c.background(Bg.PLAIN)
    val stop = s.currentStop ?: return
    val name = shortName(stop.place)
    title(160, twoTone(name, "에 도착했어요", 48))
    subtitle(240, blurb(stop.place))
    if (wide) {
        c.card(178, 334, 722, 658)
        c.image(218, 378, 64, 64, R.drawable.img_collar)
        c.text(218, 462, "내리기 전 리드줄을 챙기세요", 44, Ink.TITLE, Weight.BOLD)
        c.box(218, 562, 642, 2, Ink.DIVIDER)
        c.cardLabel(218, 598, "다음 목적지", 32)
        c.text(218, 650, plan.destination.place.name, 64, Ink.TITLE, Weight.BOLD, w = 640)
        c.text(218, 746, toDestinationText(plan), 40, Ink.BUTTON_TEXT)
        c.lottie(980, 408, 640, 488, Lotties.BORI_REST_PARK)
    } else {
        c.lottie(190, 360, 460, 350, Lotties.BORI_REST_PARK)
        c.image(388, 752, 64, 64, R.drawable.img_collar)
        c.centered(826, "내리기 전 리드줄을 챙기세요", 34, Ink.TITLE, Weight.BOLD)
    }
    // 차량 「펫 케어 모드」는 Pleos 공개 SDK(Vehicle 2.3.2)에 API가 없어 켤 수 없다. 앱 안 휴식(C08)만 시작한다.
    footer("휴식 시작") { actions?.startRest() }
}

// ---------------- C08 휴식 중 ----------------

fun Scr.c08Resting(plan: TripPlan) {
    c.background(Bg.WARM)
    val stop = s.currentStop ?: return
    val name = shortName(stop.place)
    fun stayed(st: PetaUiState) = c.rich(
        Part("${st.stayedMinutes}분", Ink.ORANGE_DEEP, Weight.BOLD),
        Part("째 쉬고 있어요", Ink.TITLE, Weight.BOLD),
        size = 48,
    )
    fun delay(st: PetaUiState): CharSequence = if (st.delayMinutes > 0) c.rich(
        Part("계획보다 ", Ink.SUB, Weight.REGULAR),
        Part("${st.delayMinutes}분", Ink.ORANGE_TEXT, Weight.BOLD),
        Part(" 더 쉬었어요", Ink.SUB, Weight.REGULAR),
        size = 32,
    ) else "${stop.plannedStayMinutes}분 쉬기로 했어요"

    val stayedView = title(206, stayed(s))
    val delayView = subtitle(290, delay(s))
    if (wide) {
        c.text(178, 160, "${name}에서", 36, Ink.SUB, Weight.MEDIUM)
        stayedView.translationY = c.px(4).toFloat()
        c.lottie(980, 408, 640, 488, Lotties.BORI_REST_PARK)
        c.card(178, 388, 724, 748)
        c.cardLabel(218, 432, "다음 목적지", 32)
        c.text(218, 488, plan.destination.place.name, 72, Ink.TITLE, Weight.BOLD, w = 660)
        c.text(218, 600, toDestinationText(plan), 44, Ink.BUTTON_TEXT)
        c.box(218, 700, 644, 2, Ink.CARD_LINE)
        c.cardLabel(218, 736, "실내 공조", 32)
        val climate = c.text(218, 792, climateText(s), 40, Ink.BUTTON_TEXT, Weight.MEDIUM, w = 644)
        host.live { climate.text = climateText(it) }
        c.text(218, 1046, "출발하면 길 안내를 이어가요", 32, Ink.GRAY)
    } else {
        c.centered(156, "${name}에서", 36, Ink.SUB, Weight.MEDIUM)
        c.lottie(200, 384, 440, 335, Lotties.BORI_REST_PARK)
        val climate = c.centered(728, climateText(s), 28, Ink.BUTTON_TEXT, Weight.MEDIUM)
        host.live { climate.text = climateText(it) }
        c.card(58, 790, 724, 222)
        c.cardLabel(90, 820, "다음 목적지")
        c.text(90, 864, plan.destination.place.name, 48, Ink.TITLE, Weight.BOLD, w = 330)
        c.text(400, 880, toDestinationText(plan), 32, Ink.GRAY, w = 350, align = Align.RIGHT)
        c.text(90, 944, "출발하면 길 안내를 이어가요", 28, Ink.GRAY)
    }
    host.live { st ->
        stayedView.text = stayed(st)
        delayView.text = delay(st)
    }
}

/** C08 공조 한 줄 (펫 케어 공조 결과) */
private fun climateText(st: PetaUiState): String {
    val t = PetaConfig.PET_CARE_CELSIUS.toInt()
    return when (st.petClimate) {
        "on" -> "에어컨을 켜고 ${t}°C로 맞췄어요"
        "pending" -> "실내를 ${t}°C로 맞추고 있어요"
        "failed" -> "공조를 켜지 못했어요"
        else -> "공조는 그대로예요"
    }
}

// ---------------- C09 목적지 도착 ----------------

fun Scr.c09ArrivedDest(plan: TripPlan) {
    c.background(Bg.PLAIN)
    val name = (s.currentStop ?: plan.destination).place.name
    title(160, "${name}에 도착했어요")
    if (wide) {
        c.image(178, 278, 64, 64, R.drawable.img_collar)
        c.text(178, 390, "내리기 전 리드줄을 챙기세요", 34, Ink.TITLE, Weight.BOLD)
        c.card(178, 489, 724, 647)
        c.cardLabel(218, 533, "여행 일정", 32)
        val schedule = if (ui.tripDay == 2) DAY2_SCHEDULE else SAMPLE_SCHEDULE
        schedule.forEachIndexed { i, (time, what) ->
            c.text(218, 613 + i * 88, time, 44, Ink.BODY, Weight.BOLD)
            c.text(398, 613 + i * 88, what, 44, Ink.BODY, w = 480)
        }
        dayChips(400, 1032)
        c.lottie(1085, 372, 431, 560, Lotties.BORI_JUMP)
    } else {
        c.lottie(297, 270, 246, 320, Lotties.BORI_JUMP)
        c.image(388, 584, 64, 64, R.drawable.img_collar)
        c.centered(694, "내리기 전 리드줄을 챙기세요", 34, Ink.TITLE, Weight.BOLD)
        c.card(58, 774, 724, 294)
        c.cardLabel(90, 812, "여행 일정")
        dayChips(470, 794)
        val schedule = if (ui.tripDay == 2) DAY2_SCHEDULE else SAMPLE_SCHEDULE
        schedule.forEachIndexed { i, (time, what) ->
            c.text(90, 884 + i * 56, time, 32, Ink.BODY, Weight.BOLD)
            c.text(208, 884 + i * 56, what, 32, Ink.BODY, w = 540)
        }
    }
}

// ---------------- C10 여행 후기 (+ C10-S 저장 확인 팝업) ----------------

fun Scr.c10Review() {
    c.background(Bg.PLAIN)
    val saved = ui.savedReviewPlace
    val place = saved ?: s.pendingReview?.placeName ?: return
    val rating = if (saved != null) ui.savedRating else ui.rating
    // 질문은 여행 전체가 아니라 장소 이름으로 (규칙 19)
    title(160, "${place.withEunNeun()} 어땠나요?")
    val cardX = left
    val cardY = if (wide) 278 else 471
    val cardH = if (wide) 648 else 330
    if (wide) c.lottie(1095, 386, 410, 533, Lotties.BORI_SIT) else c.lottie(335, 270, 171, 222, Lotties.BORI_SIT)
    c.card(cardX, cardY, 724, cardH)
    val boneY = cardY + if (wide) 213 else 64
    (0 until 5).forEach { i ->
        val x = cardX + 36 + i * 134
        val score = i + 1
        if (score <= rating) {
            // 주황 뼈다귀, 30도 기울인 모양 (규칙 20). 방금 누른 것만 한 번 튄다.
            c.lottie(x, boneY, 116, 83, Lotties.OBJ_BONE_ORANGE, loop = false, play = i == ui.bounceBone && c.motion)
        } else {
            // 주황 로띠(116×83, 가운데 boneY + 41, 오른쪽 위로 약 23°)와 같은 자리 · 같은 방향 · 같은 크기로 맞춘다
            c.image(x + 2, boneY + 11, 112, 61, R.drawable.img_bone_off, rotation = -23f)
        }
        if (saved == null) c.tap(x - 6, boneY - 10, 128, 110, "뼈다귀 ${score}개", 40f) {
            ui.rating = score
            ui.bounceBone = i
            host.rerender()
        }
    }
    ui.bounceBone = -1
    val labelY = cardY + if (wide) 333 else 184
    if (rating > 0) {
        c.centerIn(cardX, 724, labelY, RATING_WORDS[rating - 1], 41, Ink.ORANGE_TEXT, Weight.BOLD)
        c.centerIn(cardX, 724, labelY + 59, "${rating}점 / 5점", 32, Ink.GRAY, Weight.REGULAR)
    } else {
        c.centerIn(cardX, 724, labelY + 20, "뼈다귀를 눌러 평가해 주세요", 32, Ink.GRAY, Weight.MEDIUM)
    }
    if (wide) c.text(178, 958, "자세한 후기는 모바일에서 이어 쓸 수 있어요", 32, Ink.SUB)
    else c.centered(854, "자세한 후기는 모바일에서 이어 쓸 수 있어요", 32, Ink.SUB)

    footer2(
        "저장하기", "모바일에서 쓰기", primaryEnabled = rating > 0,
        onPrimary = {
            if (ui.rating > 0) {
                actions?.submitReview(ui.rating)
                ui.savedReviewPlace = place
                ui.savedRating = ui.rating
                ui.rating = 0
                host.rerender()
            }
        },
        onSecondary = {
            if (ui.rating > 0) actions?.submitReview(ui.rating) else actions?.dismissReview()
            ui.rating = 0
            host.toast("자세한 후기는 모바일 앱에서 이어 쓸 수 있어요")
        },
    )
    if (saved != null) host.overlay { savedPopup() }
}

/** C10-S 후기 저장 확인 팝업 (헤더·탭까지 덮는다) */
private fun Scr.savedPopup() {
    c.box(0, 0, c.panelW, 1184, Ink.DIM).apply {
        isClickable = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    val px = if (wide) 530 else 100
    c.box(px, 282, 640, 620, Ink.WHITE, 40f)
    c.lottie(px + 241, 349, 158, 204, Lotties.BORI_THUMBS_UP)
    c.centerIn(px, 640, 594, "후기를 저장했어요", 48, Ink.TITLE, Weight.BOLD)
    c.centerIn(px, 640, 674, "다음 여행 추천에 반영할게요", 32, Ink.SUB, Weight.REGULAR)
    c.button(px + 40, 766, 560, 96, "확인", ButtonStyle.PRIMARY) {
        ui.savedReviewPlace = null
        ui.savedRating = 0
        host.rerender()
    }
}
