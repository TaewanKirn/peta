package com.peta.car.ui

import com.peta.car.R

enum class NavTab { HOME, SEARCH, SETTINGS, NONE }

/** 헤더·하단 탭·세로 메뉴에 넘기는 상태와 동작 */
class ChromeSpec(
    val title: String,
    val tab: NavTab,
    val driving: Boolean,
    val emergencyOn: Boolean,
    val onClose: () -> Unit,
    val onTab: (NavTab) -> Unit,
    val onEmergency: () -> Unit,
)

private const val LOCKED_ALPHA = 0.35f

/**
 * 차량 화면 규칙 1~4, 13, 29:
 * 1/3 = 위 헤더(닫기 · 타이틀 · 응급) + 아래 탭(홈 · 검색 · 설정)
 * 2/3 = 위 헤더(닫기 · 왼쪽 정렬 타이틀) + 왼쪽 세로 메뉴(맨 아래 응급)
 * 주행 중에는 닫기와 탭을 흐리게 하고 응급만 누를 수 있다.
 */
fun PanelCanvas.chrome(spec: ChromeSpec) {
    if (wide) {
        headerWide(spec)
        rail(spec)
    } else {
        header(spec)
        tabBar(spec)
    }
}

private fun PanelCanvas.closeButton(spec: ChromeSpec) {
    val a = if (spec.driving) LOCKED_ALPHA else 1f
    icon(58, 36, 48, R.drawable.ic_close, Ink.TITLE, alpha = a)
    tap(40, 16, 120, 88, "닫기", 44f, enabled = !spec.driving) { spec.onClose() }
}

private fun PanelCanvas.header(spec: ChromeSpec) {
    box(0, 0, panelW, 120, Ink.WHITE)
    box(0, 118, panelW, 2, Ink.DIVIDER)
    closeButton(spec)
    centered(37, spec.title, 32, Ink.TITLE, Weight.BOLD, x = 160, w = 480)
    // 응급: 3D 사이렌 + "응급" (규칙 6). 응급 화면에서는 빨간 바탕
    val on = spec.emergencyOn
    box(644, 22, 177, 83, if (on) Ink.RED else Ink.PINK, 44f)
    image(674, 32, 45, 60, R.drawable.img_siren)
    text(735, 39, "응급", 32, if (on) Ink.ON_COLOR else Ink.RED_TEXT, Weight.BOLD)
    tap(644, 22, 177, 83, "응급", 44f) { spec.onEmergency() }
}

private fun PanelCanvas.headerWide(spec: ChromeSpec) {
    box(0, 0, panelW, 120, Ink.WHITE)
    box(0, 118, panelW, 2, Ink.DIVIDER)
    closeButton(spec)
    text(130, 37, spec.title, 32, Ink.TITLE, Weight.BOLD, w = 900)
}

private data class TabItem(val tab: NavTab, val label: String, val icon: Int)

private val TABS = listOf(
    TabItem(NavTab.HOME, "홈", R.drawable.ic_home),
    TabItem(NavTab.SEARCH, "검색", R.drawable.ic_search),
    TabItem(NavTab.SETTINGS, "설정", R.drawable.ic_settings),
)

private fun PanelCanvas.tabBar(spec: ChromeSpec) {
    val top = 1088
    box(0, top, panelW, 96, Ink.WHITE)
    box(0, top, panelW, 2, Ink.DIVIDER)
    val a = if (spec.driving) LOCKED_ALPHA else 1f
    TABS.forEachIndexed { i, item ->
        val on = spec.tab == item.tab
        val weight = if (on) Weight.BOLD else Weight.MEDIUM
        val labelW = measure(item.label, 32, weight)
        val group = 40 + 12 + labelW
        val gx = i * 280 + (280 - group) / 2
        icon(gx, top + 28, 40, item.icon, if (on) Ink.ORANGE else Ink.GRAY, alpha = a)
        text(gx + 52, top + 25, item.label, 32, if (on) Ink.ORANGE_TEXT else Ink.GRAY, weight).alpha = a
        if (on) box(i * 280 + 80, top, 120, 6, Ink.ORANGE, 3f, alpha = a)
        tap(i * 280, top, 280, 96, item.label, 0f, enabled = !spec.driving) { spec.onTab(item.tab) }
    }
}

private fun PanelCanvas.rail(spec: ChromeSpec) {
    val top = 120
    box(0, top, 120, 1064, Ink.WHITE)
    box(118, top, 2, 1064, Ink.DIVIDER)
    val a = if (spec.driving) LOCKED_ALPHA else 1f
    TABS.forEachIndexed { i, item ->
        val on = spec.tab == item.tab
        val y = top + 16 + i * 136
        icon(39, y + 21, 40, item.icon, if (on) Ink.ORANGE else Ink.GRAY, alpha = a)
        centered(y + 67, item.label, 28, if (on) Ink.ORANGE_TEXT else Ink.GRAY, if (on) Weight.BOLD else Weight.MEDIUM, x = 0, w = 118).alpha = a
        if (on) box(0, y + 24, 6, 80, Ink.ORANGE, 3f, alpha = a)
        tap(0, y, 118, 128, item.label, 24f, enabled = !spec.driving) { spec.onTab(item.tab) }
    }
    // 응급은 메뉴 맨 아래 (규칙 4·29)
    if (spec.emergencyOn) {
        box(7, top + 878, 104, 145, Ink.PINK, 24f)
        box(0, top + 911, 6, 80, Ink.RED, 3f)
    }
    image(32, top + 890, 54, 72, R.drawable.img_siren)
    centered(top + 970, "응급", 28, Ink.RED_TEXT, Weight.BOLD, x = 0, w = 118)
    tap(7, top + 870, 104, 160, "응급", 24f) { spec.onEmergency() }
}
