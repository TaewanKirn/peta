package com.peta.car.place

import com.peta.car.PetaConfig
import java.util.Random

/**
 * [가정] 시연용 가상 수치. 관광공사 API에는 후기·이용자 수가 없어, 장소 id를 시드로 값을 만든다.
 * 같은 장소는 항상 같은 값이다. 실제 사용자 후기·방문자가 아니다. [PetaConfig.DEMO_PLACE_STATS]로 끈다.
 */
object DemoStats {
    private val REVIEWS = listOf(
        "잔디가 넓어서 좋아요", "물그릇을 챙겨 주셨어요", "리드줄만 하면 어디든 괜찮아요", "그늘이 많아 쉬기 좋아요",
        "직원분이 강아지를 좋아하세요", "자리가 넓어요", "주차하기 편해요", "산책할 곳이 이어져 있어요",
        "큰 아이도 눈치 안 보여요", "간식을 챙겨 주셨어요",
    )

    fun fill(place: Place, enabled: Boolean = PetaConfig.DEMO_PLACE_STATS): Place {
        if (!enabled) return place
        val random = Random(place.id.fold(1125899906842597L) { acc, ch -> 31 * acc + ch.code })
        return place.copy(petaVisitors = 3 + random.nextInt(180), reviewOneLine = REVIEWS[random.nextInt(REVIEWS.size)])
    }
}
