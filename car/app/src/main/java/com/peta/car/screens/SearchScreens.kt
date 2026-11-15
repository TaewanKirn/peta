package com.peta.car.screens

import android.text.InputType
import android.util.TypedValue
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.peta.car.R
import com.peta.car.place.Place
import com.peta.car.place.PlaceRepository
import com.peta.car.trip.TripStage
import com.peta.car.trip.withEulReul
import com.peta.car.trip.withRo
import com.peta.car.trip.withWa
import com.peta.car.place.PlaceType
import com.peta.car.ui.Bg
import com.peta.car.ui.Ink
import com.peta.car.ui.NavTab
import com.peta.car.ui.PanelCanvas
import com.peta.car.ui.Weight

// ---------------- C12 검색 · 추천 / C13 검색 결과 / C13-E 검색 결과 없음 ----------------
// 검색 탭은 「쉬어가기 좋은 곳」과 「여행하기 좋은 곳」으로 나누고, 주 버튼 이름은 탭마다 다르다 (규칙 18).
// 장소는 서버(관광공사)에서 받은 곳을 먼저, 모자라면 샘플로 채운다. 검색어를 넣으면 서버에서 다시 찾아 온다.

private val repository = PlaceRepository.shared

private val REST_CHIPS_NARROW = listOf("전체", "휴게소", "카페", "식당", "공원")
private val REST_CHIPS_WIDE = REST_CHIPS_NARROW + listOf("숙소", "산책로", "캠핑장", "바다", "계곡")
private val TRAVEL_CHIPS = listOf("전체", "숙소", "산책로", "캠핑장", "바다", "계곡")

private fun Scr.candidates(): List<Place> {
    val base = when (ui.searchKind) {
        SearchKind.REST -> repository.restCandidates(ui.query, ui.widenKey)
        SearchKind.TRAVEL -> repository.travelCandidates(ui.query, ui.widenKey)
    }
    // 홈과 같은 반경: [범위 넓히기]로 넓혀 받은 목록 중 이 탭에 맞는 곳을 더한다 (검색어가 없을 때)
    val wider = if (ui.query.isBlank() && ui.radiusKm > RADIUS_STEPS.first())
        repository.wideNearby(ui.radiusKm).filter {
            if (ui.searchKind == SearchKind.REST) it.type == PlaceType.DOG_REST else it.type == PlaceType.DESTINATION || it.category == "공원"
        } else emptyList()
    // 쇼핑 같은 「기타」는 홈처럼 뺀다
    return (base + wider).distinctBy { it.id }.filter { it.category != "기타" }
        .let { if (wider.isEmpty()) it else it.sortedBy { p -> p.routeKm ?: Double.MAX_VALUE } }
}

/** 검색창 아래 한 줄: 「근방 20km, 리치와 쉬어가기 좋은 곳 [범위 넓히기]」 · 검색어가 있으면 「‘카페’ 검색 결과 · 가까운 순」 */
private fun Scr.radiusRow(y: Int) {
    val x = if (wide) left else 58
    val size = if (wide) 30 else 26
    if (ui.query.isNotBlank()) {
        c.text(x, y, "‘${ui.query}’ 검색 결과 · 가까운 순", size, Ink.SUB)
        return
    }
    val pet = s.plan?.pet?.name ?: "반려견"
    val what = if (ui.searchKind == SearchKind.REST) "쉬어가기" else "여행하기"
    val sub = "근방 ${ui.radiusKm}km, ${pet.withWa()} $what 좋은 곳"
    c.text(x, y, sub, size, Ink.SUB)
    val r = ui.radiusKm
    val state = repository.wideState(r)
    val pending = r > RADIUS_STEPS.first() && state == PlaceRepository.WideState.LOADING
    val failed = state == PlaceRepository.WideState.FAILED
    val next = RADIUS_STEPS.firstOrNull { it > r }
    val label = when { pending -> "넓히는 중…"; failed -> "다시 시도"; next != null -> "범위 넓히기"; else -> return }
    val text = size - 2
    val bh = if (wide) 52 else 46
    val bw = c.measure(label, text, Weight.MEDIUM) + 40
    val bx = x + c.measure(sub, size, Weight.REGULAR) + 20
    val by = y + c.lineBox(size) / 2 - bh / 2
    c.box(bx, by, bw, bh, Ink.CARD_LINE, 12f, alpha = if (pending) 0.5f else 1f)
    c.centerIn(bx, bw, by + (bh - c.lineBox(text)) / 2, label, text, Ink.SUB, Weight.MEDIUM)
    if (!pending) c.tap(bx - 8, by - (96 - bh) / 2, bw + 16, 96, label, 48f) {
        if (failed) actions?.widenNearby(r)
        else if (next != null) {
            ui.radiusKm = next
            ui.selectedId = null
            val st = repository.wideState(next)
            if (st == PlaceRepository.WideState.NONE || st == PlaceRepository.WideState.FAILED) actions?.widenNearby(next)
        }
        host.rerender()
    }
}

