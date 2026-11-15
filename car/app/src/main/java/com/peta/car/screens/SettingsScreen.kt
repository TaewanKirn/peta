package com.peta.car.screens

import com.peta.car.R
import com.peta.car.trip.TripPlan
import com.peta.car.ui.Align
import com.peta.car.ui.Bg
import com.peta.car.ui.ButtonStyle
import com.peta.car.ui.Ink
import com.peta.car.ui.Weight

// ---------------- C14 설정 ----------------
// 반려견 정보는 모바일에서 바꾼다. 차량에서는 음성 안내만 켜고 끈다. 주행 중에는 탭이 잠겨 들어올 수 없다.

fun Scr.c14Settings(plan: TripPlan?) {
    c.background(Bg.WARM)
    val pet = plan?.pet
    val rows = listOf<Triple<String, String, () -> Unit>>(
        Triple("반려견 프로필", pet?.let { "${it.name} · ${it.age}살" } ?: "없음") { host.toast("반려견 프로필은 모바일 앱에서 바꿀 수 있어요") },
        Triple("휴식 주기", pet?.let { "${it.restIntervalMinutes}분마다" } ?: "-") { host.toast("휴식 주기는 모바일 앱에서 바꿀 수 있어요") },
        Triple("단골 병원", plan?.regularHospital?.name ?: "없음") { host.toast("단골 병원은 모바일 앱에서 바꿀 수 있어요") },
        Triple("음성 안내", if (s.voiceEnabled) "켜짐" else "꺼짐") { actions?.setVoiceEnabled(!s.voiceEnabled) },
        // 자동(시스템 야간 모드) → 라이트 → 다크 순서로 바뀐다
        Triple("화면 테마", com.peta.car.ui.ThemeMode.label()) { com.peta.car.ui.ThemeMode.next(c.context); host.rerender() },
    )
    val x = left
    val w = c.panelW - 58 - x
    val top = if (wide) 160 else 150
    rows.forEachIndexed { i, (label, value, onClick) ->
        val y = top + i * 136
        c.box(x, y, w, 120, Ink.WHITE, 24f, Ink.CARD_LINE, 2f)
        c.text(x + 32, y + 34, label, 36, Ink.TITLE, Weight.BOLD)
        c.text(x + w - 420, y + 37, value, 32, Ink.SUB, w = 334, align = Align.RIGHT)
        c.icon(x + w - 70, y + 41, 38, R.drawable.ic_chevron_right, Ink.GRAY)
        c.tap(x, y, w, 120, label, 24f, onClick = onClick)
    }
    if (wide) c.text(178, 872, "주행 중에는 바꿀 수 없어요", 28, Ink.GRAY)
    else c.centered(846, "주행 중에는 바꿀 수 없어요", 28, Ink.GRAY)
    footer("앱 초기화", ButtonStyle.DANGER_OUTLINE) {
        actions?.resetApp()
        ui.sampleAccepted = false
        ui.checked.clear()
        ui.rating = 0
        ui.query = ""
        ui.category = null
        ui.selectedId = null
        ui.splashUntil = System.currentTimeMillis() + 1_500
        host.toast("앱을 처음 상태로 되돌렸어요")
        host.goTab(com.peta.car.ui.NavTab.HOME)
    }
}
