package com.peta.car.screens

import com.peta.car.R
import com.peta.car.privacy.Consent
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
        // 동의 화면(C00-P)을 다시 열어 약관을 보거나 동의 · 철회한다
        Triple("개인정보 · 위치정보", Consent.label()) { ui.reviewConsent = true; host.rerender() },
    )
    val x = left
    val w = c.panelW - 58 - x
    val top = if (wide) 160 else 150
    // 여섯 줄이 1/3 아래 버튼(952) 위에 들어가도록 줄 높이 112 · 간격 124 (예전 120 · 136)
    rows.forEachIndexed { i, (label, value, onClick) ->
        val y = top + i * 124
        c.box(x, y, w, 112, Ink.WHITE, 24f, Ink.CARD_LINE, 2f)
        c.text(x + 32, y + 30, label, 36, Ink.TITLE, Weight.BOLD)
        c.text(x + w - 420, y + 33, value, 32, Ink.SUB, w = 334, align = Align.RIGHT)
        c.icon(x + w - 70, y + 37, 38, R.drawable.ic_chevron_right, Ink.GRAY)
        c.tap(x, y, w, 112, label, 24f, onClick = onClick)
    }
    if (wide) c.text(178, 912, "주행 중에는 바꿀 수 없어요", 28, Ink.GRAY)
    else c.centered(898, "주행 중에는 바꿀 수 없어요", 28, Ink.GRAY)
    footer("앱 초기화", ButtonStyle.DANGER_OUTLINE) {
        Consent.set(c.context, Consent.State.UNKNOWN) // 처음 상태 = 동의도 다시 묻는다
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
