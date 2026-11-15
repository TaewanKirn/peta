package com.peta.car.ui

import com.peta.car.R

/**
 * assets/lottie/bori3d 로띠(res/raw)의 캔버스 크기와, 그 안에서 그림이 차지하는 영역.
 * Figma의 Bori3D/Obj3D 인스턴스 상자는 그림 영역과 같으므로, 이 값으로 로띠 뷰를 늘려 맞춘다.
 * 값은 각 JSON의 이미지 레이어(첫 프레임) 위치에서 읽었다.
 */
data class LottieArt(val raw: Int, val canvasW: Int, val canvasH: Int, val x: Int, val y: Int, val w: Int, val h: Int)

object Lotties {
    val BORI_RUN = LottieArt(R.raw.bori_run, 800, 900, 90, 141, 619, 639)
    val BORI_SIT = LottieArt(R.raw.bori_sit, 800, 900, 140, 140, 521, 680)
    val BORI_SUNGLASSES = LottieArt(R.raw.bori_sunglasses, 800, 900, 140, 140, 520, 680)
    /** 로딩: 눈 없는 컷아웃 + 벡터 눈 · 코, 머리만 위 · 오른쪽 · 아래 · 왼쪽으로 두리번 (assets/lottie/bori3d/_gen_loading_lottie.py) */
    val BORI_LOADING = LottieArt(R.raw.bori_loading, 800, 900, 140, 140, 520, 680)
    val BORI_WORRIED = LottieArt(R.raw.bori_worried, 800, 900, 139, 140, 522, 680)
    val BORI_LISTENING = LottieArt(R.raw.bori_listening, 800, 900, 108, 140, 584, 680)
    val BORI_JUMP = LottieArt(R.raw.bori_jump, 800, 900, 140, 140, 520, 680)
    val BORI_THUMBS_UP = LottieArt(R.raw.bori_thumbs_up, 800, 900, 138, 140, 525, 680)
    val BORI_REST_PARK = LottieArt(R.raw.bori_rest_park, 900, 760, 70, 151, 760, 579)
    val OBJ_CAR = LottieArt(R.raw.obj_car, 600, 640, 60, 240, 480, 320)
    val OBJ_POOP_BAG = LottieArt(R.raw.obj_poop_bag, 600, 640, 114, 80, 371, 480)
    val OBJ_BOWL_BOTTLE = LottieArt(R.raw.obj_bowl_bottle, 600, 640, 60, 151, 480, 409)
    val OBJ_BONE = LottieArt(R.raw.obj_bone, 600, 640, 60, 297, 480, 263)
    val OBJ_LEASH = LottieArt(R.raw.obj_leash, 600, 640, 60, 287, 480, 273)
    val OBJ_HOSPITAL = LottieArt(R.raw.obj_hospital, 600, 640, 60, 141, 480, 419)
    val OBJ_BONE_ORANGE = LottieArt(R.raw.obj_bone_orange, 600, 640, 60, 216, 480, 344)
    val OBJ_HOUSE = LottieArt(R.raw.obj_house, 600, 640, 60, 105, 480, 455)
}
