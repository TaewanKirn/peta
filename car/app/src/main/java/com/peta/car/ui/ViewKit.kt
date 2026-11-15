package com.peta.car.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.peta.car.R

/** 차량 화면용 작은 뷰 빌더. 터치 타깃 최소 76dp, 큰 글씨. */
class ViewKit(private val context: Context) {

    fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
    fun color(id: Int) = ContextCompat.getColor(context, id)

    fun rounded(fill: Int, radiusDp: Int = 20, strokeColor: Int? = null) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        strokeColor?.let { setStroke(dp(1), it) }
    }

    fun text(
        value: CharSequence,
        sizeSp: Float = 22f,
        colorId: Int = R.color.text,
        bold: Boolean = false,
        topDp: Int = 0,
    ) = TextView(context).apply {
        text = value
        textSize = sizeSp
        setTextColor(color(colorId))
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(dp(3).toFloat(), 1f)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(topDp) }
    }

    fun title(value: String) = text(value, 30f, bold = true)

    fun card(topDp: Int = 16, paddingDp: Int = 24, fill: Int = R.color.card) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(color(fill))
        setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp))
        layoutParams = matchWidth(topDp)
    }

    fun row(topDp: Int = 0, vararg children: View) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = matchWidth(topDp)
        children.forEach { addView(it) }
    }

    fun column(vararg children: View) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        children.forEach { addView(it) }
    }

    fun weighted(view: View, weight: Float = 1f, startDp: Int = 0): View = view.apply {
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight).apply {
            marginStart = dp(startDp)
        }
    }

    fun spacer(heightDp: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(heightDp))
    }

    fun divider(topDp: Int = 16) = View(context).apply {
        setBackgroundColor(color(R.color.line))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
            topMargin = dp(topDp); bottomMargin = dp(4)
        }
    }

    /** 왼쪽 라벨 + 오른쪽 값 (C01, C03) */
    fun keyValue(key: String, value: String, topDp: Int = 14) = row(
        topDp,
        text(key, 20f, R.color.subtext).apply {
            layoutParams = LinearLayout.LayoutParams(dp(150), ViewGroup.LayoutParams.WRAP_CONTENT)
        },
        weighted(text(value, 22f)),
    )

    fun primaryButton(label: String, onClick: () -> Unit) = button(label, R.color.primary, R.color.card, null, onClick)

    fun secondaryButton(label: String, onClick: () -> Unit) =
        button(label, R.color.card, R.color.text, R.color.line, onClick)

    fun softButton(label: String, onClick: () -> Unit) =
        button(label, R.color.primarySoft, R.color.primary, null, onClick)

    fun dangerButton(label: String, onClick: () -> Unit) = button(label, R.color.danger, R.color.card, null, onClick)

    private fun button(label: String, fill: Int, textColor: Int, stroke: Int?, onClick: () -> Unit) =
        MaterialButton(context).apply {
            text = label
            textSize = 22f
            isAllCaps = false
            minHeight = dp(76)
            insetTop = 0
            insetBottom = 0
            cornerRadius = dp(18)
            backgroundTintList = ContextCompat.getColorStateList(context, fill)
            setTextColor(color(textColor))
            stroke?.let {
                strokeColor = ContextCompat.getColorStateList(context, it)
                strokeWidth = dp(1)
            }
            setOnClickListener { onClick() }
            layoutParams = matchWidth(16)
        }

    /** 진행 막대 (C04, C08). 이전 값(from)에서 새 값(ratio)까지 부드럽게 차오른다. */
    fun progress(ratio: Float, from: Float = ratio, topDp: Int = 16, durationMs: Long = 500) = FrameLayout(context).apply {
        background = rounded(color(R.color.track), 8)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(14)).apply { topMargin = dp(topDp) }
        val fill = View(context).apply { background = rounded(color(R.color.primary), 8) }
        addView(fill, FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT))
        post {
            val total = width
            ValueAnimator.ofFloat(from.coerceIn(0f, 1f), ratio.coerceIn(0f, 1f)).apply {
                duration = durationMs
                interpolator = LinearInterpolator()
                addUpdateListener { a ->
                    val w = (total * (a.animatedValue as Float)).toInt()
                    fill.layoutParams = fill.layoutParams.apply { width = w }
                }
                start()
            }
        }
    }

    /** 원형 아이콘 (이모지) */
    fun badge(emoji: String, sizeDp: Int = 64, fill: Int = R.color.peach) = TextView(context).apply {
        text = emoji
        textSize = sizeDp / 2.6f
        gravity = Gravity.CENTER
        background = rounded(color(fill), sizeDp / 2)
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    fun matchWidth(topDp: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(topDp) }
}
