package com.peta.car.screens

import android.widget.ImageView
import com.peta.car.R
import com.peta.car.place.PlaceRepository
import com.peta.car.place.SamplePlaces
import com.peta.car.remote.SampleTrip
import com.peta.car.trip.TripPlan
import com.peta.car.trip.withWa
import com.peta.car.ui.Bg
import com.peta.car.ui.ButtonStyle
import com.peta.car.ui.Ink
import com.peta.car.ui.Lotties
import com.peta.car.ui.Part
import com.peta.car.ui.Weight

// ---------------- C00 스플래시 ----------------

fun Scr.c00Splash() {
    c.background(Bg.PLAIN)
    val pet = s.plan?.pet?.name ?: "반려견"
    val cx = c.panelW / 2
    c.lottie(cx - 142, 330, 284, 300, Lotties.BORI_RUN)
    c.image(cx - 148, 663, 296, 86, R.drawable.img_logo)
    c.centered(800, "${pet.withWa()} 떠날 준비를 하고 있어요", 32, Ink.SUB)
}

// ---------------- C01 홈 · 여행 제안 (모바일에서 받은 여행이 없을 때) ----------------

private val HOME_CHIPS = listOf(listOf("전체", "공원", "카페", "식당", "휴게소"), listOf("숙소", "산책로", "캠핑장", "바다", "계곡"))

/** 홈 추천 반경 단계(km). 관광공사 위치 조회는 200km까지 받는다. */
internal val RADIUS_STEPS = listOf(20, 50, 100, 200)

/**
 * 관광공사 위치 조회는 장소 일부만 돌려준다(양성계곡은 직선 55km인데 반경 100km 조회 43곳에 없음, 2026-11-13).
 * 그래서 칩을 고른 채 넓히면 칩 낱말로 전국 조회도 해서 그 반경 안에 드는 곳을 더한다. 바다 → 해변처럼 이름에 많이 쓰는 말.
 */
private val CHIP_WORD = mapOf(
    "공원" to "공원", "카페" to "카페", "식당" to "식당", "휴게소" to "휴게소", "숙소" to "펜션",
    "산책로" to "산책", "캠핑장" to "캠핑", "바다" to "해변", "계곡" to "계곡",
)

