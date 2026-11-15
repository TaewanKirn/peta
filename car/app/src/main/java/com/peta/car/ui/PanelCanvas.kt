package com.peta.car.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.TextView
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.peta.car.R
import kotlin.math.min
import kotlin.math.roundToInt

enum class Bg { WARM, PLAIN, GRAY }
enum class Align { LEFT, CENTER, RIGHT }
enum class ButtonStyle { PRIMARY, SECONDARY, MUTED, DANGER, DANGER_OUTLINE }

/** 글자 한 조각 (색·굵기·크기가 섞인 문장용) */
data class Part(val text: String, val color: Int, val weight: Weight, val size: Int? = null)

/**
 * Figma 패널 좌표(1/3 = 840×1184, 2/3 확장 = 1700×1184)로 그리는 화면.
 * 창 크기에 맞춰 비율을 유지해 줄이고, 모든 좌표·글자 크기는 Figma 값을 그대로 쓴다.
 * motion=false(주행 중)이면 로띠는 첫 프레임에 멈춘다 (Pleos 주의 분산 지침).
 */
@SuppressLint("ViewConstructor")
class PanelCanvas(
    context: Context,
    val scale: Float,
    val wide: Boolean,
    val motion: Boolean,
) : FrameLayout(context) {

    val panelW = if (wide) WIDE_WIDTH else WIDTH
    private val MIN_TOUCH = 76

    fun px(v: Int) = (v * scale).roundToInt()
    fun px(v: Float) = (v * scale).roundToInt()

    private fun lp(x: Int, y: Int, w: Int, h: Int) = LayoutParams(
        if (w > 0) px(w) else LayoutParams.WRAP_CONTENT,
        if (h > 0) px(h) else LayoutParams.WRAP_CONTENT,
    ).apply { leftMargin = px(x); topMargin = px(y) }

    fun background(kind: Bg) {
        background = PanelBackground(kind)
    }

    /** 사각형·원. fill이 0이면 투명 */
    fun box(
        x: Int, y: Int, w: Int, h: Int,
        fill: Int = 0, radius: Float = 0f, stroke: Int = 0, strokeW: Float = 0f,
        alpha: Float = 1f, oval: Boolean = false,
    ): View = View(context).apply {
        background = shape(fill, radius, stroke, strokeW, oval)
        this.alpha = alpha
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(this, lp(x, y, w, h))
    }

    fun shape(fill: Int, radius: Float = 0f, stroke: Int = 0, strokeW: Float = 0f, oval: Boolean = false) =
        GradientDrawable().apply {
            if (oval) shape = GradientDrawable.OVAL else cornerRadius = radius * scale
            setColor(fill)
            if (stroke != 0 && strokeW > 0) setStroke(px(strokeW).coerceAtLeast(1), stroke)
        }

    fun typeface(weight: Weight): Typeface = Fonts.of(context, weight)

    /**
     * 글자. x·y는 Figma 글자 상자의 왼쪽 위. w가 0이면 내용 폭.
     * 자간은 모든 글자 -3% (차량 화면 규칙 9).
     */
    fun text(
        x: Int, y: Int, value: CharSequence, size: Int,
        color: Int = Ink.BODY, weight: Weight = Weight.REGULAR,
        w: Int = 0, align: Align = Align.LEFT, lineHeight: Int = 0, maxLines: Int = 0,
    ): TextView {
        val view = TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_PX, size * scale)
            setTextColor(color)
            typeface = typeface(weight)
            letterSpacing = LETTER_SPACING
            includeFontPadding = false
            gravity = when (align) {
                Align.LEFT -> Gravity.START
                Align.CENTER -> Gravity.CENTER_HORIZONTAL
                Align.RIGHT -> Gravity.END
            }
            val multi = value.contains('\n') || maxLines > 1
            if (multi) setLineHeight(px(if (lineHeight > 0) lineHeight else (size * 1.5f).roundToInt()))
            if (maxLines > 0) {
                this.maxLines = maxLines
                ellipsize = android.text.TextUtils.TruncateAt.END
            } else if (!multi && w > 0) {
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
        }
        // 모든 글자 공통 광학 보정: Noto Sans KR은 한글을 글자 상자 아래쪽에 그려 약 8% 처져 보인다 (lift 참고)
        addView(view, lp(x, y - lift(size), w, 0))
        return view
    }

    /** 패널(또는 x~x+w) 가운데 정렬 */
    fun centered(
        y: Int, value: CharSequence, size: Int, color: Int = Ink.BODY, weight: Weight = Weight.REGULAR,
        x: Int = 0, w: Int = panelW, lineHeight: Int = 0,
    ) = text(x, y, value, size, color, weight, w = w, align = Align.CENTER, lineHeight = lineHeight)

    /** 색·굵기가 섞인 한 문장 */
    fun rich(vararg parts: Part, size: Int): CharSequence {
        val b = SpannableStringBuilder()
        parts.forEach { p ->
            val start = b.length
            b.append(p.text)
            val end = b.length
            b.setSpan(ForegroundColorSpan(p.color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            b.setSpan(TypefaceSpan(typeface(p.weight)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (p.size != null && p.size != size) {
                b.setSpan(AbsoluteSizeSpan(px(p.size)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return b
    }

    /** 글자 폭(Figma 단위) */
    fun measure(value: String, size: Int, weight: Weight): Int {
        val paint = TextPaint().apply {
            typeface = typeface(weight)
            textSize = size.toFloat()
            letterSpacing = LETTER_SPACING
        }
        return paint.measureText(value).roundToInt()
    }

    /** 라인 아이콘 (assets/line → vector drawable). tint로 색을 준다. */
    fun icon(x: Int, y: Int, size: Int, res: Int, tint: Int, alpha: Float = 1f, rotation: Float = 0f): ImageView =
        ImageView(context).apply {
            setImageResource(res)
            imageTintList = ColorStateList.valueOf(tint)
            scaleType = ImageView.ScaleType.FIT_CENTER
            this.alpha = alpha
            this.rotation = rotation
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(this, lp(x, y, size, size))
        }

    fun image(
        x: Int, y: Int, w: Int, h: Int, res: Int,
        fit: ImageView.ScaleType = ImageView.ScaleType.FIT_CENTER,
        radius: Float = 0f, topRadiusOnly: Boolean = false, rotation: Float = 0f, alpha: Float = 1f,
    ): ImageView = ImageView(context).apply {
        setImageResource(res)
        scaleType = fit
        this.rotation = rotation
        this.alpha = alpha
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        roundCorners(this, radius, topRadiusOnly)
        addView(this, lp(x, y, w, h))
    }

    private fun roundCorners(view: View, radius: Float, topRadiusOnly: Boolean) {
        if (radius <= 0) return
        val r = radius * scale
        view.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(v: View, outline: android.graphics.Outline) {
                val extra = if (topRadiusOnly) r.roundToInt() else 0
                outline.setRoundRect(0, 0, v.width, v.height + extra, r)
            }
        }
        view.clipToOutline = true
    }

    /**
     * 원격 사진. 받아 오는 동안은 투명이라 아래에 깐 placeholder가 보이고, 도착하면 부드럽게 나타난다.
     * 실패하면 아무것도 바꾸지 않는다.
     */
    fun remoteImage(
        x: Int, y: Int, w: Int, h: Int, url: String,
        radius: Float = 0f, topRadiusOnly: Boolean = false,
    ): ImageView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        roundCorners(this, radius, topRadiusOnly)
        addView(this, lp(x, y, w, h))
        val cached = RemoteImages.cached(url)
        if (cached != null) {
            setImageBitmap(cached)
        } else {
            alpha = 0f
            RemoteImages.load(url, px(w)) { bitmap ->
                if (bitmap != null && isAttachedToWindow) {
                    setImageBitmap(bitmap)
                    animate().alpha(1f).setDuration(200).start()
                }
            }
        }
    }

    /**
     * 3D 보리·오브젝트 로띠. x·y·w·h는 Figma 인스턴스 상자(= 그림 영역).
     * 기본은 한 번만 재생(렉 방지). loop=true면 반복. motion=false면 첫 프레임에 멈춘다.
     */
    fun lottie(
        x: Int, y: Int, w: Int, h: Int, art: LottieArt,
        loop: Boolean = false, rotation: Float = 0f, alpha: Float = 1f, play: Boolean = motion,
    ): LottieAnimationView {
        val s = min(w / art.w.toFloat(), h / art.h.toFloat())
        val left = x + (w - art.w * s) / 2f - art.x * s
        val top = y + (h - art.h * s) / 2f - art.y * s
        val view = LottieAnimationView(context).apply {
            setAnimation(art.raw)
            repeatCount = if (loop) LottieDrawable.INFINITE else 0
            this.alpha = alpha
            this.rotation = rotation
            pivotX = (art.x + art.w / 2f) * s * scale
            pivotY = (art.y + art.h / 2f) * s * scale
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            if (play) playAnimation() else progress = 0f
        }
        addView(view, LayoutParams(px(art.canvasW * s), px(art.canvasH * s)).apply {
            leftMargin = px(left); topMargin = px(top)
        })
        return view
    }

    /** 글자 없는 터치 영역 */
    fun tap(x: Int, y: Int, w: Int, h: Int, label: String, radius: Float = 48f, enabled: Boolean = true, onClick: () -> Unit): View {
        val view = View(context).apply {
            contentDescription = label
            isEnabled = enabled
            isClickable = enabled
            foreground = RippleDrawable(ColorStateList.valueOf(0x22000000), null, shape(Ink.WHITE, radius))
            setOnClickListener { onClick() }
        }
        addView(view, lp(x, y, w, h))
        return view
    }

    /** 플랫 버튼 (차량 화면 규칙 8). 모서리는 높이의 절반. */
    fun button(
        x: Int, y: Int, w: Int, h: Int, label: String, style: ButtonStyle,
        enabled: Boolean = true, iconRes: Int? = null, radius: Float = h / 2f, onClick: () -> Unit,
    ) {
        val (fill, stroke, ink) = when (style) {
            ButtonStyle.PRIMARY -> Triple(Ink.ORANGE, 0, Ink.ON_PRIMARY)
            ButtonStyle.SECONDARY -> Triple(Ink.WHITE, Ink.BUTTON_LINE, Ink.BUTTON_TEXT)
            ButtonStyle.MUTED -> Triple(Ink.MUTED_FILL, 0, Ink.BUTTON_TEXT)
            ButtonStyle.DANGER -> Triple(Ink.RED, 0, Ink.ON_COLOR)
            ButtonStyle.DANGER_OUTLINE -> Triple(Ink.WHITE, Ink.PINK_STROKE, Ink.RED_TEXT)
        }
        val a = if (enabled) 1f else 0.4f
        box(x, y, w, h, fill, radius, stroke, if (stroke != 0) 3f else 0f, alpha = a)
        val size = 32
        val textW = measure(label, size, Weight.BOLD)
        val textH = 46
        if (iconRes != null) {
            val group = 40 + 12 + textW
            val gx = x + (w - group) / 2
            icon(gx, y + (h - 40) / 2, 40, iconRes, ink, alpha = a)
            text(gx + 52, y + (h - textH) / 2, label, size, ink, Weight.BOLD).alpha = a
        } else {
            text(x, y + (h - textH) / 2, label, size, ink, Weight.BOLD, w = w, align = Align.CENTER).alpha = a
        }
        // 72px 버튼도 터치 영역은 AAOS 최소 76dp 이상
        val th = maxOf(h, MIN_TOUCH)
        tap(x, y - (th - h) / 2, w, th, label, radius, enabled, onClick)
    }

    /** 필터 칩. 돌려주는 값은 칩 폭 */
    /**
     * Noto Sans KR은 글자 상자 안에서 한글을 아래쪽에 그린다(상자를 가운데 둬도 글자가 약 8% 아래로 보임, 2026-11-13 C12에서 측정).
     * [text]가 모든 글자를 이만큼 올린다. 화면 코드는 상자 기준 좌표를 그대로 쓰면 된다.
     */
    fun lift(size: Int): Int = (size * 0.08f).roundToInt()

    /** 한 줄 글자 상자 높이 (includeFontPadding = false, Noto Sans KR ≈ 1.45em. 32 → 46) */
    fun lineBox(size: Int): Int = (size * 1.45f).roundToInt()

    fun chip(x: Int, y: Int, label: String, on: Boolean, onClick: (() -> Unit)? = null): Int {
        val size = 32
        val weight = if (on) Weight.BOLD else Weight.MEDIUM
        val pad = if (on) 28 else 30
        val w = measure(label, size, weight) + pad * 2
        val h = if (on) 64 else 68
        if (on) box(x, y, w, h, Ink.CHIP_ON, 32f) else box(x, y, w, h, Ink.WHITE, 32f, Ink.CHIP_LINE, 2f)
        text(x, y + (h - 46) / 2, label, size, if (on) Ink.ON_CHIP else Ink.BUTTON_TEXT, weight, w = w, align = Align.CENTER)
        // 터치 영역은 보이는 칩보다 크게: AAOS 최소 76dp (칩 줄 간격이 80이라 76까지만)
        onClick?.let { val th = maxOf(h, MIN_TOUCH); tap(x, y - (th - h) / 2, w, th, label, 32f, onClick = it) }
        return w
    }

    /** 체크 표시 (준비물·추천 카드). 켜짐 = 주황 원 + 흰 체크 */
    fun check(x: Int, y: Int, size: Int, on: Boolean) {
        if (on) {
            box(x, y, size, size, Ink.ORANGE, size * 0.47f)
            val inner = (size * 0.5f).roundToInt()
            icon(x + (size - inner) / 2, y + (size - inner) / 2, inner, R.drawable.ic_check, Ink.ON_PRIMARY)
        } else {
            box(x, y, size, size, Ink.WHITE, size * 0.47f, Ink.CHECK_LINE, 2.5f)
        }
    }

    /** 듣는 중 마이크 (C05-V · C06) */
    fun mic(x: Int, y: Int, size: Int) {
        box(x, y, size, size, Ink.MIC_RING_OUT, oval = true)
        val mid = (size * 0.786f).roundToInt()
        box(x + (size - mid) / 2, y + (size - mid) / 2, mid, mid, Ink.MIC_RING_IN, oval = true)
        val core = (size * 0.571f).roundToInt()
        box(x + (size - core) / 2, y + (size - core) / 2, core, core, Ink.ORANGE, oval = true)
        val ic = (size * 0.238f).roundToInt()
        icon(x + (size - ic) / 2, y + (size - ic) / 2, ic, R.drawable.ic_mic, Ink.ON_PRIMARY)
    }

    /** 좌절 선 (실패 화면의 보리 머리 위) */
    fun sadLines(x: Int, y: Int, unit: Float) {
        val bars = listOf(0 to 20, 24 to 4, 48 to 12, 72 to 0, 96 to 20)
        val heights = listOf(52, 76, 64, 84, 56)
        bars.forEachIndexed { i, (dx, dy) ->
            box(x + (dx * unit).roundToInt(), y + (dy * unit).roundToInt(), (5 * unit).roundToInt().coerceAtLeast(3),
                (heights[i] * unit).roundToInt(), Ink.SAD_LINE, 2.6f * unit, alpha = 0.55f)
        }
    }

    /** 직접 만든 뷰를 Figma 좌표에 놓는다 */
    fun place(view: View, x: Int, y: Int, w: Int, h: Int): View {
        addView(view, lp(x, y, w, h))
        return view
    }

    /** 가로로 넘기는 영역. 안쪽 좌표는 (0,0)부터 */
    fun hScroll(x: Int, y: Int, w: Int, h: Int, contentW: Int): PanelCanvas {
        val inner = PanelCanvas(context, scale, wide, motion)
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            addView(inner, LayoutParams(px(contentW), px(h)))
        }
        addView(scroll, lp(x, y, w, h))
        return inner
    }

    /** 패널 배경: Figma 그라디언트(WARM = 왼쪽 위 방사형 두 개, PLAIN = 위→아래 크림, GRAY = 실패 화면) */
    private inner class PanelBackground(private val kind: Bg) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun draw(canvas: Canvas) {
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            when (kind) {
                Bg.PLAIN -> {
                    val (top, bottom) = if (ThemeMode.dark) 0xFF1A1512.toInt() to 0xFF241A13.toInt() else 0xFFFFFCF9.toInt() to 0xFFFFF1E4.toInt()
                    paint.shader = LinearGradient(0f, 0f, 0f, h,
                        intArrayOf(top, top, bottom), floatArrayOf(0f, 0.45f, 1f),
                        Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, w, h, paint)
                }
                Bg.GRAY -> {
                    paint.shader = if (ThemeMode.dark) LinearGradient(0f, 0f, 0f, h, 0xFF221C17.toInt(), 0xFF2F2823.toInt(), Shader.TileMode.CLAMP)
                    else LinearGradient(0f, 0f, 0f, h, 0xFFF7F5F4.toInt(), 0xFFE3DFDD.toInt(), Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, w, h, paint)
                }
                Bg.WARM -> {
                    paint.shader = null
                    val dark = ThemeMode.dark
                    paint.color = if (dark) 0xFF1A1512.toInt() else 0xFFFFFCF9.toInt()
                    canvas.drawRect(0f, 0f, w, h, paint)
                    // Figma gradientTransform 역산: 중심 (0, 0.05H) 반경 0.75W × 0.42H / 중심 (0, 0.42H) 반경 0.5W × 0.25H
                    glow(canvas, 0f, 0.05f * h, 0.75f * min(w, WIDTH * scale), 0.42f * h, if (dark) 0xBF5A3418.toInt() else 0xBFFFDDBF.toInt())
                    glow(canvas, 0f, 0.42f * h, 0.5f * min(w, WIDTH * scale), 0.25f * h, if (dark) 0x994A3A1A.toInt() else 0x99FFEDB8.toInt())
                }
            }
        }

        private fun glow(canvas: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, color: Int) {
            val shader = RadialGradient(0f, 0f, 1f, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
            shader.setLocalMatrix(Matrix().apply { setScale(rx, ry); postTranslate(cx, cy) })
            paint.shader = shader
            canvas.drawRect(0f, 0f, bounds.width().toFloat(), bounds.height().toFloat(), paint)
        }

        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.OPAQUE
    }

    companion object {
        const val WIDTH = 840
        const val WIDE_WIDTH = 1700
        const val HEIGHT = 1184
        const val LETTER_SPACING = -0.03f
    }
}