fun Scr.searchResults(): List<Place> =
    repository.fitting(repository.search(candidates(), ui.query, ui.category, ui.widenKey), s.plan?.pet)

fun Scr.c12Search() {
    val results = searchResults()
    val empty = results.isEmpty()
    c.background(if (empty) Bg.GRAY else Bg.PLAIN)
    segment()
    searchPanel(empty)
    if (empty) {
        // C13-E. 반경 20km에서 못 찾으면 [범위 넓혀 찾기]로 낱말 전국 조회를 한다.
        val key = ui.widenKey
        val pending = key != null && repository.widenedHits(key) == null
        val cx = if (wide) 120 else 0
        val cw = c.panelW - cx
        val title = when {
            pending -> "넓은 범위에서 찾고 있어요"
            key != null -> "넓혀 봐도 찾지 못했어요"
            else -> "찾는 곳이 없어요"
        }
        val sub = if (key != null) "전국 반려견 동반 장소에서 찾았어요" else "반려견과 갈 수 있는 곳만 보여 드려요"
        // 강아지 그래픽을 위에, 글자를 아래에
        when {
            pending && wide -> loadingBori(cx + cw / 2 - 120, 470, 240, 320)
            pending -> loadingBori(cx + cw / 2 - 75, 470, 150, 200)
            wide -> worriedBori(cx + cw / 2 - 120, 470, 240, 320)
            else -> worriedBori(cx + cw / 2 - 75, 470, 150, 200)
        }
        c.centerIn(cx, cw, if (wide) 820 else 700, title, 48, Ink.TITLE, Weight.BOLD)
        c.centerIn(cx, cw, if (wide) 896 else 776, sub, 32, Ink.SUB, Weight.REGULAR)
        footer2(if (key == null) "범위 넓혀 찾기" else "추천 장소 보기", "다시 검색",
            onPrimary = {
                if (key == null) {
                    // 칩으로 걸러 둔 분류는 풀어야 넓힌 결과가 가려지지 않는다
                    val word = ui.query.ifBlank { ui.category ?: "반려견" }
                    ui.category = null
                    ui.widenKey = word
                    actions?.widenSearch(word)
                } else {
                    ui.query = ""
                    ui.category = null
                    ui.widenKey = null
                }
                host.rerender()
            },
            onSecondary = {
                ui.widenKey = null
                ui.focusSearch = true
                host.rerender()
            })
        return
    }
    // C12(검색어 없음) · C13(검색 결과): 카드를 가로로 넘긴다
    radiusRow(if (wide) 452 else 446)
    val (sheetX, sheetW, gap, sheetY) = if (wide) listOf(178, 351, 20, 516) else listOf(58, 315, 22, 498)
    // 2/3 화면은 세로 메뉴(120) 오른쪽만 넘긴다
    val scrollX = if (wide) 120 else 0
    val startX = sheetX - scrollX
    val contentW = startX + results.size * (sheetW + gap) + 36
    val row = c.hScroll(scrollX, sheetY - 20, c.panelW - scrollX, 480, contentW)
    results.forEachIndexed { i, place ->
        row.placeSheet(startX + i * (sheetW + gap), 20, sheetW, place, place.id == ui.selectedId) {
            ui.selectedId = if (ui.selectedId == place.id) null else place.id
            host.rerender()
        }
    }
    val selected = results.firstOrNull { it.id == ui.selectedId }
    val label = if (ui.searchKind == SearchKind.REST) "경유지로 추가" else "목적지로 설정"
    footer(label, enabled = selected != null) { selected?.let { usePlace(it) } }
}

private fun Scr.usePlace(place: Place) {
    when (ui.searchKind) {
        SearchKind.REST -> {
            if (s.stage == TripStage.ON_TRIP) actions?.chooseStop(place) else actions?.planStop(place)
            host.toast("${shortName(place).withEulReul()} 쉬었다 갈 곳으로 정했어요")
        }
        SearchKind.TRAVEL -> {
            if (s.stage != TripStage.ON_TRIP) {
                host.toast("여행 계획은 모바일 앱에서 만들 수 있어요")
                return
            }
            actions?.changeDestination(place)
            host.toast("목적지를 ${shortName(place).withRo()} 바꿨어요")
        }
    }
    ui.selectedId = null
    host.goTab(NavTab.HOME)
}