fun Scr.c01Home(plan: TripPlan) {
    c.background(Bg.PLAIN)
    // 추천 = 샘플 추천 여행 + 관광공사에서 받은 주변 장소(반경 20km, 가까운 순). 칩을 고르면 그 분류만 남긴다.
    // 「여기로 떠나기」는 아직 샘플 여행으로 이어간다 (여행 계획 만들기는 모바일 몫).
    val picks = homePicks()
    val index = ui.homeIndex.coerceIn(0, (picks.size - 1).coerceAtLeast(0))
    val go = {
        ui.homeDetail = null
        ui.sampleAccepted = true
        actions?.useSampleTrip()
        host.rerender()
    }
    val onChip: (String) -> Unit = { chip ->
        ui.homeChip = chip
        ui.homeIndex = 0
        // 이미 넓힌 상태면 새 칩 낱말도 받아 둔다
        if (ui.radiusKm > RADIUS_STEPS.first()) CHIP_WORD[chip]?.let {
            if (PlaceRepository.shared.widenedHits(it) == null) actions?.widenSearch(it)
        }
        host.rerender()
    }
    // 한 번 누르면 고르고, 고른 카드를 다시 누르면 자세히 본다
    val tapCard: (Int) -> Unit = { i ->
        if (i == index) ui.homeDetail = picks[i].id else ui.homeIndex = i
        host.rerender()
    }
    val page: (Int) -> Unit = { ui.homeIndex = it; host.rerender() }

    title(160, "오늘은 여기로 여행 어때요?")
    homeSubtitle(plan, showLink = picks.isNotEmpty())
    if (wide) {
        chipRow(178, 318, 1464, HOME_CHIPS.flatten(), ui.homeChip, onChip)
        if (picks.isEmpty()) {
            homeEmpty(274, 1272, 412)
        } else {
            // 2/3: 카드 두 장을 나란히, 두 장씩 넘긴다
            val first = index / 2 * 2
            val cardW = (1272 - 32) / 2
            for (k in 0..1) {
                val i = first + k
                val pick = picks.getOrNull(i) ?: break
                homeCard(274 + k * (cardW + 32), 412, cardW, 550, 330, pick, titleSize = 44, selected = i == index) { tapCard(i) }
            }
            c.pagerButton(178, 649, 76, next = false, enabled = first > 0, label = "이전 추천") { page(first - 2) }
            c.pagerButton(1566, 649, 76, next = true, enabled = first + 2 <= picks.lastIndex, label = "다음 추천") { page(first + 2) }
        }
    } else {
        if (picks.isEmpty()) {
            homeEmpty(120, 600, 328)
        } else {
            homeCard(120, 328, 600, 384, 220, picks[index], titleSize = 40, selected = true) { tapCard(index) }
            c.pagerButton(22, 482, 76, next = false, enabled = index > 0, label = "이전 추천") { page(index - 1) }
            c.pagerButton(742, 482, 76, next = true, enabled = index < picks.lastIndex, label = "다음 추천") { page(index + 1) }
        }
        chipRows(58, 758, 724, HOME_CHIPS, ui.homeChip, onChip)
    }
    // 결과가 없으면 비활성. 범위 넓히기 · 전체 보기는 본문 안의 회색 글자로 한 단계 낮춘다.
    footer("여기로 떠나기", enabled = picks.isNotEmpty(), onClick = go)
    picks.firstOrNull { it.id == ui.homeDetail }?.let { place -> host.overlay { homeDetailPopup(place, go) } }
        ?: run { ui.homeDetail = null }
}

/** 다음 반경 단계. 이미 가장 넓으면 null */
private fun Scr.nextRadius(): Int? = RADIUS_STEPS.firstOrNull { it > ui.radiusKm }

/** 넓힌 반경의 결과를 아직 받는 중인지 */
private fun Scr.radiusPending(): Boolean {
    if (ui.radiusKm <= RADIUS_STEPS.first()) return false
    val repo = PlaceRepository.shared
    return repo.wideState(ui.radiusKm) == PlaceRepository.WideState.LOADING ||
        CHIP_WORD[ui.homeChip]?.let { repo.widenedHits(it) == null } == true
}

private fun Scr.widenRadius() {
    val next = nextRadius() ?: return
    ui.radiusKm = next
    ui.homeIndex = 0
    if (PlaceRepository.shared.wideState(next).let { it == PlaceRepository.WideState.NONE || it == PlaceRepository.WideState.FAILED }) actions?.widenNearby(next)
    CHIP_WORD[ui.homeChip]?.let { if (PlaceRepository.shared.widenedHits(it) == null) actions?.widenSearch(it) }
    host.rerender()
}

/** 「근방 20km, 리치와 함께 가기 좋은 곳」 + 회색 박스 [범위 넓히기] */
private fun Scr.homeSubtitle(plan: TripPlan, showLink: Boolean) {
    val sub = "근방 ${ui.radiusKm}km, ${plan.pet.name.withWa()} 함께 가기 좋은 곳"
    val pending = radiusPending()
    val failed = PlaceRepository.shared.wideState(ui.radiusKm) == PlaceRepository.WideState.FAILED
    val label = when {
        !showLink -> null
        pending -> "넓히는 중…"
        failed -> "다시 시도"
        nextRadius() != null -> "범위 넓히기"
        else -> null
    }
    val size = 32
    val subW = c.measure(sub, size, Weight.REGULAR)
    val pillText = 28
    val pillH = 56
    val pillW = label?.let { c.measure(it, pillText, Weight.MEDIUM) + 48 } ?: 0
    val gap = 24
    val x0 = if (wide) left else 40 + (760 - subW - (if (label != null) gap + pillW else 0)) / 2
    c.text(x0, 236, sub, size, Ink.SUB, Weight.REGULAR)
    if (label != null) {
        val px = x0 + subW + gap
        val py = 236 + 22 - pillH / 2 // 부제 글자 줄 가운데에 맞춘다
        // 아래 칩(필터)보다 한 단계 낮춘다: 테두리 없는 회색 박스 + 회색 글자
        c.box(px, py, pillW, pillH, Ink.CARD_LINE, 12f, alpha = if (pending) 0.5f else 1f)
        c.centerIn(px, pillW, py + (pillH - c.lineBox(pillText)) / 2, label, pillText, Ink.SUB, Weight.MEDIUM)
        // 터치 영역은 96 높이로 넉넉하게 (규칙: 터치 96px 이상)
        if (!pending) c.tap(px - 8, py - (96 - pillH) / 2, pillW + 16, 96, label, 48f) {
            if (failed) { actions?.widenNearby(ui.radiusKm); host.rerender() } else widenRadius()
        }
    }
}

