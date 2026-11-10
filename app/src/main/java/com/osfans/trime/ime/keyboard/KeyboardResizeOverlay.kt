/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import splitties.dimensions.dp
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * 「调整键盘高度」模式的遮罩层，盖在键盘区域上方。
 *
 * - 上边缘是拖动手柄，上下拖动实时改变键盘高度；
 * - 中间显示当前百分比；
 * - 底部「重置」「完成」。
 *
 * 遮罩消费所有触摸事件，调整期间下面的按键不会被点到。
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class KeyboardResizeOverlay(
    context: Context,
    private val keyboardWindow: KeyboardWindow,
    private val onFinish: (save: Boolean) -> Unit,
) : FrameLayout(context) {
    private val textColor = runCatching { ColorManager.getColor("key_text_color") }.getOrDefault(Color.WHITE)
    private val keyBackColor = runCatching { ColorManager.getColor("key_back_color") }.getOrDefault(Color.DKGRAY)
    private val backColor = runCatching { ColorManager.getColor("keyboard_back_color") }.getOrDefault(Color.BLACK)

    /** 当前（预览中的）比例 */
    var scale: Int = 100
        private set

    private var minScale = AppPrefs.Keyboard.KEYBOARD_HEIGHT_SCALE_MIN
    private var maxScale = AppPrefs.Keyboard.KEYBOARD_HEIGHT_SCALE_MAX

    // 拖动状态
    private var dragStartRawY = 0f
    private var dragStartHeight = 0
    private var baseHeight = 0

    private var reloadPending = false

    private val percentText =
        TextView(context).apply {
            setTextColor(textColor)
            textSize = 40f
            gravity = Gravity.CENTER
        }

    private val hintText =
        TextView(context).apply {
            setTextColor(ColorUtils.setAlphaComponent(textColor, 0xB0))
            textSize = 14f
            gravity = Gravity.CENTER
            setText(R.string.keyboard_resize_hint)
        }

    private val handle =
        TextView(context).apply {
            text = "⇕"
            setTextColor(textColor)
            textSize = 18f
            gravity = Gravity.CENTER
            background =
                GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(ColorUtils.setAlphaComponent(keyBackColor, 0xE6))
                }
        }

    /** 手柄所在的整条上边缘，比手柄本身大，方便按住 */
    private val handleBar =
        FrameLayout(context).apply {
            addView(handle, LayoutParams(dp(96), dp(28), Gravity.CENTER))
            setOnTouchListener { _, event -> onHandleTouch(event) }
        }

    private fun button(textRes: Int, onClick: () -> Unit) = TextView(context).apply {
        setText(textRes)
        setTextColor(textColor)
        textSize = 16f
        gravity = Gravity.CENTER
        background =
            GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(keyBackColor)
            }
        setOnClickListener { onClick() }
    }

    init {
        // 半透明遮罩：能看到下面键盘的实时大小，但按键不可点击
        setBackgroundColor(ColorUtils.setAlphaComponent(backColor, 0xC0))
        isClickable = true

        addView(handleBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(44), Gravity.TOP))

        val center =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(percentText)
                addView(hintText)
            }
        addView(center, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        val buttons =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                val lp = { LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(8); marginEnd = dp(8) } }
                addView(button(R.string.keyboard_resize_reset) { applyScale(100) }, lp())
                addView(button(R.string.keyboard_resize_done) { onFinish(true) }, lp())
            }
        addView(
            buttons,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
                setMargins(dp(8), 0, dp(8), dp(8))
            },
        )
    }

    /** 进入调整模式时调用，[initialScale] 是当前保存的比例 */
    fun start(initialScale: Int) {
        val keyboard = keyboardWindow.activeKeyboard
        baseHeight = keyboard?.baseKeyboardHeight ?: 0
        if (baseHeight > 0) {
            // 每行至少 32dp
            val rows = keyboard?.rowCount?.coerceAtLeast(1) ?: 1
            val minByRows = ceil(rows * dp(32) * 100.0 / baseHeight).toInt()
            minScale = maxOf(AppPrefs.Keyboard.KEYBOARD_HEIGHT_SCALE_MIN, minByRows)
            // 不超过屏幕高度的一半
            val maxByScreen = (resources.displayMetrics.heightPixels / 2) * 100 / baseHeight
            maxScale = minOf(AppPrefs.Keyboard.KEYBOARD_HEIGHT_SCALE_MAX, maxByScreen).coerceAtLeast(minScale)
        }
        scale = initialScale.coerceIn(minScale, maxScale)
        updateText()
    }

    private fun onHandleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 用 rawY：拖动时手柄会跟着键盘移动，相对坐标会来回跳
                dragStartRawY = event.rawY
                dragStartHeight = baseHeight * scale / 100
                handle.isPressed = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (baseHeight <= 0) return true
                val newHeight = dragStartHeight + (dragStartRawY - event.rawY)
                val newScale = (newHeight * 100f / baseHeight).roundToInt()
                applyScale(newScale)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handle.isPressed = false
            }
        }
        return true
    }

    private fun applyScale(value: Int) {
        val clamped = value.coerceIn(minScale, maxScale)
        if (clamped == scale) return
        scale = clamped
        updateText()
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        KeyboardPrefs.previewHeightScale = clamped
        // 同一帧内的多次变化只重建一次键盘
        if (!reloadPending) {
            reloadPending = true
            post {
                reloadPending = false
                if (isAttachedToWindow && visibility == VISIBLE) keyboardWindow.reloadKeyboards()
            }
        }
    }

    private fun updateText() {
        percentText.text = "$scale%"
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = true

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        // 旋转屏幕：放弃本次调整
        if (visibility == VISIBLE) onFinish(false)
    }
}
