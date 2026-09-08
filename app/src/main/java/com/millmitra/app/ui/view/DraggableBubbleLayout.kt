package com.millmitra.app.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import kotlin.math.abs

/**
 * Custom ViewGroup that intercepts drag gestures across the entire floating capsule dock,
 * allowing users to drag from anywhere (even across buttons), while still passing clean
 * tap clicks to child action buttons when not dragging.
 */
class DraggableBubbleLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var windowManager: WindowManager? = null
    private var layoutParamsRef: WindowManager.LayoutParams? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var snapAnimator: ValueAnimator? = null

    private val touchSlop: Int = (ViewConfiguration.get(context).scaledTouchSlop).coerceAtLeast(8)
    private var onBubbleLongPress: (() -> Unit)? = null

    // Ghost Mode: Auto-dim to 38% opacity after 4s idle so it never blocks reading text
    private val dimRunnable = Runnable {
        animate()
            .alpha(0.38f)
            .setDuration(400)
            .start()
    }

    fun wakeUp() {
        removeCallbacks(dimRunnable)
        animate().alpha(1.0f).setDuration(150).start()
        scheduleDim(4000L)
    }

    fun scheduleDim(delayMs: Long = 4000L) {
        removeCallbacks(dimRunnable)
        postDelayed(dimRunnable, delayMs)
    }

    fun cancelDim() {
        removeCallbacks(dimRunnable)
        animate().alpha(1.0f).setDuration(150).start()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scheduleDim(4000L)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(dimRunnable)
    }

    private val longPressRunnable = Runnable {
        if (!isDragging) {
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            onBubbleLongPress?.invoke()
        }
    }

    fun setOnBubbleLongClickListener(listener: () -> Unit) {
        this.onBubbleLongPress = listener
    }

    fun setup(windowManager: WindowManager, params: WindowManager.LayoutParams) {
        this.windowManager = windowManager
        this.layoutParamsRef = params
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        wakeUp()
        val params = layoutParamsRef ?: return super.onInterceptTouchEvent(ev)
        val wm = windowManager ?: return super.onInterceptTouchEvent(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                snapAnimator?.cancel()
                initialX = params.x
                initialY = params.y
                initialTouchX = ev.rawX
                initialTouchY = ev.rawY
                isDragging = false
                removeCallbacks(longPressRunnable)
                postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                // Return false so child buttons can receive ACTION_DOWN
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.rawX - initialTouchX)
                val dy = abs(ev.rawY - initialTouchY)
                if (dx > touchSlop || dy > touchSlop) {
                    removeCallbacks(longPressRunnable)
                    isDragging = true
                    // Intercept gesture! Children receive ACTION_CANCEL; this view takes over touch stream
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                isDragging = false
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        wakeUp()
        val params = layoutParamsRef ?: return super.onTouchEvent(event)
        val wm = windowManager ?: return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                snapAnimator?.cancel()
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                removeCallbacks(longPressRunnable)
                postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()

                if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                    removeCallbacks(longPressRunnable)
                    isDragging = true
                }

                if (isDragging) {
                    val screenWidth = resources.displayMetrics.widthPixels
                    val screenHeight = resources.displayMetrics.heightPixels
                    val viewWidth = width.coerceAtLeast(120)
                    val viewHeight = height.coerceAtLeast(100)

                    params.x = (initialX + dx).coerceIn(0, screenWidth - viewWidth)
                    params.y = (initialY + dy).coerceIn(40, (screenHeight - viewHeight - 60).coerceAtLeast(100))

                    try {
                        wm.updateViewLayout(this, params)
                    } catch (e: Exception) {
                        // Ignore if detached
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                if (isDragging) {
                    snapToEdge()
                } else {
                    performClick()
                }
                isDragging = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                if (isDragging) {
                    snapToEdge()
                }
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Magnetically snaps the bubble to the nearest screen edge (left or right)
     * with a smooth deceleration curve.
     */
    private fun snapToEdge() {
        val params = layoutParamsRef ?: return
        val wm = windowManager ?: return
        val currentX = params.x
        val screenWidth = resources.displayMetrics.widthPixels
        val viewWidth = width.coerceAtLeast(120)

        val targetX = if (currentX + (viewWidth / 2) < screenWidth / 2) {
            16 // Snap to left margin
        } else {
            screenWidth - viewWidth - 16 // Snap to right margin
        }

        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(currentX, targetX).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                params.x = animation.animatedValue as Int
                try {
                    wm.updateViewLayout(this@DraggableBubbleLayout, params)
                } catch (e: Exception) {
                    // Ignore if detached during animation
                }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    scheduleDim(3500L)
                }
            })
        }
        snapAnimator?.start()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
