package com.example.regiontranslator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 드래그(이동) + 리사이즈(모서리 핸들) 가능한 영역 선택 뷰.
 * - overlay 위에서 번역할 영역을 지정하는 용도
 */
class RegionSelectorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // 내부 선택 영역 (뷰 내부 좌표)
    private val rect = RectF(dp(8f), dp(8f), dp(220f), dp(150f))

    private val handleRadius = dp(10f)
    private val minW = dp(80f)
    private val minH = dp(60f)

    private enum class Mode { NONE, MOVE, TL, TR, BL, BR }
    private var mode = Mode.NONE

    private var lastX = 0f
    private var lastY = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // default colors (system will pick), keep simple
        borderPaint.color = 0xFF66FF66.toInt()
        handlePaint.color = 0xFF66FF66.toInt()

        canvas.drawRoundRect(rect, dp(6f), dp(6f), borderPaint)

        // handles
        canvas.drawCircle(rect.left, rect.top, handleRadius, handlePaint)
        canvas.drawCircle(rect.right, rect.top, handleRadius, handlePaint)
        canvas.drawCircle(rect.left, rect.bottom, handleRadius, handlePaint)
        canvas.drawCircle(rect.right, rect.bottom, handleRadius, handlePaint)
    }

    fun getSelectedRectInView(): RectF = RectF(rect)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mode = hitTest(x, y)
                lastX = x
                lastY = y
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastX
                val dy = y - lastY

                when (mode) {
                    Mode.MOVE -> {
                        rect.offset(dx, dy)
                        clampToBounds()
                    }
                    Mode.TL -> resize(dx, dy, left = true, top = true)
                    Mode.TR -> resize(dx, dy, left = false, top = true)
                    Mode.BL -> resize(dx, dy, left = true, top = false)
                    Mode.BR -> resize(dx, dy, left = false, top = false)
                    else -> {}
                }

                lastX = x
                lastY = y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mode = Mode.NONE
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun resize(dx: Float, dy: Float, left: Boolean, top: Boolean) {
        val r = RectF(rect)

        if (left) r.left += dx else r.right += dx
        if (top) r.top += dy else r.bottom += dy

        // enforce min size
        if (r.width() < minW) {
            if (left) r.left = r.right - minW else r.right = r.left + minW
        }
        if (r.height() < minH) {
            if (top) r.top = r.bottom - minH else r.bottom = r.top + minH
        }

        rect.set(r)
        clampToBounds()
    }

    private fun clampToBounds() {
        // keep selection inside view
        val w = width.toFloat()
        val h = height.toFloat()

        // if view not measured yet, skip
        if (w <= 0f || h <= 0f) return

        val dxLeft = max(0f - rect.left, 0f)
        val dxRight = min(w - rect.right, 0f)
        val dyTop = max(0f - rect.top, 0f)
        val dyBottom = min(h - rect.bottom, 0f)

        rect.offset(dxLeft + dxRight, dyTop + dyBottom)

        // ensure still valid
        rect.left = rect.left.coerceIn(0f, w - minW)
        rect.top = rect.top.coerceIn(0f, h - minH)
        rect.right = rect.right.coerceIn(rect.left + minW, w)
        rect.bottom = rect.bottom.coerceIn(rect.top + minH, h)
    }

    private fun hitTest(x: Float, y: Float): Mode {
        fun near(ax: Float, ay: Float) = abs(x - ax) <= handleRadius * 1.6f && abs(y - ay) <= handleRadius * 1.6f
        return when {
            near(rect.left, rect.top) -> Mode.TL
            near(rect.right, rect.top) -> Mode.TR
            near(rect.left, rect.bottom) -> Mode.BL
            near(rect.right, rect.bottom) -> Mode.BR
            rect.contains(x, y) -> Mode.MOVE
            else -> Mode.NONE
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