/** 홈 추천 목록. 「전체」는 샘플 추천을 앞에, 나머지는 가까운 순. 쇼핑 같은 「기타」는 뺀다. */
private fun Scr.homePicks(): List<com.peta.car.place.Place> {
    val samples = SamplePlaces.travel
    val repo = PlaceRepository.shared
    // 주변(관광공사 위치 조회, 반경 20km) + [범위 넓히기]로 넓혀 받은 반경의 목록
    val widened = ui.radiusKm > RADIUS_STEPS.first()
    val wider = if (!widened) emptyList() else
        repo.wideNearby(ui.radiusKm) +
            CHIP_WORD[ui.homeChip]?.let { repo.widenedHits(it) }.orEmpty()
                .filter { com.peta.car.place.distanceKm(s.location, it.point) <= ui.radiusKm }
    val nearby = (repo.travelCandidates("") + repo.restCandidates("") + wider)
        .filter { p -> samples.none { it.id == p.id } && p.category != "기타" }
        .distinctBy { it.id }
        .sortedBy { it.routeKm ?: Double.MAX_VALUE }
    // 반려견 적합도: 이 반려견이 갈 수 없다고 확인된 곳(예: 소형견만 · 안내견만)은 뺀다
    val fit = repo.fitting(samples + nearby, s.plan?.pet).filter { PlaceRepository.matchesChip(it, ui.homeChip) }
    // 「전체」는 한 분류(예: 홍대 근처 숙소)가 몰리지 않게 분류를 번갈아 보여 준다
    return if (ui.homeChip == "전체") fit.filter { it in samples } + mixCategories(fit.filter { it !in samples }) else fit
}

/** 분류별 가까운 순을 유지하면서 분류를 번갈아 섞는다. 가장 가까운 곳이 있는 분류부터. */
internal fun mixCategories(list: List<com.peta.car.place.Place>): List<com.peta.car.place.Place> {
    val groups = list.groupBy { it.category }.values.map { it.toMutableList() }.toMutableList()
    val out = ArrayList<com.peta.car.place.Place>(list.size)
    while (groups.isNotEmpty()) {
        val it = groups.iterator()
        while (it.hasNext()) {
            val g = it.next()
            out += g.removeAt(0)
            if (g.isEmpty()) it.remove()
        }
    }
    return out
}

