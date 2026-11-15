package com.peta.car

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.peta.car.debug.DebugBridge
import com.peta.car.screens.Scr
import com.peta.car.screens.Screen
import com.peta.car.screens.ScreenHost
import com.peta.car.screens.UiLocal
import com.peta.car.screens.c00Splash
import com.peta.car.screens.c00pConsent
import com.peta.car.screens.c01Home
import com.peta.car.screens.c01rHomeTrip
import com.peta.car.screens.c02Confirm
import com.peta.car.screens.c03Checklist
import com.peta.car.screens.c04Route
import com.peta.car.screens.c04eRouteFail
import com.peta.car.screens.c05Driving
import com.peta.car.screens.c05vStopVoice
import com.peta.car.screens.c06Rest
import com.peta.car.screens.c06eRestRetry
import com.peta.car.screens.c07ArrivedStop
import com.peta.car.screens.c08Resting
import com.peta.car.screens.c09ArrivedDest
import com.peta.car.screens.c10Review
import com.peta.car.screens.c11Emergency
import com.peta.car.screens.c12Search
import com.peta.car.screens.c14Settings
import com.peta.car.trip.TripPhase
import com.peta.car.trip.TripStage
import com.peta.car.ui.ChromeSpec
import com.peta.car.ui.NavTab
import com.peta.car.ui.PanelCanvas
import com.peta.car.ui.chrome
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri

/**
 * 차량 패널 화면. 상태(PetaStore)에 따라 C00~C14를 그린다 (화면 번호는 2026-11-12 기준).
 * 창이 가로로 넓으면(2/3 확장, 1700×1184) 세로 메뉴 레이아웃으로 그린다.
 * 계기판·내비·공조는 시스템 영역이고, 이 앱은 패널만 그린다.
 * 주행 중(D)에는 응급과 C05 「쉬었다 갈 곳 변경/취소」 외에 터치를 받지 않는다.
 */
class MainActivity : AppCompatActivity(), ScreenHost {

    private lateinit var root: FrameLayout
    private val ui = UiLocal()
    private var lastSignature: String? = null
    private val liveUpdates = mutableListOf<(PetaUiState) -> Unit>()
    private var overlayDraw: (() -> Unit)? = null
    private var wasDriving = false

    private val actions get() = PetaStore.actions

