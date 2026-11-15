package com.peta.car.privacy

import android.content.Context

/**
 * 위치정보 · 개인정보 동의 (C00-P, 2026-11-15). 기기 안에만 저장한다.
 * 동의 전·거부 상태에서는 차의 현재 위치를 외부(한국관광공사 · 카카오)로 보내지 않는다 —
 * 장소·병원은 앱에 든 샘플로, 낱말 검색은 좌표 없이 그대로 동작한다.
 * 약관 내용이 바뀌면 [VERSION]을 올려 다시 묻는다.
 */
object Consent {
    enum class State { UNKNOWN, AGREED, DECLINED }

    const val VERSION = 1
    /** C00-P · QR이 여는 처리방침 (저장소 docs/PRIVACY.md) */
    const val POLICY_URL = "https://github.com/TaewanKirn/peta/blob/main/docs/PRIVACY.md"
    private const val PREFS = "peta_consent"

    @Volatile var state: State = State.UNKNOWN
        private set

    val locationAllowed: Boolean get() = state == State.AGREED

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        state = if (p.getInt("version", 0) < VERSION) State.UNKNOWN
        else State.entries.getOrElse(p.getInt("state", 0)) { State.UNKNOWN }
    }

    fun set(context: Context, value: State) {
        state = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("version", VERSION).putInt("state", value.ordinal).apply()
    }

    fun label(): String = when (state) {
        State.AGREED -> "동의함"
        State.DECLINED -> "동의 안 함"
        State.UNKNOWN -> "확인 전"
    }
}
