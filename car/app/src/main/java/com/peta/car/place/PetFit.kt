package com.peta.car.place

import com.peta.car.trip.PetProfile

/**
 * 관광공사 반려동물 동반 조건 (detailPetTour2). 값이 없는 항목은 빈 문자열.
 * 2026-11-13 홍익대 반경 20km 12곳 샘플: 동반 구역은 모두 있고, 가능 견종은 6곳에만 있었다.
 */
data class PetRule(
    /** acmpyTypeCd: 전구역 동반가능 · 일부구역 동반가능 … */
    val area: String = "",
    /** acmpyPsblCpam: 전 견종 동반 가능 · 훈련된 소형견 · 5kg 이하 … */
    val allowed: String = "",
    /** acmpyNeedMtr: 목줄 착용 … */
    val needs: String = "",
    /** etcAcmpyInfo */
    val extra: String = "",
    /** relaPosesFclty: 운동장 · 전용 샤워장 … */
    val facilities: String = "",
) {
    val isEmpty get() = area.isBlank() && allowed.isBlank() && needs.isBlank() && extra.isBlank() && facilities.isBlank()
}

enum class Fit { OK, CHECK, NO }

/**
 * 이 반려견이 갈 수 있는 곳인지. 앱이 판단할 수 없는 조건(털날림 등)은 [Fit.CHECK]로 두고 보호자에게 보여 준다.
 * 조건 정보가 없으면 [Fit.CHECK] (거르지 않는다).
 */
object PetFit {
    /** 동물보호법 시행규칙의 맹견 5종 */
    private val DANGEROUS = listOf("도사", "핏불", "로트와일러", "스태퍼드셔")
    private val KG_LIMIT = Regex("""(\d+(?:\.\d+)?)\s*kg\s*(이하|미만)""")

    fun of(rule: PetRule?, pet: PetProfile?): Fit {
        if (rule == null || pet == null) return Fit.CHECK
        if ("불가" in rule.area) return Fit.NO
        val text = rule.allowed.replace(" ", "")
        if (text.isBlank()) return Fit.CHECK
        val size = pet.sizeLabel
        val dangerous = DANGEROUS.any { it in pet.breed }

        if ("맹견" in text && ("제외" in text || "불가" in text) && dangerous) return Fit.NO
        if ("전견종" in text || "모든견종" in text) return if ("털날림" in text) Fit.CHECK else Fit.OK
        // 안내견만 받는 곳 (다른 견종 조건이 없을 때)
        if ("안내견" in text && listOf("소형견", "중형견", "대형견", "kg", "견종").none { it in text }) return Fit.NO
        if (KG_LIMIT.containsMatchIn(rule.allowed.lowercase()) || "소형견" in text) {
            return if (size == "소형견") Fit.OK else Fit.NO
        }
        if ("중형견" in text && ("이하" in text || "까지" in text)) return if (size == "대형견") Fit.NO else Fit.OK
        if ("대형견" in text && ("불가" in text || "제외" in text)) return if (size == "대형견") Fit.NO else Fit.OK
        if ("맹견" in text) return Fit.OK // 맹견 제외 조건이고 이 반려견은 맹견이 아니다
        return Fit.CHECK
    }

    /** 상세 팝업 한 줄: "전구역 동반가능 · 목줄 착용" */
    fun summary(rule: PetRule?): String =
        rule?.let { listOf(it.area, it.needs).filter(String::isNotBlank).joinToString(" · ") }.orEmpty()
}
