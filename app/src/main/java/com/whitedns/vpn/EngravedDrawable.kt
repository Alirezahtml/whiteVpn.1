package com.whitedns.vpn

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue

/**
 * Authentic Engraved (Debossed / Chiseled Inset) Drawable:
 * - Recessed cavity fill with vertical lighting gradient
 * - Chiseled inner drop shadow along top rim
 * - Crisp specular light reflection along the bottom rim (upward-facing wall catching physical light)
 * - Dual-tone beveled border (darker top bevel, highlighted bottom bevel)
 */
class EngravedDrawable(
    private val cornerRadius: Float,
    private val isDark: Boolean,
    private val isChecked: Boolean = false,
    private val accentColor: Int? = null,
    private val depthPx: Float = 16f
) : Drawable() {

    private val boundsRect = RectF()
    private val clipPath = Path()
    private val borderPath = Path()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bottomHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return

        boundsRect.set(bounds)

        // Path for clipping inner shadow and fill
        clipPath.reset()
        clipPath.addRoundRect(boundsRect, cornerRadius, cornerRadius, Path.Direction.CW)

        // Path for border
        val inset = borderPaint.strokeWidth / 2f
        val innerBorderRect = RectF(
            boundsRect.left + inset,
            boundsRect.top + inset,
            boundsRect.right - inset,
            boundsRect.bottom - inset
        )
        borderPath.reset()
        borderPath.addRoundRect(innerBorderRect, (cornerRadius - inset).coerceAtLeast(0f), (cornerRadius - inset).coerceAtLeast(0f), Path.Direction.CW)

        // 1. Recessed cavity fill
        val topBgColor: Int
        val botBgColor: Int
        if (isChecked && accentColor != null) {
            topBgColor = if (isDark) 0xFF0E2A50.toInt() else 0xFFE1EFFF.toInt()
            botBgColor = if (isDark) 0xFF14386A.toInt() else 0xFFEBF5FF.toInt()
        } else if (isDark) {
            topBgColor = 0xFF0B0E15.toInt()
            botBgColor = 0xFF141A26.toInt()
        } else {
            topBgColor = 0xFFE4E9F2.toInt()
            botBgColor = 0xFFF2F5FA.toInt()
        }

        bgPaint.shader = LinearGradient(
            boundsRect.centerX(), boundsRect.top,
            boundsRect.centerX(), boundsRect.bottom,
            topBgColor, botBgColor,
            Shader.TileMode.CLAMP
        )

        // 2. Chiseled inner drop shadow along top
        val shadowAlpha = if (isDark) 0x60 else 0x30
        val shadowColor = (shadowAlpha shl 24) or 0x000000
        val shadowDepth = (depthPx * 1.5f).coerceAtMost(h * 0.5f)
        innerShadowPaint.shader = LinearGradient(
            boundsRect.centerX(), boundsRect.top,
            boundsRect.centerX(), boundsRect.top + shadowDepth,
            shadowColor, Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )

        // 3. Crisp specular reflection on bottom rim
        val highlightAlpha = if (isDark) 0x30 else 0xCC
        val highlightColor = (highlightAlpha shl 24) or 0xFFFFFF
        val highlightDepth = (depthPx * 0.8f).coerceAtMost(h * 0.35f)
        bottomHighlightPaint.shader = LinearGradient(
            boundsRect.centerX(), boundsRect.bottom - highlightDepth,
            boundsRect.centerX(), boundsRect.bottom,
            Color.TRANSPARENT, highlightColor,
            Shader.TileMode.CLAMP
        )

        // 4. Dual-tone beveled border: top is darker chiseled inset, bottom is reflective lip
        val topBorder = if (isChecked && accentColor != null) {
            accentColor
        } else if (isDark) {
            0x403A4D6B
        } else {
            0x26000000
        }

        val botBorder = if (isChecked && accentColor != null) {
            (accentColor and 0x00FFFFFF) or (0x80 shl 24)
        } else if (isDark) {
            0x28FFFFFF
        } else {
            0xF0FFFFFF.toInt()
        }

        borderPaint.shader = LinearGradient(
            boundsRect.centerX(), boundsRect.top,
            boundsRect.centerX(), boundsRect.bottom,
            topBorder, botBorder,
            Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        if (boundsRect.width() <= 0f || boundsRect.height() <= 0f) return

        canvas.save()
        canvas.clipPath(clipPath)

        // Draw recessed cavity
        canvas.drawRect(boundsRect, bgPaint)

        // Draw inner top shadow (engraved depth)
        canvas.drawRect(boundsRect, innerShadowPaint)

        // Draw inner bottom highlight (reflective lip)
        canvas.drawRect(boundsRect, bottomHighlightPaint)

        canvas.restore()

        // Draw beveled dual-tone stroke
        canvas.drawPath(borderPath, borderPaint)
    }

    override fun setAlpha(alpha: Int) {
        bgPaint.alpha = alpha
        borderPaint.alpha = alpha
        innerShadowPaint.alpha = alpha
        bottomHighlightPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        bgPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        fun createEngravedCard(context: Context, radiusDp: Float = 16f, isDark: Boolean): Drawable {
            val r = radiusDp * context.resources.displayMetrics.density
            val depth = 6f * context.resources.displayMetrics.density
            return EngravedDrawable(
                cornerRadius = r,
                isDark = isDark,
                isChecked = false,
                depthPx = depth
            )
        }

        fun createEngravedButton(
            context: Context,
            radiusDp: Float = 12f,
            isDark: Boolean,
            isChecked: Boolean = false,
            accentColor: Int? = null
        ): Drawable {
            val r = radiusDp * context.resources.displayMetrics.density
            val depth = 4f * context.resources.displayMetrics.density
            val normalDrawable = EngravedDrawable(
                cornerRadius = r,
                isDark = isDark,
                isChecked = isChecked,
                accentColor = accentColor,
                depthPx = depth
            )
            val rippleColor = ColorStateList.valueOf(
                if (accentColor != null) ((accentColor and 0x00FFFFFF) or (0x26 shl 24)) else 0x1A0066FF
            )
            return RippleDrawable(rippleColor, normalDrawable, null)
        }
    }
}