private fun Scr.homeEmpty(x: Int, w: Int, y: Int) {
    val pending = radiusPending()
    val next = nextRadius()
    val title = if (pending) "넓은 범위에서 찾고 있어요" else "근방 ${ui.radiusKm}km에 ${ui.homeChip} 정보가 없어요"
    val sub = when {
        pending -> "근방 ${ui.radiusKm}km까지 찾고 있어요"
        next != null -> "범위를 넓혀 볼까요?"
        else -> "다른 분류를 골라 보세요"
    }
    // 강아지 그래픽을 위에, 글자를 아래에
    val dogW = if (wide) 210 else 132
    val dogH = dogW * 4 / 3
    val dogY = y + if (wide) 36 else 18
    if (pending) loadingBori(x + w / 2 - dogW / 2, dogY, dogW, dogH) else worriedBori(x + w / 2 - dogW / 2, dogY, dogW, dogH)
    var ty = dogY + dogH + if (wide) 28 else 14
    c.centerIn(x, w, ty, title, if (wide) 44 else 38, Ink.TITLE, Weight.BOLD)
    ty += if (wide) 70 else 58
    c.centerIn(x, w, ty, sub, if (wide) 32 else 28, Ink.SUB, Weight.REGULAR)
    if (pending) return
    // 부제 옆 [범위 넓히기]와 같은 회색 박스 (여기로 떠나기 · 필터 칩보다 한 단계 낮춘다)
    ty += if (wide) 56 else 44
    val failed = PlaceRepository.shared.wideState(ui.radiusKm) == PlaceRepository.WideState.FAILED
    val (label, onTap) = if (failed) {
        "다시 시도" to { actions?.widenNearby(ui.radiusKm); host.rerender() }
    } else if (next != null) {
        "범위 넓히기" to { widenRadius() }
    } else {
        "전체 보기" to { ui.homeChip = "전체"; ui.homeIndex = 0; host.rerender() }
    }
    val text = 28
    val bh = 56
    val bw = c.measure(label, text, Weight.MEDIUM) + 48
    val bx = x + w / 2 - bw / 2
    c.box(bx, ty, bw, bh, Ink.CARD_LINE, 12f)
    c.centerIn(bx, bw, ty + (bh - c.lineBox(text)) / 2, label, text, Ink.SUB, Weight.MEDIUM)
    c.tap(bx - 8, ty - (96 - bh) / 2, bw + 16, 96, label, 48f, onClick = onTap)
}

private fun Scr.homeCard(
    x: Int, y: Int, w: Int, h: Int, photoH: Int, pick: com.peta.car.place.Place, titleSize: Int,
    selected: Boolean, onTap: () -> Unit,
) {
    if (selected) c.card(x, y, w, h, Ink.ORANGE, 4f) else c.card(x, y, w, h)
    val photo = photoOf(pick)
    if (photo != null) {
        c.image(x, y, w, photoH, photo, ImageView.ScaleType.CENTER_CROP, radius = 32f, topRadiusOnly = true)
    } else {
        c.box(x + 4, y + 4, w - 8, photoH - 4, Ink.PEACH, 28f)
        c.lottie(x + w / 2 - 90, y + 20, 180, photoH - 40, Lotties.OBJ_HOUSE)
        // 받아 온 사진이 있으면 placeholder 위에 덮는다 (카드 테두리 4 안쪽)
        if (pick.imageUrl.isNotBlank()) c.remoteImage(x + 4, y + 4, w - 8, photoH - 4, pick.imageUrl, radius = 28f, topRadiusOnly = true)
    }
    if (selected) c.check(x + 20, y + 20, 56, on = true)
    val ty = y + photoH
    val label = if (SamplePlaces.travel.any { it.id == pick.id }) "페타 추천 여행" else pick.category.ifBlank { "추천 장소" }
    if (wide) {
        c.cardLabel(x + 36, ty + 24, label)
        c.text(x + 36, ty + 64, pick.name, titleSize, Ink.TITLE, Weight.BOLD, w = w - 72)
        c.distanceText(x + 36, ty + 140, eta(pick), pick.routeKm ?: 0.0, size = 32, w = w - 72)
    } else {
        c.cardLabel(x + 32, ty + 16, label, size = 24)
        c.text(x + 32, ty + 44, pick.name, titleSize, Ink.TITLE, Weight.BOLD, w = w - 64)
        c.distanceText(x + 32, ty + 102, eta(pick), pick.routeKm ?: 0.0)
    }
    c.tap(x, y, w, h, pick.name, 32f, onClick = onTap)
}

