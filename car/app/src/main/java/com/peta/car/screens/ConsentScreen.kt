package com.peta.car.screens

import com.peta.car.R
import com.peta.car.privacy.Consent
import com.peta.car.ui.Align
import com.peta.car.ui.Bg
import com.peta.car.ui.ButtonStyle
import com.peta.car.ui.Ink
import com.peta.car.ui.Lotties
import com.peta.car.ui.Weight

// ---------------- C00-P 개인정보 · 위치정보 동의 (Figma 「개인정보 · 위치정보 동의」 2026-11-15) ----------------
// 첫 실행 때 스플래시 다음 · 홈 전, 주차(P)일 때만 연다(주행 중이면 미룬다). 설정(C14)에서 다시 열어 철회할 수 있다.
// 차 화면엔 세 줄 요약만, 전문은 QR로 폰에서 읽는다. 동의하지 않아도 예시 추천 · 낱말 검색은 쓸 수 있다.

private val CONSENT_ITEMS = listOf("[필수] 위치정보 이용 동의", "[필수] 개인정보 수집 · 이용 동의")
private const val QR_WHITE = 0xFFFFFFFF.toInt() // 다크에서도 QR은 흰 바탕이어야 읽힌다

fun Scr.c00pConsent() {
    c.background(Bg.PLAIN)
    val reviewing = ui.reviewConsent && Consent.state == Consent.State.AGREED
    val agree = {
        Consent.set(c.context, Consent.State.AGREED)
        actions?.refreshNearby() // 이제 위치로 주변 장소 · 병원을 받는다
        closeConsent()
    }
    val decline = {
        Consent.set(c.context, Consent.State.DECLINED)
        host.toast(if (reviewing) "동의를 철회했어요. 위치 없이 예시 추천으로 보여 드릴게요" else "위치 없이 예시 추천으로 보여 드릴게요")
        closeConsent()
    }
    val note = "동의하지 않아도 예시 추천과 직접 검색은 쓸 수 있어요\n설정에서 언제든 다시 보거나 철회할 수 있어요"
    if (wide) {
        c.lottie(96, 110, 260, 309, Lotties.BORI_LISTENING)
        c.text(96, 470, "시작하기 전에\n확인해 주세요", 56, Ink.TITLE, Weight.BOLD, w = 700, lineHeight = 76)
        c.text(96, 640, "반려견과 갈 수 있는 곳과 가까운 동물병원을 찾을 때\n차의 현재 위치를 써요", 30, Ink.SUB, w = 720, lineHeight = 46)
        c.text(96, 1020, note, 24, Ink.GRAY, w = 720, lineHeight = 36)
        consentSummary(880, 96, 724)
        consentRows(880, 452, 724, withLink = false)
        // 2/3는 QR을 화면에 바로 둔다
        c.box(880, 636, 724, 276, Ink.WHITE, 24f, Ink.CARD_LINE, 2f)
        c.box(912, 668, 212, 212, QR_WHITE, 12f)
        c.image(912, 668, 212, 212, R.drawable.img_qr_privacy, radius = 12f)
        c.text(1156, 692, "약관 전문은 폰으로 확인하세요", 30, Ink.TITLE, Weight.BOLD, w = 416)
        c.text(1156, 748, "카메라로 QR을 비추면\n위치정보 이용약관과\n개인정보 처리방침이 열려요", 26, Ink.SUB, w = 416, lineHeight = 40)
        consentButtons(880, 968, 300, 408, reviewing, agree, decline)
    } else {
        c.lottie(c.panelW / 2 - 75, 60, 150, 178, Lotties.BORI_LISTENING)
        c.centered(262, "시작하기 전에 확인해 주세요", 44, Ink.TITLE, Weight.BOLD)
        c.centered(326, "반려견과 갈 수 있는 곳을 찾을 때\n차의 현재 위치를 써요", 30, Ink.SUB, lineHeight = 44)
        consentSummary(48, 440, 744)
        consentRows(48, 772, 744, withLink = true)
        consentButtons(48, 940, 300, 428, reviewing, agree, decline)
        c.centered(1060, note, 24, Ink.GRAY, lineHeight = 36)
        ui.consentSheet?.let { doc -> host.overlay { consentSheet(doc) } }
    }
}

private fun Scr.closeConsent() {
    val fromSettings = ui.reviewConsent
    ui.reviewConsent = false
    ui.consentSheet = null
    if (fromSettings) host.goTab(com.peta.car.ui.NavTab.SETTINGS) else host.rerender()
}