    private val permissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { PetaService.start(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.peta.car.ui.ThemeMode.load(this)
        com.peta.car.privacy.Consent.load(this)
        root = FrameLayout(this).apply { setBackgroundColor(0xFFFFFCF9.toInt()) }
        setContentView(root)
        root.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob ->
            if (r - l != or_ - ol || b - t != ob - ot) root.post { rerender() }
        }

        val wanted = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add("android.car.permission.CAR_SPEED")
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissions.launch(wanted.toTypedArray())
        if (PetaConfig.SDK_PROBE) com.peta.car.debug.SdkProbe.run(this)

        // C00 스플래시는 처음 열 때 잠깐 보여 준다
        ui.splashUntil = System.currentTimeMillis() + SPLASH_MILLIS
        later(SPLASH_MILLIS + 50) { rerender() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { PetaStore.state.collect { render(it) } }
        }
        if (PetaConfig.DEBUG_BRIDGE) lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                DebugBridge.applied.collect { cmd -> if (cmd != null && cmd.id > lastDebugId) applyDebugUi(cmd) }
            }
        }
    }

    // ---------------- 플로우 점검 페이지(/admin) ----------------

    private var lastDebugId = 0L

    /** 서비스가 상태를 맞춘 뒤, 화면 안 값(탭·응급·팝업·스플래시)을 맞춘다 */
    private fun applyDebugUi(cmd: DebugBridge.Command) {
        lastDebugId = cmd.id
        val code = cmd.action
        if (!code.startsWith("C") && code != "event:reset" && code != "event:resetApp") return
        ui.emergency = false
        ui.tab = NavTab.HOME
        ui.splashUntil = 0L
        ui.savedReviewPlace = null
        ui.rating = 0
        ui.query = ""
        ui.category = null
        ui.widenKey = null
        ui.selectedId = null
        when (code) {
            "C00" -> {
                ui.splashUntil = System.currentTimeMillis() + SPLASH_MILLIS
                later(SPLASH_MILLIS + 50) { rerender() }
            }
            "C01" -> ui.sampleAccepted = false
            "C02" -> ui.sampleAccepted = true
            "C10S" -> {
                ui.savedReviewPlace = PetaStore.state.value.pendingReview?.placeName
                    ?: PetaStore.state.value.plan?.destination?.place?.name
                ui.savedRating = 5
            }
            "C11" -> {
                ui.emergency = true
                ui.hospitalIndex = 0
            }
            "C12" -> ui.tab = NavTab.SEARCH
            "C13E" -> {
                ui.tab = NavTab.SEARCH
                ui.query = EMPTY_SEARCH_QUERY
            }
            "C14" -> ui.tab = NavTab.SETTINGS
        }
        rerender()
    }

    // ---------------- 화면 선택 ----------------

    private fun screenFor(s: PetaUiState): Screen {
        val plan = s.plan
        val driving = s.vehicle.isDriving
        // 응급은 언제든, 주행 중에도 연다 (규칙 13·24)
        if (ui.emergency && plan != null) return Screen.C11_EMERGENCY
        if (s.stage == TripStage.ON_TRIP && driving) {
            s.restPrompt?.let { return if (it.retrying && it.answer == null) Screen.C06E_REST_RETRY else Screen.C06_REST }
            if (s.stopQuestion != null) return Screen.C05V_STOP_VOICE
            return Screen.C05_DRIVING
        }
        if (plan == null) return Screen.C00_SPLASH
        if (System.currentTimeMillis() < ui.splashUntil) return Screen.C00_SPLASH
        // 위치를 처음 쓰기 전에 동의를 받는다. 주행 중이면 미루고(위치는 보내지 않음) 주차하면 연다.
        if (!driving && (ui.reviewConsent || com.peta.car.privacy.Consent.state == com.peta.car.privacy.Consent.State.UNKNOWN)) {
            return Screen.C00P_CONSENT
        }
        when (ui.tab) {
            NavTab.SEARCH -> return Screen.C12_SEARCH
            NavTab.SETTINGS -> return Screen.C14_SETTINGS
            else -> Unit
        }
        return when (s.stage) {
            TripStage.WELCOME -> when {
                ui.savedReviewPlace != null || s.pendingReview != null -> Screen.C10_REVIEW
                !s.planFromPhone && !ui.sampleAccepted -> Screen.C01_HOME
                else -> Screen.C02_CONFIRM
            }
            TripStage.BRIEFING -> Screen.C03_CHECKLIST
            TripStage.SUMMARY -> Screen.C04_ROUTE
            TripStage.ON_TRIP -> when {
                s.routeFailed && s.phase == TripPhase.DRIVING -> Screen.C04E_ROUTE_FAIL
                s.phase == TripPhase.AT_WAYPOINT -> if (s.resting) Screen.C08_RESTING else Screen.C07_ARRIVED_STOP
                s.phase == TripPhase.AT_DESTINATION -> Screen.C09_ARRIVED_DEST
                // 여행 중 정차해 다시 열었을 때 (재탑승)
                else -> Screen.C01R_HOME_TRIP
            }
        }
    }

    /**
     * 입력창 바깥을 누르면 키보드를 내린다(1113). 터치는 그대로 아래 화면(버튼 등)에 전달한다.
     * 검색창은 매 렌더마다 새로 만들어지므로 화면이 아니라 이 입구에서 한 번에 처리한다.
     */
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
            val focused = currentFocus
            if (focused is android.widget.EditText) {
                val r = android.graphics.Rect()
                focused.getGlobalVisibleRect(r)
                if (!r.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(focused.windowToken, 0)
                    focused.clearFocus()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun render(s: PetaUiState) {
        if (root.width == 0) return
        val driving = s.vehicle.isDriving
        if (driving && !wasDriving) ui.tab = NavTab.HOME // 주행 중에는 탭이 잠긴다
        wasDriving = driving
        val screen = screenFor(s)
        DebugBridge.screen = screen.name
        val wide = root.width.toFloat() / root.height > WIDE_RATIO
        val dark = com.peta.car.ui.ThemeMode.resolve(this)
        root.setBackgroundColor(if (dark) 0xFF1A1512.toInt() else 0xFFFFFCF9.toInt())
        val signature = listOf(
            dark, com.peta.car.ui.ThemeMode.setting, com.peta.car.privacy.Consent.state,
            screen, wide, driving, s.plan, s.planFromPhone, s.syncOffline, s.restPrompt, s.stopQuestion,
            s.pendingStop, s.plannedStop, s.plannedStopDecided, s.waypointVisited, s.routeFailed, s.stage, s.phase,
            s.currentStop, s.resting, s.pendingReview, s.hospitals, s.nearbyRestPlaces, s.placesRev, s.voiceEnabled,
            root.width, root.height,
        ).toString()
        if (signature == lastSignature) {
            liveUpdates.forEach { it(s) }
            return
        }
        lastSignature = signature
        liveUpdates.clear()
        overlayDraw = null

        val panelW = if (wide) PanelCanvas.WIDE_WIDTH else PanelCanvas.WIDTH
        val scale = min(root.width / panelW.toFloat(), root.height / PanelCanvas.HEIGHT.toFloat())
        val c = PanelCanvas(this, scale, wide, motion = !driving)
        root.removeAllViews()
        root.addView(c, FrameLayout.LayoutParams(
            (panelW * scale).toInt(), (PanelCanvas.HEIGHT * scale).toInt(), Gravity.CENTER,
        ))
        val scr = Scr(c, s, ui, this)
        val plan = s.plan
        when (screen) {
            Screen.C00_SPLASH -> scr.c00Splash()
            Screen.C00P_CONSENT -> scr.c00pConsent()
            Screen.C14_SETTINGS -> scr.c14Settings(plan)
            Screen.C12_SEARCH, Screen.C13E_SEARCH_EMPTY -> scr.c12Search()
            Screen.C10_REVIEW -> scr.c10Review()
            else -> if (plan != null) when (screen) {
                Screen.C01_HOME -> scr.c01Home(plan)
                Screen.C01R_HOME_TRIP -> scr.c01rHomeTrip(plan)
                Screen.C02_CONFIRM -> scr.c02Confirm(plan)
                Screen.C03_CHECKLIST -> scr.c03Checklist(plan)
                Screen.C04_ROUTE -> scr.c04Route(plan)
                Screen.C04E_ROUTE_FAIL -> scr.c04eRouteFail()
                Screen.C05_DRIVING -> scr.c05Driving(plan)
                Screen.C05V_STOP_VOICE -> scr.c05vStopVoice()
                Screen.C06_REST -> scr.c06Rest(plan)
                Screen.C06E_REST_RETRY -> scr.c06eRestRetry()
                Screen.C07_ARRIVED_STOP -> scr.c07ArrivedStop(plan)
                Screen.C08_RESTING -> scr.c08Resting(plan)
                Screen.C09_ARRIVED_DEST -> scr.c09ArrivedDest(plan)
                Screen.C11_EMERGENCY -> scr.c11Emergency(plan)
                else -> Unit
            }
        }
        if (screen == Screen.C00_SPLASH) return
        // 동의 화면은 헤더 · 탭 없이 패널 전체를 쓴다 (QR 시트만 덮는다)
        if (screen == Screen.C00P_CONSENT) { overlayDraw?.invoke(); return }

        // 주행 중에는 본문 터치를 막는다 (응급 화면과 C05 변경/취소 버튼만 예외)
        if (driving && !screen.usableWhileDriving) {
            c.place(View(this).apply { isClickable = true }, if (wide) 120 else 0, 120,
                if (wide) panelW - 120 else panelW, if (wide) 1064 else 968)
        }
        c.chrome(ChromeSpec(
            title = screen.title,
            tab = if (ui.emergency) NavTab.NONE else ui.tab,
            driving = driving,
            emergencyOn = ui.emergency,
            onClose = { moveTaskToBack(true) }, // 패널만 닫는다. 다시 열면 보던 화면 (규칙 3)
            onTab = { goTab(it) },
            onEmergency = {
                ui.emergency = !ui.emergency
                if (ui.emergency) {
                    ui.hospitalIndex = 0
                    actions?.refreshNearby()
                }
                rerender()
            },
        ))
        overlayDraw?.invoke()
        liveUpdates.forEach { it(s) }
    }

    // ---------------- ScreenHost ----------------

    override fun rerender() {
        lastSignature = null
        render(PetaStore.state.value)
    }

    override fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun dial(phone: String) {
        try {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone))))
        } catch (e: ActivityNotFoundException) {
            toast("전화 앱을 열 수 없어요")
        }
    }

    override fun closePanel() {
        moveTaskToBack(true)
    }

    override fun goTab(tab: NavTab) {
        ui.emergency = false
        ui.tab = tab
        rerender()
    }

    override fun live(update: (PetaUiState) -> Unit) {
        liveUpdates += update
    }

    override fun overlay(draw: () -> Unit) {
        overlayDraw = draw
    }

    override fun later(delayMillis: Long, block: () -> Unit) {
        lifecycleScope.launch {
            delay(delayMillis)
            block()
        }
    }

    override fun showKeyboard(view: View) {
        view.requestFocus()
        getSystemService(InputMethodManager::class.java)?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private companion object {
        const val SPLASH_MILLIS = 3_000L
        /** C13-E 바로 가기: 어떤 장소 이름과도 겹치지 않는 검색어 */
        const val EMPTY_SEARCH_QUERY = "ㅁㅁㅁ"
        /** 이보다 가로가 넓으면 2/3 확장 레이아웃 (1/3 = 0.71, 2/3 = 1.44) */
        const val WIDE_RATIO = 1.1f
    }
}