/** 홈 장소 상세 (카드를 다시 누르면). 화면 전체를 덮고, 닫기 · 여기로 떠나기 두 버튼만 둔다. */
private fun Scr.homeDetailPopup(place: com.peta.car.place.Place, go: () -> Unit) {
    c.box(0, 0, c.panelW, 1184, Ink.DIM).apply {
        isClickable = true
        importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    val pw = if (wide) 1100 else 760
    val px = (c.panelW - pw) / 2
    val py = 100
    val ph = 984
    val photoH = if (wide) 330 else 280
    c.box(px, py, pw, ph, Ink.WHITE, 40f)
    val photo = photoOf(place)
    if (photo != null) {
        c.image(px, py, pw, photoH, photo, ImageView.ScaleType.CENTER_CROP, radius = 40f, topRadiusOnly = true)
    } else {
        c.box(px, py, pw, photoH, Ink.PEACH, 40f)
        c.box(px, py + photoH - 40, pw, 40, Ink.PEACH) // 아래 모서리는 각지게
        c.lottie(px + pw / 2 - 110, py + 40, 220, photoH - 80, Lotties.OBJ_HOUSE)
        if (place.imageUrl.isNotBlank()) c.remoteImage(px, py, pw, photoH, place.imageUrl, radius = 40f, topRadiusOnly = true)
    }
    val tx = px + 48
    val tw = pw - 96
    var ty = py + photoH + 36
    val label = if (SamplePlaces.travel.any { it.id == place.id }) "페타 추천 여행" else place.category.ifBlank { "추천 장소" }
    c.cardLabel(tx, ty, label)
    ty += 44
    c.text(tx, ty, place.name, 48, Ink.TITLE, Weight.BOLD, w = tw)
    ty += 80
    c.distanceText(tx, ty, eta(place), place.routeKm ?: 0.0, size = 32, w = tw)
    ty += 60
    // 샘플 표시는 코드 안의 데이터 구분용이라 화면에는 쓰지 않는다
    val address = place.address.replace(" (샘플)", "").replace("(샘플)", "").trim()
    if (address.isNotBlank()) { c.text(tx, ty, address, 30, Ink.GRAY, w = tw); ty += 56 }
    if (place.reviewOneLine.isNotBlank()) { c.text(tx, ty, "“${place.reviewOneLine}”", 32, Ink.BODY, w = tw); ty += 56 }
    if (place.petaVisitors > 0) { c.text(tx, ty, "${place.petaVisitors}명 다녀감", 28, Ink.GRAY, w = tw); ty += 52 }
    // 반려견 동반 조건 (관광공사). 받기 전이거나 정보가 없으면 그렇게 밝힌다.
    val rule = PlaceRepository.shared.petRule(place.id)
    val fit = com.peta.car.place.PetFit.of(rule, s.plan?.pet)
    // 관광공사에 조건이 있을 때만 보여 준다 (샘플 장소 · 조건이 비어 있는 곳은 줄을 두지 않는다)
    if (rule != null && !rule.isEmpty) {
        val ruleLine = listOf(com.peta.car.place.PetFit.summary(rule), rule.allowed).filter(String::isNotBlank).joinToString(" · ")
        val ruleColor = if (fit == com.peta.car.place.Fit.OK) Ink.ORANGE_TEXT else Ink.GRAY
        c.text(tx, ty, ruleLine, 28, ruleColor, Weight.MEDIUM, w = tw)
    }
    val by = py + ph - 48 - 104
    val closeW = (tw - 24) / 3
    c.button(tx, by, closeW, 104, "닫기", ButtonStyle.SECONDARY) { ui.homeDetail = null; host.rerender() }
    c.button(tx + closeW + 24, by, tw - closeW - 24, 104, "여기로 떠나기", ButtonStyle.PRIMARY, onClick = go)
}

/** 검색창 모양 (C01-W 오른쪽 위). 누르면 검색 탭으로 */
fun Scr.searchField(x: Int, y: Int, w: Int) {
    c.box(x, y, w, 96, Ink.WHITE, 48f, Ink.ORANGE, 3f)
    c.box(x + 16, y + 16, 64, 64, Ink.ORANGE, 32f)
    c.icon(x + 28, y + 28, 40, R.drawable.ic_mic, Ink.ON_PRIMARY)
    c.text(x + 100, y + 24, "장소를 검색해 보세요", 32, Ink.GRAY, w = w - 190)
    c.icon(x + w - 72, y + 26, 44, R.drawable.ic_search, Ink.BUTTON_TEXT)
    c.tap(x, y, w, 96, "장소 검색", 48f) { host.goTab(com.peta.car.ui.NavTab.SEARCH) }
}

// ---------------- C01-R 홈 · 여행 중 (재탑승) ----------------

fun Scr.c01rHomeTrip(plan: TripPlan) {
    c.background(Bg.PLAIN)
    title(160, "${plan.pet.name.withWa()} 함께 여행 중이에요")
    // 여정은 흰 박스 하나에 담고, 일차 칩은 박스 아래 가운데 (C09와 같은 자리)
    val bx = if (wide) 178 else 58
    val by = if (wide) 268 else 280
    val bw = if (wide) 860 else 724
    val bh = if (wide) 736 else 640
    c.card(bx, by, bw, bh)
    if (wide) c.lottie(1091, 372, 419, 560, Lotties.BORI_SUNGLASSES, rotation = -5f)
    // 1/3: 도착 카드(y≈510)와 겹치지 않게 작게, 출발 · 경유 줄 오른쪽에
    else c.lottie(628, 296, 132, 176, Lotties.BORI_SUNGLASSES, rotation = -5f)
    val x0 = bx + 32
    val y0 = by + if (wide) 60 else 50
    if (ui.tripDay == 2) tripDay2(plan, x0, y0, bx, bw) else tripDay1(plan, x0, y0, cardW = if (wide) 646 else 600)
    val cw = dayChipsWidth()
    dayChips(bx + (bw - cw) / 2, by + bh - if (wide) 96 else 88)
    // 검색 「여행하기 좋은 곳」에서 고르면 여행 계획의 목적지를 바꾼다 (SearchScreens.usePlace)
    footer("목적지 변경하기", ButtonStyle.MUTED) {
        ui.searchKind = SearchKind.TRAVEL
        ui.query = ""
        ui.category = null
        ui.selectedId = null
        host.goTab(com.peta.car.ui.NavTab.SEARCH)
    }
}

/** 1일차: 출발 · 경유는 한 줄, 지금 가는 도착지는 주황 카드로 강조 */
private fun Scr.tripDay1(plan: TripPlan, x0: Int, y0: Int, cardW: Int) {
    val stop = s.pendingStop
    // 여정: 지난 곳은 회색
    val y = if (stop != null) y0 + (if (wide) 258 else 214) else y0 + (if (wide) 124 else 110)
    c.box(x0 + 12, y0 + 12, 4, y - y0, Ink.ROUTE_DONE)
    c.routeDot(x0, y0, done = true)
    c.text(x0 + 56, y0 - 6, "출발", 28, Ink.FAINT, Weight.MEDIUM)
    c.text(x0 + 146, y0 - 12, plan.origin.ifBlank { "현재 위치" }, 36, Ink.FAINT, Weight.MEDIUM)
    if (stop != null) {
        val passed = s.waypointVisited
        val sy = y0 + if (wide) 90 else 80
        c.routeDot(x0, sy, done = passed)
        c.text(x0 + 56, sy - 6, "경유", 28, if (passed) Ink.FAINT else Ink.GRAY, Weight.MEDIUM)
        c.text(x0 + 146, sy - 12, shortName(stop.place), 36, if (passed) Ink.FAINT else Ink.TITLE, if (passed) Weight.MEDIUM else Weight.BOLD)
    }
    val cardX = x0 + 56
    val cardH = if (wide) 300 else 272
    c.card(cardX, y - 34, cardW, cardH, Ink.ORANGE, 4f, fill = Ink.PEACH)
    c.routeDot(x0, y)
    c.cardLabel(cardX + 28, y - 2, "도착")
    // 1/3은 카드 폭이 좁아 이름을 한 단계 줄인다
    c.text(cardX + 28, y + 40, plan.destination.place.name, if (wide) 48 else 40, Ink.TITLE, Weight.BOLD, w = cardW - 56, maxLines = 1)
    // 체크인 시각은 모바일에서 아직 오지 않아 샘플 숙소에만 시안 값을 쓴다. 목적지를 바꾸면 주소(시 · 구 · 동까지)를 보여 준다.
    val dest = plan.destination.place
    val sub = if (dest.id == SampleTrip.plan.destination.place.id) "16:00 체크인"
    else shortAddress(dest.address).ifBlank { dest.category.ifBlank { "목적지" } }
    c.text(cardX + 28, y + 116, sub, 32, Ink.BODY, w = cardW - 56, maxLines = 1)
    // 경유지를 아직 안 들렀으면 경유지까지 + 경유지에서 목적지까지
    val destKm = plan.destination.place.routeKm ?: 0.0
    val remainKm = if (stop != null && !s.waypointVisited) (s.legRemainKm ?: stop.place.routeKm ?: 0.0) + destKm
    else s.legRemainKm ?: destKm
    val remainMin = s.remainMinutes?.takeIf { it > 0 } ?: etaForKm(remainKm)
    c.text(cardX + 28, y + 170, "${hm(remainMin)} | ${km(remainKm)} 남음", 32, Ink.ORANGE_TEXT, Weight.BOLD)
}

/** "경기도 성남시 중원구 성남대로 997" → "경기도 성남시 중원구 성남대로" (앞 네 마디, 괄호 · 번지 제외) */
internal fun shortAddress(address: String): String =
    address.substringBefore("(").trim().split(" ").filter { it.isNotBlank() && !it.first().isDigit() }.take(4).joinToString(" ")

/** 2일차: 아직 움직이지 않으니 강조 없이 같은 크기의 줄로만 (일정은 샘플 [가정]) */
private fun Scr.tripDay2(plan: TripPlan, x0: Int, y0: Int, bx: Int, bw: Int) {
    val rows = DAY2_STOPS.mapIndexed { i, stop ->
        val label = when (i) { 0 -> "출발"; DAY2_STOPS.lastIndex -> "도착"; else -> "경유" }
        Triple(label, stop.place ?: plan.origin.ifBlank { "집" }, "${stop.time} ${stop.what}")
    }
    val gap = 120
    c.box(x0 + 12, y0 + 12, 4, gap * (rows.size - 1), Ink.ROUTE_DONE)
    rows.forEachIndexed { i, (label, name, detail) ->
        val ry = y0 + i * gap
        c.routeDot(x0, ry)
        c.text(x0 + 56, ry - 6, label, 28, Ink.GRAY, Weight.MEDIUM)
        // 장소 이름은 1일차 경유지와 같은 굵기
        c.text(x0 + 146, ry - 12, name, 36, Ink.TITLE, Weight.BOLD, w = if (wide) 640 else 400)
        c.text(x0 + 146, ry + 38, detail, 28, Ink.SUB)
    }
    c.centerIn(bx, bw, y0 + gap * rows.size, "2일차 길 안내는 내일 출발할 때 시작해요", 28, Ink.SUB, Weight.REGULAR)
}

/** 앞뒤 글자를 다르게 (예: "가평휴게소" Bold + "에 도착했어요" Medium) */
fun Scr.twoTone(lead: String, tail: String, size: Int, leadColor: Int = Ink.TITLE, tailColor: Int = Ink.TITLE,
                leadWeight: Weight = Weight.BOLD, tailWeight: Weight = Weight.MEDIUM) =
    c.rich(Part(lead, leadColor, leadWeight), Part(tail, tailColor, tailWeight), size = size)
