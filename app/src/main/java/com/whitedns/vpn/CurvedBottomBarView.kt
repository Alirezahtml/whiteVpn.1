package com.whitedns.vpn

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.content.ContextCompat

/**
 * Exact Telegram iOS/Android Floating Pill Bottom Navigation Bar:
 * - Floating rounded stadium capsule floating above the home bar with subtle shadow and border
 * - Telegram soft rounded pill container highlight behind the active tab's icon and label
 * - Smooth gliding animation of the active pill between tabs
 * - Telegram Blue active accents and Apple/Telegram dark charcoal inactive typography
 * - Tactile spring bounce on active tab icon
 * - Full RTL / Persian locale support with mirrored layout
 */
class CurvedBottomBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val palette = WhiteDnsDesignTokens.forContext(context)

    data class TabData(
        val title: String,
        val icon: Drawable?
    )

    class TabItem(private val bar: CurvedBottomBarView, val position: Int) {
        fun select() {
            bar.selectTab(position, notify = true, animate = true)
        }
    }

    private val tabs = mutableListOf<TabData>()
    private var selectedIndex = 1 // Default to VPN (index 1)
    private var previousIndex = 1

    private var bottomInset = 0

    // Geometry rects
    private val barRect = RectF()
    private val shadowRect = RectF()
    private val activePillRect = RectF()

    // Smooth gliding indicator position
    private var currentIndicatorCenterX = -1f
    private var glideAnimator: ValueAnimator? = null

    // Icon bounce scale
    private var iconBounceScale = 1f
    private var bounceAnimator: ValueAnimator? = null

    // Paints
    private val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val barBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val activePillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private var onTabSelectedListener: ((Int) -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
        // Default tabs: Settings (0), VPN (1), Subscriptions (2)
        tabs.add(TabData(context.getString(R.string.tab_settings), ContextCompat.getDrawable(context, R.drawable.ic_advanced_tab)))
        tabs.add(TabData(context.getString(R.string.tab_vpn), ContextCompat.getDrawable(context, R.drawable.ic_vpn_tab)))
        tabs.add(TabData(context.getString(R.string.tab_subscriptions), ContextCompat.getDrawable(context, R.drawable.ic_subscriptions_tab)))
    }

    fun setBottomInset(inset: Int) {
        if (bottomInset != inset) {
            bottomInset = inset
            requestLayout()
            invalidate()
        }
    }

    fun getTabAt(index: Int): TabItem? {
        if (index in 0 until tabs.size) {
            return TabItem(this, index)
        }
        return null
    }

    val selectedTabPosition: Int get() = selectedIndex

    fun setOnTabSelectedListener(listener: (Int) -> Unit) {
        this.onTabSelectedListener = listener
    }

    private fun isRtl(): Boolean = layoutDirection == View.LAYOUT_DIRECTION_RTL

    private fun getVisualIndex(logicalIndex: Int): Int {
        return if (isRtl()) (tabs.size - 1 - logicalIndex) else logicalIndex
    }

    private fun getLogicalIndex(visualIndex: Int): Int {
        return if (isRtl()) (tabs.size - 1 - visualIndex) else visualIndex
    }

    private fun getTabCenterX(logicalIndex: Int): Float {
        if (tabs.isEmpty() || barRect.width() <= 0f) return 0f
        val tabW = barRect.width() / tabs.size.coerceAtLeast(1)
        val vIndex = getVisualIndex(logicalIndex)
        return barRect.left + (vIndex + 0.5f) * tabW
    }

    fun selectTab(position: Int, notify: Boolean = true, animate: Boolean = true) {
        if (position !in 0 until tabs.size) return
        if (position == selectedIndex && glideAnimator?.isRunning != true) return

        previousIndex = selectedIndex
        selectedIndex = position

        val targetCenterX = getTabCenterX(position)

        glideAnimator?.cancel()
        if (animate && ValueAnimator.areAnimatorsEnabled() && currentIndicatorCenterX > 0f) {
            val startX = currentIndicatorCenterX
            glideAnimator = ValueAnimator.ofFloat(startX, targetCenterX).apply {
                duration = 260L
                interpolator = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)
                addUpdateListener {
                    currentIndicatorCenterX = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            currentIndicatorCenterX = targetCenterX
            invalidate()
        }

        bounceAnimator?.cancel()
        if (animate && ValueAnimator.areAnimatorsEnabled()) {
            bounceAnimator = ValueAnimator.ofFloat(0.86f, 1.12f, 0.98f, 1f).apply {
                duration = 300L
                interpolator = PathInterpolator(0.34f, 1.56f, 0.64f, 1f)
                addUpdateListener {
                    iconBounceScale = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            iconBounceScale = 1f
            invalidate()
        }

        if (notify) {
            performTabHaptic()
            onTabSelectedListener?.invoke(position)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Floating pill total height: 60dp pill + 8dp top padding + 8dp bottom padding + bottomInset
        val desiredHeight = dp(68f).toInt() + bottomInset
        val resolvedWidth = MeasureSpec.getSize(widthMeasureSpec)
        val resolvedHeight = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(resolvedWidth, resolvedHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateBarBounds(w, h)
        if (currentIndicatorCenterX <= 0f && w > 0) {
            currentIndicatorCenterX = getTabCenterX(selectedIndex)
        }
    }

    private fun updateBarBounds(w: Int, h: Int) {
        val horizontalMargin = dp(16f)
        val topMargin = dp(6f)
        val pillHeight = dp(54f)
        val bottomMargin = dp(6f) + bottomInset

        barRect.set(
            horizontalMargin,
            topMargin,
            w.toFloat() - horizontalMargin,
            topMargin + pillHeight
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            if (tabs.isNotEmpty() && barRect.width() > 0f) {
                val touchX = event.x.coerceIn(barRect.left, barRect.right)
                val relativeX = touchX - barRect.left
                val tabW = barRect.width() / tabs.size
                val visualIndex = (relativeX / tabW).toInt().coerceIn(0, tabs.size - 1)
                val clickedIndex = getLogicalIndex(visualIndex)
                selectTab(clickedIndex, notify = true, animate = true)
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event) || true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        super.onRtlPropertiesChanged(layoutDirection)
        currentIndicatorCenterX = getTabCenterX(selectedIndex)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (tabs.isEmpty() || width == 0) return

        if (barRect.isEmpty) {
            updateBarBounds(width, height)
        }

        val pillCornerRadius = barRect.height() / 2f

        // 1. Soft layered ambient drop shadow under the floating capsule
        val shadowOffset = dp(3f)
        shadowRect.set(
            barRect.left + dp(4f),
            barRect.top + shadowOffset,
            barRect.right - dp(4f),
            barRect.bottom + shadowOffset + dp(3f)
        )
        shadowPaint.color = if (palette.isDark) 0x38000000 else 0x140C121D
        canvas.drawRoundRect(shadowRect, pillCornerRadius, pillCornerRadius, shadowPaint)

        // 2. Luxury Floating Pill Capsule Surface (Harmonized with app surface)
        val barBgColor = if (palette.isDark) 0xFF141D28.toInt() else 0xFFFFFFFF.toInt()
        barBgPaint.color = barBgColor
        canvas.drawRoundRect(barRect, pillCornerRadius, pillCornerRadius, barBgPaint)

        // Subtle outer border stroke (Subtle glass contour)
        barBorderPaint.strokeWidth = dp(1f)
        barBorderPaint.color = if (palette.isDark) 0x2AFFFFFF else 0x18000000
        canvas.drawRoundRect(barRect, pillCornerRadius, pillCornerRadius, barBorderPaint)

        // 3. Telegram Soft Pastel Active Tab Container Pill (Harmonized cyan/blue glow)
        if (currentIndicatorCenterX <= 0f) {
            currentIndicatorCenterX = getTabCenterX(selectedIndex)
        }

        val tabW = barRect.width() / tabs.size.coerceAtLeast(1)
        val activePillWidth = minOf(tabW - dp(10f), dp(84f))
        val activePillHeight = barRect.height() - dp(10f)
        val activePillRadius = activePillHeight / 2f
        val activePillTop = barRect.top + dp(5f)

        activePillRect.set(
            currentIndicatorCenterX - activePillWidth / 2f,
            activePillTop,
            currentIndicatorCenterX + activePillWidth / 2f,
            activePillTop + activePillHeight
        )

        val activePillBg = if (palette.isDark) 0xFF1D324E.toInt() else 0xFFE6F0FD.toInt()
        activePillPaint.color = activePillBg
        canvas.drawRoundRect(activePillRect, activePillRadius, activePillRadius, activePillPaint)

        // 4. Render Tabs: Icons and Text Labels
        val activeColor = if (palette.isDark) 0xFF2EA6FF.toInt() else 0xFF0066FF.toInt()
        val inactiveColor = if (palette.isDark) 0xFF8A99AC.toInt() else 0xFF4A5568.toInt()

        val iconSize = dp(22f)
        val iconCenterY = barRect.top + dp(17f)
        val labelY = barRect.top + dp(42.5f)
        val textSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            11f,
            resources.displayMetrics
        )

        for (i in tabs.indices) {
            val tab = tabs[i]
            val tabCenterX = getTabCenterX(i)
            val isSelected = (i == selectedIndex)

            val tintColor = if (isSelected) activeColor else inactiveColor

            // Draw Icon
            tab.icon?.let { icon ->
                val scale = if (isSelected) iconBounceScale else 1f

                canvas.save()
                canvas.translate(tabCenterX, iconCenterY)
                canvas.scale(scale, scale)

                val halfSize = (iconSize / 2f).toInt()
                icon.bounds = Rect(-halfSize, -halfSize, halfSize, halfSize)
                icon.colorFilter = PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
                icon.draw(canvas)
                canvas.restore()
            }

            // Draw Title Label (Telegram Style bold when selected, medium when unselected)
            textPaint.color = tintColor
            textPaint.textSize = textSizePx
            textPaint.typeface = if (isSelected) WhiteDnsBodyBoldTypeface else WhiteDnsBodyTypeface
            canvas.drawText(tab.title, tabCenterX, labelY, textPaint)
        }
    }

    private fun performTabHaptic() {
        try {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(10, 80))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(10)
                }
            }
        } catch (_: Exception) {}
    }

    override fun onDetachedFromWindow() {
        glideAnimator?.cancel()
        glideAnimator = null
        bounceAnimator?.cancel()
        bounceAnimator = null
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
