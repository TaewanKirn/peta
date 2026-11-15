package com.peta.car.ui

import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.peta.car.R

/** 진행 막대. 화면을 다시 그리지 않고 현재 값에서 다음 값까지 부드럽게 이어서 차오른다. */
class Gauge(context: Context, kit: ViewKit) : FrameLayout(context) {

    private val fill = View(context)
    private var current = 0f
    private var animator: ValueAnimator? = null

    init {
        background = kit.rounded(kit.color(R.color.track), 8)
        fill.background = kit.rounded(kit.color(R.color.primary), 8)
        addView(fill, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kit.dp(14)).apply {
            topMargin = kit.dp(16)
        }
    }

    fun animateTo(target: Float, durationMs: Long) {
        val to = target.coerceIn(0f, 1f)
        animator?.cancel()
        animator = ValueAnimator.ofFloat(current, to).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener {
                current = it.animatedValue as Float
                applyWidth()
            }
            start()
        }
    }

    private fun applyWidth() {
        if (width == 0) {
            post { applyWidth() }
            return
        }
        val w = (width * current).toInt()
        fill.layoutParams = fill.layoutParams.apply { width = w }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