private fun Scr.segment() {
    val x = left
    val w = c.panelW - 58 - x
    val half = (w - 12) / 2
    c.box(x, 144, w, 80, Ink.SEGMENT, 40f)
    val onX = if (ui.searchKind == SearchKind.REST) x + 6 else x + 6 + half
    c.box(onX, 150, half, 68, Ink.WHITE, 34f)
    listOf(SearchKind.REST to "쉬어가기 좋은 곳", SearchKind.TRAVEL to "여행하기 좋은 곳").forEachIndexed { i, (kind, label) ->
        val on = ui.searchKind == kind
        val hx = x + 6 + i * half
        c.centerIn(hx, half, 161, label, 32, if (on) Ink.TITLE else Ink.GRAY, if (on) Weight.BOLD else Weight.MEDIUM)
        c.tap(hx, 150, half, 68, label, 34f) {
            if (ui.searchKind != kind) {
                ui.searchKind = kind
                ui.category = null
                ui.widenKey = null
                ui.selectedId = null
                host.rerender()
            }
        }
    }
}

private fun Scr.searchPanel(empty: Boolean) {
    val x = left
    val w = c.panelW - 58 - x
    c.box(x, 244, w, 188, Ink.WHITE, 40f, if (empty) Ink.SEARCH_OFF else Ink.ORANGE, 3f)
    // 마이크: 말로 검색 (정차 중)
    c.box(x + 16, 260, 64, 64, Ink.ORANGE, 32f)
    c.icon(x + 28, 272, 40, R.drawable.ic_mic, Ink.ON_PRIMARY)
    c.tap(x + 16, 260, 64, 64, "말로 검색", 32f) {
        host.toast("듣고 있어요")
        actions?.listenQuery { q ->
            if (q == null) host.toast("잘 못 들었어요. 다시 말해 주세요")
            else {
                ui.query = q
                ui.selectedId = null
                ui.widenKey = null
                actions?.searchPlaces(q)
                host.rerender()
            }
        }
    }
    val field = searchInput(c)
    c.place(field, x + 100, 258, w - 190, 64)
    c.icon(x + w - 72, 270, 44, R.drawable.ic_search, Ink.BUTTON_TEXT)
    c.tap(x + w - 84, 258, 68, 68, "검색", 34f) { submit(field.text.toString()) }
    if (ui.focusSearch) {
        ui.focusSearch = false
        field.post { host.showKeyboard(field) }
    }
    c.box(x + 20, 340, w - 40, 2, Ink.SEGMENT)
    val chips = when {
        ui.searchKind == SearchKind.TRAVEL -> TRAVEL_CHIPS
        wide -> REST_CHIPS_WIDE
        else -> REST_CHIPS_NARROW
    }
    chipRow(x + 20, 354, w - 40, chips, ui.category ?: "전체") {
        ui.category = it.takeIf { label -> label != "전체" }
        ui.selectedId = null
        ui.widenKey = null
        host.rerender()
    }
}

private fun Scr.searchInput(c: PanelCanvas) = EditText(c.context).apply {
    background = null
    setPadding(0, 0, 0, 0)
    gravity = android.view.Gravity.CENTER_VERTICAL
    setTextSize(TypedValue.COMPLEX_UNIT_PX, 32 * c.scale)
    typeface = c.typeface(Weight.REGULAR)
    letterSpacing = PanelCanvas.LETTER_SPACING
    setTextColor(Ink.TITLE)
    setHintTextColor(Ink.GRAY)
    hint = "장소를 검색해 보세요"
    setText(ui.query)
    isSingleLine = true
    inputType = InputType.TYPE_CLASS_TEXT
    // 가로로 넓은 차량 화면에서는 키보드가 올라올 때 입력창만 전체 화면으로 뜨는(extract UI) 일이 있다. 끈다.
    imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
    // inputType · singleLine을 바꾸면 줄 배치가 다시 잡혀 문구가 위로 붙는다. 마지막에 다시 가운데로 맞춘다.
    includeFontPadding = false
    gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START
    setOnEditorActionListener { v, actionId, event ->
        val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
        if (actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
            (context.getSystemService(InputMethodManager::class.java))?.hideSoftInputFromWindow(v.windowToken, 0)
            submit(text.toString())
            true
        } else false
    }
}

private fun Scr.submit(text: String) {
    ui.query = text.trim()
    ui.selectedId = null
    ui.widenKey = null
    actions?.searchPlaces(ui.query)
    host.rerender()
}
