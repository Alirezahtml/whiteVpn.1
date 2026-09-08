package com.whitedns.vpn

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * A luxury horizontal stadium toggle switch connect button matching the design:
 * - Elongated dark matte capsule track with recessed bevel
 * - Smooth sliding elevated circular knob with 3D specular highlight and drop shadow
 * - Pure vector illuminated Power Symbol (⏻)
 * - Google Build / Gemini rotating LED beam when connecting
 * - Vibrant electric cyan/sapphire aura in active state
 * - Fluid drag-to-slide & tap interactions with spring physics and haptic feedback
 */
class ConnectionOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val palette = WhiteDnsDesignTokens.forContext(context)
    private val evaluator = ArgbEvaluator()

    // Dimensions
    private val desiredWidth = dp(246f)
    private val desiredHeight = dp(106f)

    // Bounds
    private val trackRect = RectF()
    private val trackPath = Path()
    private val knobRect = RectF()
    private val powerArcBounds = RectF()
    private val powerPath = Path()

    // Paints
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackInnerShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.4f)
    }
    private val trackActiveGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
    }
    private val ledBeamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val ledMatrix = Matrix()
    private val powerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = dp(3.2f)
    }
    private val powerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = dp(6.5f)
    }

    // State
    private var state: VpnState = VpnState.Stopped
    private var knobProgress = 0f // 0f = OFF (left), 1f = ON (right)
    private var targetProgress = 0f

    private var ledPhase = 0f
    private var isPaused = false

    private var masterAnimator: ValueAnimator? = null
    private var slideAnimator: ValueAnimator? = null

    // Touch handling
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var isDragging = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    init {
        isClickable = true
        isFocusable = true
    }

    fun setVpnState(newState: VpnState) {
        if (state == newState) return
        state = newState

        targetProgress = when (newState) {
            VpnState.Started -> 1f
            VpnState.Starting -> 0.65f
            VpnState.Stopping -> 0.35f
            else -> 0f
        }

        if (state == VpnState.Starting || state == VpnState.Stopping) {
            if (!isPaused && isAttachedToWindow && isShown) {
                startMasterAnimation()
            }
        } else {
            stopMasterAnimation()
        }

        slideAnimator?.cancel()
        if (ValueAnimator.areAnimatorsEnabled()) {
            slideAnimator = ValueAnimator.ofFloat(knobProgress, targetProgress).apply {
                duration = if (newState == VpnState.Starting || newState == VpnState.Stopping) 420L else 340L
                interpolator = PathInterpolator(0.18f, 1.25f, 0.35f, 1f)
                addUpdateListener {
                    knobProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            knobProgress = targetProgress
            invalidate()
        }
    }

    fun resumeAnimation() {
        isPaused = false
        startMasterAnimation()
    }

    fun pauseAnimation() {
        isPaused = true
        masterAnimator?.cancel()
        slideAnimator?.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (state == VpnState.Starting || state == VpnState.Stopping) {
            startMasterAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        stopMasterAnimation()
        slideAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible && !isPaused && (state == VpnState.Starting || state == VpnState.Stopping)) {
            startMasterAnimation()
        } else {
            stopMasterAnimation()
            slideAnimator?.cancel()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = resolveSize(desiredWidth.toInt(), widthMeasureSpec)
        val h = resolveSize(desiredHeight.toInt(), heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || !isClickable) return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - touchDownX
                val dy = event.y - touchDownY
                if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    isDragging = true
                }
                if (isDragging) {
                    val knobTravel = (trackRect.width() - (knobRect.width() + dp(14f))).coerceAtLeast(1f)
                    val offset = event.x - (trackRect.left + dp(7f) + knobRect.width() / 2f)
                    knobProgress = (offset / knobTravel).coerceIn(0f, 1f)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (isDragging) {
                    isDragging = false
                    val shouldTurnOn = knobProgress >= 0.5f
                    val wasOn = state == VpnState.Started
                    if (shouldTurnOn != wasOn) {
                        performHaptic()
                        performClick()
                    } else {
                        // Spring back to current state
                        animateProgressTo(targetProgress)
                    }
                } else {
                    // Quick tap anywhere on switch toggles it!
                    performHaptic()
                    performClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                isDragging = false
                animateProgressTo(targetProgress)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun animateProgressTo(target: Float) {
        slideAnimator?.cancel()
        slideAnimator = ValueAnimator.ofFloat(knobProgress, target).apply {
            duration = 260L
            interpolator = PathInterpolator(0.2f, 1.2f, 0.35f, 1f)
            addUpdateListener {
                knobProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        // 1. Calculate track bounds
        val trackMarginH = dp(10f)
        val trackMarginV = dp(6f)
        trackRect.set(trackMarginH, trackMarginV, w - trackMarginH, h - trackMarginV)
        val trackRadius = trackRect.height() / 2f

        // 2. Draw authentic engraved capsule track (recessed into surface)
        trackPath.reset()
        trackPath.addRoundRect(trackRect, trackRadius, trackRadius, Path.Direction.CW)

        canvas.save()
        canvas.clipPath(trackPath)

        // 2a. Recessed cavity background gradient (concave well)
        val topBg = if (palette.isDark) 0xFF0A0D14.toInt() else 0xFFD8DEE9.toInt()
        val botBg = if (palette.isDark) 0xFF141924.toInt() else 0xFFECEFF5.toInt()
        trackPaint.shader = LinearGradient(
            trackRect.centerX(), trackRect.top,
            trackRect.centerX(), trackRect.bottom,
            topBg, botBg,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(trackRect, trackPaint)

        // 2b. Chiseled inner top shadow (engraved carve)
        val shadowAlpha = if (palette.isDark) 0x70 else 0x36
        val shadowColor = (shadowAlpha shl 24) or 0x000000
        val shadowDepth = dp(14f)
        trackInnerShadowPaint.shader = LinearGradient(
            trackRect.centerX(), trackRect.top,
            trackRect.centerX(), trackRect.top + shadowDepth,
            shadowColor, Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(trackRect, trackInnerShadowPaint)

        // 2c. Crisp specular highlight on bottom rim
        val highlightAlpha = if (palette.isDark) 0x2A else 0xDD
        val highlightColor = (highlightAlpha shl 24) or 0xFFFFFF
        val highlightDepth = dp(7f)
        trackHighlightPaint.shader = LinearGradient(
            trackRect.centerX(), trackRect.bottom - highlightDepth,
            trackRect.centerX(), trackRect.bottom,
            Color.TRANSPARENT, highlightColor,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(trackRect, trackHighlightPaint)

        // 2d. Subtle illuminated track fill when active
        if (knobProgress > 0.05f) {
            val activeColor = when (state) {
                is VpnState.Error, VpnState.DailyLimitReached -> 0xFFFF453A.toInt()
                VpnState.Starting, VpnState.Stopping -> 0xFFFFD60A.toInt()
                else -> 0xFF0A84FF.toInt()
            }
            val alpha = (knobProgress * 55).toInt().coerceIn(0, 255)
            trackActiveGlowPaint.color = (activeColor and 0x00FFFFFF) or (alpha shl 24)
            canvas.drawRect(trackRect, trackActiveGlowPaint)
        }

        canvas.restore()

        // 2e. Dual-tone beveled border: darker top chiseled lip, highlighted bottom lip
        val topStroke = if (palette.isDark) 0x403A4D6B else 0x28000000
        val botStroke = if (palette.isDark) 0x22FFFFFF else 0xF0FFFFFF.toInt()
        trackStrokePaint.shader = LinearGradient(
            trackRect.centerX(), trackRect.top,
            trackRect.centerX(), trackRect.bottom,
            topStroke, botStroke,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(trackRect, trackRadius, trackRadius, trackStrokePaint)

        // 3. Calculate knob dimensions and position
        val knobPad = dp(7f)
        val knobDiameter = trackRect.height() - knobPad * 2f
        val knobRadius = knobDiameter / 2f
        val startX = trackRect.left + knobPad
        val endX = trackRect.right - knobPad - knobDiameter
        val currentKnobLeft = startX + knobProgress * (endX - startX)
        val currentKnobTop = trackRect.top + knobPad
        knobRect.set(currentKnobLeft, currentKnobTop, currentKnobLeft + knobDiameter, currentKnobTop + knobDiameter)

        val knobCx = knobRect.centerX()
        val knobCy = knobRect.centerY()

        // 4. Draw knob outer ambient glow (when active or connecting)
        if (knobProgress > 0.1f || state == VpnState.Starting || state == VpnState.Stopping) {
            val glowRadius = knobRadius + dp(18f)
            val glowColor = when (state) {
                is VpnState.Error, VpnState.DailyLimitReached -> 0xFFFF453A.toInt()
                VpnState.Starting, VpnState.Stopping -> 0xFFFFD60A.toInt()
                else -> 0xFF00D2FF.toInt()
            }
            val glowAlpha = (knobProgress * 110).toInt().coerceIn(0, 255)
            knobShadowPaint.shader = RadialGradient(
                knobCx, knobCy, glowRadius,
                intArrayOf((glowColor and 0x00FFFFFF) or (glowAlpha shl 24), Color.TRANSPARENT),
                floatArrayOf(0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(knobCx, knobCy, glowRadius, knobShadowPaint)
        }

        // 5. Google Build Rotating LED beam when starting / stopping
        if (state == VpnState.Starting || state == VpnState.Stopping) {
            val rotation = ledPhase * 360f
            ledMatrix.setRotate(rotation, knobCx, knobCy)
            val ledBeamColors = intArrayOf(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                0x40FFD60A.toInt(),
                0xB0FFD60A.toInt(),
                0xFFFFFFFF.toInt()
            )
            val positions = floatArrayOf(0f, 0.45f, 0.75f, 0.92f, 1f)
            ledBeamPaint.shader = SweepGradient(knobCx, knobCy, ledBeamColors, positions).apply {
                setLocalMatrix(ledMatrix)
            }
            ledBeamPaint.strokeWidth = dp(4f)
            canvas.drawCircle(knobCx, knobCy, knobRadius + dp(3f), ledBeamPaint)
        }

        // 6. Draw Knob Body
        if (knobProgress < 0.3f) {
            // OFF State: Elevated matte charcoal disk
            val topColor = if (palette.isDark) 0xFF353944.toInt() else 0xFFFFFFFF.toInt()
            val bottomColor = if (palette.isDark) 0xFF242730.toInt() else 0xFFE2E8F0.toInt()
            knobPaint.shader = LinearGradient(
                knobCx, knobRect.top,
                knobCx, knobRect.bottom,
                topColor, bottomColor,
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(knobCx, knobCy, knobRadius, knobPaint)
            knobRimPaint.color = if (palette.isDark) 0x44FFFFFF else 0x33000000
            canvas.drawCircle(knobCx, knobCy, knobRadius, knobRimPaint)
        } else {
            // ON / Connecting State: Radiant Electric Cyan -> Sapphire Blue gradient
            val startColor = when (state) {
                is VpnState.Error, VpnState.DailyLimitReached -> 0xFFFF453A.toInt()
                VpnState.Starting, VpnState.Stopping -> 0xFFFFD60A.toInt()
                else -> 0xFF00D2FF.toInt()
            }
            val endColor = when (state) {
                is VpnState.Error, VpnState.DailyLimitReached -> 0xFFD70015.toInt()
                VpnState.Starting, VpnState.Stopping -> 0xFFFF9F0A.toInt()
                else -> 0xFF0A84FF.toInt()
            }
            knobPaint.shader = LinearGradient(
                knobRect.left, knobRect.top,
                knobRect.right, knobRect.bottom,
                startColor, endColor,
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(knobCx, knobCy, knobRadius, knobPaint)
            knobRimPaint.color = 0x66FFFFFF
            canvas.drawCircle(knobCx, knobCy, knobRadius, knobRimPaint)
        }

        // 7. Draw Vector Power Symbol (⏻)
        val powerRadius = dp(16f)
        powerArcBounds.set(
            knobCx - powerRadius, knobCy - powerRadius,
            knobCx + powerRadius, knobCy + powerRadius
        )

        powerPath.reset()
        // Arc from -55 degrees to 235 degrees (leaves a 70 degree gap at top)
        powerPath.arcTo(powerArcBounds, -55f, 290f, false)

        // Soft glow for active power symbol
        if (knobProgress > 0.4f) {
            powerGlowPaint.color = 0x66FFFFFF
            canvas.drawPath(powerPath, powerGlowPaint)
            canvas.drawLine(knobCx, knobCy - powerRadius * 1.05f, knobCx, knobCy - dp(1f), powerGlowPaint)
        }

        // Clean crisp white power stroke
        val powerStrokeColor = if (knobProgress < 0.3f && !palette.isDark) 0xFF0F172A.toInt() else Color.WHITE
        powerPaint.color = powerStrokeColor
        canvas.drawPath(powerPath, powerPaint)
        // Vertical notch tick at top
        canvas.drawLine(knobCx, knobCy - powerRadius * 1.05f, knobCx, knobCy - dp(1f), powerPaint)
    }

    private fun startMasterAnimation() {
        if (state != VpnState.Starting && state != VpnState.Stopping) return
        if (masterAnimator?.isRunning == true) return
        masterAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                ledPhase = it.animatedFraction
                invalidate()
            }
            start()
        }
    }

    private fun stopMasterAnimation() {
        masterAnimator?.cancel()
        masterAnimator = null
        ledPhase = 0f
    }

    private fun performHaptic() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 16, 40, 24), intArrayOf(0, 140, 0, 220), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(25)
                }
            }
        } catch (_: Exception) {}
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
