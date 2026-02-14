package com.example.regiontranslator

import android.graphics.*
import kotlin.math.max
import kotlin.math.min

object BitmapUtils {

    fun safeCrop(src: Bitmap, rect: Rect): Bitmap {
        val left = max(0, min(rect.left, src.width - 1))
        val top = max(0, min(rect.top, src.height - 1))
        val right = max(left + 1, min(rect.right, src.width))
        val bottom = max(top + 1, min(rect.bottom, src.height))
        return Bitmap.createBitmap(src, left, top, right - left, bottom - top)
    }

    /**
     * OCR 전처리:
     * - 확대 (작은 글자 인식 개선)
     * - 그레이스케일 + 대비 약간 증가
     * - 가벼운 샤픈
     */
    fun preprocessForOcr(src: Bitmap): Bitmap {
        val scale = 1.6f
        val scaled = Bitmap.createScaledBitmap(
            src,
            (src.width * scale).toInt().coerceAtLeast(1),
            (src.height * scale).toInt().coerceAtLeast(1),
            true
        )

        val out = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val cm = ColorMatrix().apply {
            setSaturation(0f)
            // 대비 약간 업: 1.15
            val contrast = 1.15f
            val t = (-0.5f * contrast + 0.5f) * 255f
            val m = floatArrayOf(
                contrast, 0f, 0f, 0f, t,
                0f, contrast, 0f, 0f, t,
                0f, 0f, contrast, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )
            postConcat(ColorMatrix(m))
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
            isFilterBitmap = true
        }
        canvas.drawBitmap(scaled, 0f, 0f, paint)

        // light sharpen convolution
        val sharp = Bitmap.createBitmap(out.width, out.height, Bitmap.Config.ARGB_8888)
        val rs = RenderScriptCompat.sharpen(out, sharp)
        return rs ?: out
    }
}

/**
 * 렌더스크립트 없이도 동작하도록 하는 아주 가벼운 샤픈(소프트웨어) 처리.
 * (RenderScript는 deprecated라, 여기서는 CPU로 간단히 처리)
 */
object RenderScriptCompat {
    fun sharpen(src: Bitmap, dst: Bitmap): Bitmap? {
        return try {
            val w = src.width
            val h = src.height
            val pixels = IntArray(w * h)
            src.getPixels(pixels, 0, w, 0, 0, w, h)

            // kernel:
            //  0 -1  0
            // -1  5 -1
            //  0 -1  0
            fun clamp(v: Int) = v.coerceIn(0, 255)

            val out = IntArray(w * h)
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val c = pixels[y * w + x]
                    val up = pixels[(y - 1) * w + x]
                    val dn = pixels[(y + 1) * w + x]
                    val lf = pixels[y * w + (x - 1)]
                    val rt = pixels[y * w + (x + 1)]

                    val a = Color.alpha(c)
                    val r = clamp(5 * Color.red(c) - Color.red(up) - Color.red(dn) - Color.red(lf) - Color.red(rt))
                    val g = clamp(5 * Color.green(c) - Color.green(up) - Color.green(dn) - Color.green(lf) - Color.green(rt))
                    val b = clamp(5 * Color.blue(c) - Color.blue(up) - Color.blue(dn) - Color.blue(lf) - Color.blue(rt))

                    out[y * w + x] = Color.argb(a, r, g, b)
                }
            }
            // edges: copy
            for (x in 0 until w) { out[x] = pixels[x]; out[(h-1)*w+x]=pixels[(h-1)*w+x] }
            for (y in 0 until h) { out[y*w]=pixels[y*w]; out[y*w+w-1]=pixels[y*w+w-1] }

            dst.setPixels(out, 0, w, 0, 0, w, h)
            dst
        } catch (_: Throwable) {
            null
        }
    }
}
