package com.peta.car.trip

private fun String.hasBatchim(): Boolean {
    val last = lastOrNull() ?: return false
    return last in '가'..'힣' && (last - '가') % 28 != 0
}

/** 보리와 / 단풍과 */
fun String.withWa(): String = this + if (hasBatchim()) "과" else "와"

/** 휴게소로 / 식당으로 / 서울로 (ㄹ 받침은 '로') */
fun String.withRo(): String {
    val last = lastOrNull() ?: return this
    if (last !in '가'..'힣') return this + "로"
    val jong = (last - '가') % 28
    return this + if (jong == 0 || jong == 8) "로" else "으로"
}

/** 보리랑 / 단풍이랑 */
fun String.withRang(): String = this + if (hasBatchim()) "이랑" else "랑"

/** 숲속 스테이는 / 가평휴게소는 / 병원은 */
fun String.withEunNeun(): String = this + if (hasBatchim()) "은" else "는"

/** 보리가 / 설이가 / 단풍이 */
fun String.withIGa(): String = this + if (hasBatchim()) "이" else "가"

/** 식당을 / 휴게소를 */
fun String.withEulReul(): String = this + if (hasBatchim()) "을" else "를"
