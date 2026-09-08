package com.millmitra.app.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs

/**
 * Handles smooth dragging and magnetic edge-snapping for the MillMitra floating bubble.
 */
class OverlayTouchListener(
    private val context: Context,
    private val windowManager: WindowManager,
    private val params: WindowManager.LayoutParams,
    private val rootView: View,
    private val onBubbleClick: (() -> Unit)? = null
) : View.OnTouchListener {

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private val clickThreshold = 14 // pixels threshold to differentiate tap vs drag

    private val screenWidth: Int
        get() {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)
            return metrics.widthPixels
        }

    private val screenHeight: Int
        get() {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)
            return metrics.heightPixels
        }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()

                if (abs(dx) > clickThreshold || abs(dy) > clickThreshold) {
                    isDragging = true
                }

                if (isDragging) {
                    params.x = initialX + dx
                    val newY = initialY + dy
                    params.y = newY.coerceIn(40, (screenHeight - 160).coerceAtLeast(100))
                    try {
                        windowManager.updateViewLayout(rootView, params)
                    } catch (e: Exception) {
                        // Ignore if detached during drag
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    // Trigger tap callback
                    onBubbleClick?.invoke()
                    v.performClick()
                } else {
                    // Magnetic snapping to the nearest edge (left or right)
                    snapToEdge()
                }
                return true
            }
        }
        return false
    }

    private fun snapToEdge() {
        val currentX = params.x
        val viewWidth = rootView.width.coerceAtLeast(120)
        val targetX = if (currentX + (viewWidth / 2) < screenWidth / 2) {
            16 // Snap to left margin
        } else {
            screenWidth - viewWidth - 16 // Snap to right margin
        }

        val animator = ValueAnimator.ofInt(currentX, targetX).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                params.x = animation.animatedValue as Int
                try {
                    windowManager.updateViewLayout(rootView, params)
                } catch (e: Exception) {
                    // View may be detached
                }
            }
        }
        animator.start()
    }
}