/** 무엇을 · 어디에 · 보관 세 줄 요약 카드 (높이 296) */
private fun Scr.consentSummary(x: Int, y: Int, w: Int) {
    val rows = listOf(
        Triple("모으는 정보", "차의 현재 위치", 44),
        Triple("보내는 곳", "한국관광공사 · 카카오\n(장소 · 동물병원 조회)", 88),
        Triple("보관", "페타에 저장하지 않아요", 44),
    )
    c.box(x, y, w, 296, Ink.WHITE, 24f, Ink.CARD_LINE, 2f)
    var yy = y + 32
    rows.forEachIndexed { i, (k, v, h) ->
        c.text(x + 32, yy + 2, k, 26, Ink.ORANGE_TEXT, Weight.MEDIUM, w = 180)
        c.text(x + 220, yy, v, 30, Ink.BODY, w = w - 252, lineHeight = 44)
        yy += h
        if (i < rows.lastIndex) {
            c.box(x + 32, yy + 13, w - 64, 2, Ink.DIVIDER)
            yy += 28
        }
    }
}

/** 필수 동의 두 줄. 1/3은 「전문 보기 ›」로 QR 시트를 연다 */
private fun Scr.consentRows(x: Int, y: Int, w: Int, withLink: Boolean) {
    CONSENT_ITEMS.forEachIndexed { i, label ->
        val ry = y + i * 80
        c.box(x, ry + 14, 44, 44, Ink.PEACH, 22f, Ink.ORANGE_TEXT, 3f)
        c.icon(x + 10, ry + 24, 24, R.drawable.ic_check, Ink.ORANGE_TEXT)
        c.text(x + 64, ry + 15, label, 30, Ink.BODY, Weight.MEDIUM, w = w - 260)
        if (withLink) {
            c.text(x + w - 180, ry + 19, "전문 보기 ›", 26, Ink.GRAY, Weight.MEDIUM, w = 180, align = Align.RIGHT)
            c.tap(x, ry - 2, w, 76, "$label 전문 보기", 24f) {
                ui.consentSheet = label.removePrefix("[필수] ")
                host.rerender()
            }
        }
    }
}

private fun Scr.consentButtons(
    x: Int, y: Int, secondW: Int, primaryW: Int, reviewing: Boolean,
    agree: () -> Unit, decline: () -> Unit,
) {
    // 설정에서 다시 연 경우(이미 동의): 철회 · 닫기
    if (reviewing) {
        c.button(x, y, secondW, 96, "동의 철회", ButtonStyle.SECONDARY, onClick = decline)
        c.button(x + secondW + 16, y, primaryW, 96, "닫기", ButtonStyle.PRIMARY) { closeConsent() }
    } else {
        c.button(x, y, secondW, 96, "동의 안 함", ButtonStyle.SECONDARY, onClick = decline)
        c.button(x + secondW + 16, y, primaryW, 96, "동의하고 시작", ButtonStyle.PRIMARY, onClick = agree)
    }
}

/** C00-P-Q 약관 전문 · QR 시트 (1/3) */
private fun Scr.consentSheet(doc: String) {
    val close = { ui.consentSheet = null; host.rerender() }
    c.box(0, 0, c.panelW, 1184, Ink.DIM).apply { isClickable = true; setOnClickListener { close() } }
    c.box(0, 464, 840, 720, Ink.WHITE, 40f).isClickable = true
    c.box(0, 1144, 840, 40, Ink.WHITE) // 아래 모서리는 각지게
    c.box(372, 484, 96, 8, Ink.BUTTON_LINE, 4f)
    c.text(48, 528, doc, 40, Ink.TITLE, Weight.BOLD, w = 640)
    c.icon(752, 532, 44, R.drawable.ic_close, Ink.TITLE)
    c.tap(730, 510, 88, 88, "닫기", 44f) { close() }
    c.text(48, 592, "전문은 폰에서 읽을 수 있어요. 카메라로 QR을 비추세요.", 28, Ink.SUB, w = 744)
    c.box(290, 664, 260, 260, QR_WHITE, 12f)
    c.image(290, 664, 260, 260, R.drawable.img_qr_privacy, radius = 12f)
    c.box(48, 960, 744, 160, Ink.SEGMENT, 20f)
    val lines = listOf(
        "· 차의 현재 위치를 장소 · 동물병원 조회에만 써요",
        "· 한국관광공사 · 카카오에 좌표만 보내고, 저장하지 않아요",
        "· 설정에서 언제든 동의를 철회할 수 있어요",
    )
    lines.forEachIndexed { i, l -> c.text(76, 984 + i * 40, l, 26, Ink.BODY, w = 688) }
}
