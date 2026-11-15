package com.peta.car.ui

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.peta.car.R

/**
 * 앱 색. 라이트 = Figma 「화면수정(태완)」 스타일프레임(2026-11-12), 다크 = Figma 「차량 · 다크 (2026-11-13 시안)」.
 * 다크 값은 사용성 검토(WCAG 대비)로 고친 값이다: 주황 위 글자는 어둡게(ON_PRIMARY 6.2:1), 빨강 채움 #CC1E31(흰 글자 5.5:1),
 * FAINT #958A83(배경 대비 5.4:1). 어느 쪽을 쓸지는 [ThemeMode.dark].
 */
object Ink {
    private fun c(light: Long, dark: Long): Int = (if (ThemeMode.dark) dark else light).toInt()

    val TITLE get() = c(0xFF2A211B, 0xFFF5EEE8)
    val BODY get() = c(0xFF2A211B, 0xFFF5EEE8)
    val DARK get() = c(0xFF131417, 0xFFF5EEE8)
    val BUTTON_TEXT get() = c(0xFF3D332B, 0xFFE9DFD7)
    val SUB get() = c(0xFF6B5E57, 0xFFB9ADA5)
    val GRAY get() = c(0xFF7A6F68, 0xFFA3978F)
    val FAINT get() = c(0xFFA39890, 0xFF958A83)
    val ORANGE get() = c(0xFFF2711C, 0xFFF2711C)
    val ORANGE_TEXT get() = c(0xFFB84A08, 0xFFFF9A55)
    val ORANGE_DEEP get() = c(0xFFE05A00, 0xFFFF8A3D)
    val ORANGE_TAG get() = c(0xFFB04600, 0xFFFF9A55)
    val RED get() = c(0xFFE5283C, 0xFFCC1E31)
    val RED_TEXT get() = c(0xFFC71F33, 0xFFFF6B78)
    val PINK get() = c(0xFFFDE7E9, 0xFF3A2226)
    val PINK_BOX get() = c(0xFFFBD0D6, 0xFF4A2A30)
    val PINK_LINE get() = c(0xFFF0A0AC, 0xFF6E3A43)
    val PINK_STROKE get() = c(0xFFF3C2C8, 0xFF5E343B)
    val PINK_BUTTON get() = c(0xFFC87886, 0xFFC87886)
    val PINK_INK get() = c(0xFF4A282E, 0xFFF3C9CF)
    val PINK_INK_DARK get() = c(0xFF2A1418, 0xFFFFE3E7)
    val WHITE get() = c(0xFFFFFFFF, 0xFF26201C)
    val DIVIDER get() = c(0xFFEFE8E2, 0xFF3A322C)
    val CARD_LINE get() = c(0xFFEFE8E2, 0xFF3A322C)
    val CHIP_LINE get() = c(0xFFE6DDD6, 0xFF4A413A)
    val BUTTON_LINE get() = c(0xFFD9CCC2, 0xFF5A4F47)
    val MUTED_FILL get() = c(0xFFD9CCC2, 0xFF3D3530)
    val CHECK_LINE get() = c(0xFFB3A9A2, 0xFF6E635B)
    val CHIP_ON get() = c(0xFF3D332B, 0xFFF2E9E1)
    val SEGMENT get() = c(0xFFF1E8E0, 0xFF2F2823)
    val PEACH get() = c(0xFFFFE8D6, 0xFF3A2A1E)
    val TRACK get() = c(0xFFF6DCC8, 0xFF3D2E22)
    val ROUTE_LINE get() = c(0xFFF2C9A8, 0xFF6B4A30)
    val ROUTE_DONE get() = c(0xFFE3D9D1, 0xFF4A413A)
    val ROUTE_DONE_DOT get() = c(0xFFC9BEB6, 0xFF6E635B)
    val MIC_RING_OUT get() = c(0xFFFFF1E6, 0xFF3A2A1E)
    val MIC_RING_IN get() = c(0xFFFFE0C7, 0xFF4A3424)
    val SAD_LINE get() = c(0xFF5C5973, 0xFFA9A6C4)
    val SEARCH_OFF get() = c(0xFFB8ABA2, 0xFF6E635B)
    val DIM get() = c(0x8029211C, 0x99000000)

    /** 주황 채움 위 글자 · 아이콘 (라이트 흰색 / 다크 어두운 색, 대비 6.2:1) */
    val ON_PRIMARY get() = c(0xFFFFFFFF, 0xFF1A1512)
    /** 빨강 등 진한 색 위 흰 글자 (두 모드 같음) */
    val ON_COLOR get() = c(0xFFFFFFFF, 0xFFFFFFFF)
    /** 선택된 칩 글자 (다크는 칩이 밝아지므로 어두운 글자) */
    val ON_CHIP get() = c(0xFFFFFFFF, 0xFF1A1512)
}

/**
 * 화면 테마. 설정 0 = 자동(시스템 야간 모드), 1 = 라이트, 2 = 다크. C14 설정에서 바꾸고 기기에 저장한다.
 * Pleos SDK에 차량 주간/야간 API가 없어 자동은 안드로이드 uiMode를 따른다 (2026-11-13 확인).
 */
object ThemeMode {
    @Volatile var dark: Boolean = false
    @Volatile var setting: Int = 0
    private const val PREFS = "peta_theme"

    fun load(context: Context) {
        setting = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("setting", 0)
    }

    fun next(context: Context) {
        setting = (setting + 1) % 3
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("setting", setting).apply()
    }

    /** 지금 다크로 그릴지 계산해 [dark]에 넣고 돌려준다 */
    fun resolve(context: Context): Boolean {
        val night = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        dark = when (setting) { 1 -> false; 2 -> true; else -> night }
        return dark
    }

    fun label(): String = when (setting) { 1 -> "라이트"; 2 -> "다크"; else -> "자동" }
}

enum class Weight { REGULAR, MEDIUM, BOLD }

/** Noto Sans KR 400/500/700 (res/font, OFL). Figma 글자와 같은 서체·굵기 */
object Fonts {
    private var cache: Map<Weight, Typeface>? = null

    fun of(context: Context, weight: Weight): Typeface {
        val map = cache ?: mapOf(
            Weight.REGULAR to load(context, R.font.noto_sans_kr_regular, Typeface.NORMAL),
            Weight.MEDIUM to load(context, R.font.noto_sans_kr_medium, Typeface.NORMAL),
            Weight.BOLD to load(context, R.font.noto_sans_kr_bold, Typeface.BOLD),
        ).also { cache = it }
        return map.getValue(weight)
    }

    private fun load(context: Context, id: Int, fallbackStyle: Int): Typeface =
        runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: Typeface.defaultFromStyle(fallbackStyle)
}
