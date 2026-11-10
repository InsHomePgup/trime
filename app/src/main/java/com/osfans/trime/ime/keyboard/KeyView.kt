/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.KeyEvent
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.sizeDp
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.popup.PopupAction
import com.osfans.trime.ime.popup.PopupDelegate
import com.osfans.trime.util.sp
import splitties.dimensions.dp
import timber.log.Timber

@SuppressLint("ClickableViewAccessibility", "ViewConstructor")
class KeyView(
    context: Context,
    private val key: Key,
    private val keyboard: Keyboard,
    private val keyboardView: KeyboardView,
    private val keyboardActionListener: KeyboardActionListener,
) : GestureFrame(context) {

    private val service: TrimeInputMethodService
        get() = keyboardView.service

    private val popup: PopupDelegate
        get() = keyboardView.popup

    private val rime get() = RimeDaemon.getFirstSessionOrNull()!!

    private val hookShiftArrow: Boolean by lazy {
        AppPrefs.defaultInstance().keyboard.hookShiftArrow.getValue()
    }

    private val deletedTextBuffer = ArrayDeque<String>()

    private var lastModifierTapTime = 0L

    /**
     * 长按退格一段时间后整词删除：英文、数字删到上一个单词开头（连同后面的空格），
     * 其他字符（汉字、标点）仍然一次删一个。有编码、有选中文字时不处理，返回 false 交给原逻辑。
     */
    private fun deleteWordBeforeCursor(): Boolean {
        if (rime.run { statusCached }.isComposing) return false
        val ic = service.currentInputConnection ?: return false
        if (!ic.getSelectedText(0).isNullOrEmpty()) return false
        val before = ic.getTextBeforeCursor(64, 0)?.toString() ?: return false
        if (before.isEmpty()) return false
        var end = before.length
        while (end > 0 && before[end - 1] == ' ') end--
        var start = end
        if (start > 0 && before[start - 1].let { it < '\u0080' && it.isLetterOrDigit() }) {
            while (start > 0 && before[start - 1].let { it < '\u0080' && it.isLetterOrDigit() }) start--
        } else if (start > 0) {
            start -= if (start >= 2 && before[start - 1].isLowSurrogate() && before[start - 2].isHighSurrogate()) 2 else 1
        }
        val count = before.length - start
        if (count <= 0) return false
        ic.deleteSurroundingText(count, 0)
        return true
    }

    private var keyPressed = false
    override fun isPressed(): Boolean = keyPressed

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private var cachedIcon: IconicsDrawable? = null
    private var cachedIconName: String? = null

    private val cachedLocation = intArrayOf(0, 0)
    private val cachedBounds = Rect()
    private var boundsValid = false

    val bounds: Rect
        get() = cachedBounds.also {
            if (!boundsValid) updateBounds()
        }

    fun updateBounds() {
        val (x, y) = cachedLocation.also { getLocationInWindow(it) }
        cachedBounds.set(x + key.extraWidthLeft, y, x + width - key.extraWidthRight, y + height)
        boundsValid = true
    }

    init {
        setWillNotDraw(false)
        isRepeatable = key.click?.isRepeatable ?: false
        isSlideCursor = key.click?.isSlideCursor ?: false
        isSlideDelete = key.click?.isSlideDelete ?: false
        hasLongPress = key.hasAction(KeyBehavior.LONG_CLICK)
        hasDouble = key.hasAction(KeyBehavior.DOUBLE_CLICK)
        hasLazyDouble = key.hasAction(KeyBehavior.LAZY_DOUBLE_CLICK)
        hasPopup = key.popup.isNotEmpty()

        onPress = {
            if (keyboard.firstPressedKeyIndex == -1) keyboard.firstPressedKeyIndex = id
            setPressedState(true)
            key.getCode(KeyBehavior.CLICK).let { keyboardActionListener.onPress(it) }
            showPopupPreview()
        }

        onRelease = { behavior, isFromLongPress ->
            Timber.d("KeyView release: label=${key.getLabel()}, behavior=$behavior, fromLongPress=$isFromLongPress")
            if (isFromLongPress) {
                if (hasPopup) {
                    val triggerAction = PopupAction.TriggerAction(id)
                    popup.listener.onPopupAction(triggerAction)
                    triggerAction.outAction?.let { action ->
                        keyboardActionListener.onAction(KeyAction(action))
                        dismissPopupPreview()
                    }
                    setPressedState(false)
                } else if (isRepeatable) {
                    key.getAction(KeyBehavior.CLICK)?.let {
                        if (it.code == KeyEvent.KEYCODE_DEL && repeatCount >= WORD_DELETE_AFTER_REPEATS && deleteWordBeforeCursor()) {
                            return@let
                        }
                        processKeyAction(it, KeyBehavior.CLICK)
                    }
                }
            } else {
                when (behavior) {
                    KeyBehavior.CLICK -> {
                        val pressedIdx = keyboard.firstPressedKeyIndex
                        val actionBehavior = if (pressedIdx != -1 && pressedIdx != id) KeyBehavior.COMBO else behavior
                        key.getAction(actionBehavior)?.let { processKeyAction(it, actionBehavior) }
                    }
                    KeyBehavior.DOUBLE_CLICK, KeyBehavior.LAZY_DOUBLE_CLICK,
                    KeyBehavior.SWIPE_UP, KeyBehavior.SWIPE_DOWN, KeyBehavior.SWIPE_LEFT, KeyBehavior.SWIPE_RIGHT,
                    ->
                        key.getAction(behavior)?.let { processKeyAction(it, behavior) }
                    else -> {}
                }

                setPressedState(false)
                dismissPopupPreview()
            }
            if (keyboard.firstPressedKeyIndex == id) keyboard.firstPressedKeyIndex = -1
        }

        onSwipe = { direction ->
            setPressedState(true)
            showPopupPreview(direction)
        }

        onSlide = { delta, _, _ ->
            if (isSlideCursor) {
                when {
                    delta > 0 -> keyboardActionListener?.onAction(KeyAction("Right"))
                    delta < 0 -> keyboardActionListener?.onAction(KeyAction("Left"))
                }
            } else if (isSlideDelete) {
                val ic = service.currentInputConnection
                when {
                    delta < 0 -> {
                        val beforeText = ic.getTextBeforeCursor(1, 0) ?: ""
                        if (beforeText.isNotEmpty()) {
                            deletedTextBuffer.addFirst(beforeText.toString())
                            ic.deleteSurroundingText(1, 0)
                        }
                    }

                    delta > 0 -> {
                        if (deletedTextBuffer.isNotEmpty()) {
                            ic.commitText(deletedTextBuffer.removeFirst(), 1)
                        }
                    }
                }
            }
        }

        onLongClick = {
            if (key.popup.isNotEmpty()) {
                dismissPopupPreview()
                showPopupKeyboard()
            } else if (hasLongPress) {
                key.getAction(KeyBehavior.LONG_CLICK)?.let {
                    processKeyAction(it, KeyBehavior.LONG_CLICK)
                    setPressedState(false)
                    dismissPopupPreview()
                }
            }
        }

        onMove = { x, y, isLongPress ->
            if (isLongPress && hasPopup) {
                popup.listener.onPopupAction(PopupAction.ChangeFocusAction(id, x, y))
            }
        }

        onCancel = {
            deletedTextBuffer.clear()
            setPressedState(false)
            dismissPopupPreview()
        }
    }

    fun setPressedState(pressed: Boolean) {
        if (keyPressed != pressed) {
            keyPressed = pressed
            if (pressed) {
                key.onPressed()
            } else {
                key.onReleased()
            }
            invalidate()
        }
    }

    private fun processKeyAction(action: KeyAction, behavior: KeyBehavior) {
        Timber.d("processKeyAction: label=${key.getLabel()}, code=${action.code}, type=$behavior")

        if (action.isModifierKey) {
            val lock = if (action.shiftLock == "double") {
                // iOS 式：单击是一次性 Shift，快速双击锁定大写，锁定时单击解除
                val now = SystemClock.elapsedRealtime()
                val isDoubleTap = now - lastModifierTapTime <= doubleTapTimeout
                lastModifierTapTime = if (isDoubleTap) 0L else now
                key.isOn || isDoubleTap || behavior == KeyBehavior.LONG_CLICK
            } else {
                action.isShiftLock xor (behavior == KeyBehavior.LONG_CLICK)
            }
            keyboard.clickModifierKey(lock, action.modifierKeyOnMask)
            keyboardView.invalidateAllKeys()
            return
        }

        keyboardActionListener.onAction(action)

        val hookArrow = if (hookShiftArrow) {
            when (action.code) {
                in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT -> true
                KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END -> true
                else -> false
            }
        } else {
            false
        }

        if (!hookArrow) {
            if (keyboard.refreshModifier()) {
                keyboardView.invalidateAllKeys()
            }
        }
    }

    private fun showPopupKeyboard() {
        val popupKeys = key.popup
        if (popupKeys.isEmpty()) return

        popup.listener.onPopupAction(
            PopupAction.ShowKeyboardAction(id, popupKeys, bounds),
        )
    }

    private fun showPopupPreview(behavior: KeyBehavior = KeyBehavior.CLICK) {
        // 按键气泡暂时整体关闭，不读「按键弹出预览」设置：现在的样子离 iOS 太远，
        // 而设置页会把旧版的默认值「开」存下来，改默认值关不掉。见 docs/ios-parity-backlog.md
        if (!KEY_PREVIEW_ENABLED) return
        // 和 iOS 一样，功能键（空格、Shift、退格、换行、切换键盘等）不弹气泡
        val click = key.click
        if (behavior == KeyBehavior.CLICK && (click?.isFunctional == true || click?.code == KeyEvent.KEYCODE_SPACE)) return
        key.getPreviewText(behavior).takeIf { it.isNotEmpty() }?.let { previewText ->
            val context = if (previewText.isIconFont) {
                previewText
            } else {
                String(Character.toChars(previewText.codePointAt(0)))
            }
            popup.listener.onPopupAction(PopupAction.PreviewAction(id, context, bounds))
        }
    }

    private fun dismissPopupPreview() {
        popup.listener.onPopupAction(
            PopupAction.DismissAction(id),
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val totalWidth = key.width + key.extraWidthLeft + key.extraWidthRight
        val desiredWidth = totalWidth + paddingLeft + paddingRight
        val desiredHeight = key.height + paddingTop + paddingBottom

        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        boundsValid = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawBackground(canvas, key)

        val label = key.getLabel().let {
            if (it == "enter_labels") keyboardView.labelEnter else it
        }

        if (label.isNotEmpty()) {
            drawLabel(canvas, label)
        }

        val symbol = key.symbolLabel
        if (symbol.isNotEmpty()) {
            drawSymbol(canvas, symbol)
        }

        val hint = key.hint
        if (hint.isNotEmpty()) {
            drawSymbol(canvas, hint, isTop = false)
        }
    }

    private fun drawBackground(canvas: Canvas, k: Key) {
        val bg = k.getBackgroundDrawable() ?: return

        if (bg is GradientDrawable) {
            (k.roundCorner ?: keyboard.roundCorner).takeIf { it > 0f }?.let { bg.cornerRadius = dp(it) }
            (k.keyBorder ?: keyboard.keyBorder).takeIf { it > 0 }?.let { bg.setStroke(dp(it), ColorManager.getColor("key_border_color")) }
        }

        bg.setBounds(
            paddingLeft,
            paddingTop,
            width - paddingRight,
            height - paddingBottom,
        )
        bg.draw(canvas)
    }

    private fun drawLabel(canvas: Canvas, label: String) {
        val textColor = key.getTextColor()
        // 图标按普通字号画（标签名本身很长，不能按「多字标签」缩小）
        val textSize = sp(
            key.keyTextSize.takeIf { it > 0 }
                ?: if (label.length > 1 && !label.isIconFont) keyboardView.keyLongTextSize else keyboardView.keyTextSize,
        )

        if (label.isIconFont) {
            drawIcon(canvas, shiftStateIcon(label), textSize.toInt(), textColor, key.keyTextOffsetX, key.keyTextOffsetY)
        } else {
            textPaint.apply {
                color = textColor
                this.textSize = textSize
                typeface = FontManager.getTypeface("key_font")
                clearShadowLayer()
            }

            val centerX = (width - paddingLeft - paddingRight) / 2f + paddingLeft
            val centerY = (height - paddingTop - paddingBottom) / 2f + paddingTop
            val fontMetrics = textPaint.fontMetrics
            val adjustmentY = -(fontMetrics.ascent + fontMetrics.descent) / 2f

            canvas.drawText(label, centerX + glyphCenterOffset(label) + sp(key.keyTextOffsetX), centerY + adjustmentY + sp(key.keyTextOffsetY), textPaint)
        }
    }

    private val glyphBounds = android.graphics.Rect()

    /**
     * 全角标点（（）“”等）的字形在字宽里偏向一侧，按字宽居中会显得歪。
     * 单个非 ASCII 字符按实际字形的左右边界居中。
     */
    private fun glyphCenterOffset(label: String): Float {
        if (label.isEmpty() || label.codePointCount(0, label.length) != 1 || label[0] < '\u0080') return 0f
        textPaint.getTextBounds(label, 0, label.length, glyphBounds)
        if (glyphBounds.isEmpty) return 0f
        val advance = textPaint.measureText(label)
        // textAlign 是 CENTER：字宽中心在 x 处，字形中心在 x - advance/2 + (left+right)/2
        return advance / 2f - (glyphBounds.left + glyphBounds.right) / 2f
    }

    /** Shift 键按状态换图标：未按 → 空心箭头，一次性大写 → 实心箭头，锁定 → 大写锁定 */
    private fun shiftStateIcon(label: String): String {
        if (label != SHIFT_ICON_OFF) return label
        return when {
            key.isOn -> SHIFT_ICON_LOCKED
            keyboard.isShifted -> SHIFT_ICON_ON
            else -> label
        }
    }

    private fun drawIcon(
        canvas: Canvas,
        iconName: String,
        size: Int,
        color: Int,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        isTop: Boolean? = null,
    ) {
        val halfSize = size / 2

        val cmdName = iconName.toIconName()
        val icon = if (cachedIconName == cmdName) {
            cachedIcon!!
        } else {
            IconicsDrawable(context, cmdName).apply {
                sizeDp = size
            }.also {
                cachedIcon = it
                cachedIconName = cmdName
            }
        }

        icon.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)

        val centerX = (width - paddingLeft - paddingRight) / 2f + paddingLeft + sp(offsetX)

        val centerY = when (isTop) {
            true -> paddingTop + halfSize + sp(offsetY)
            false -> height - paddingBottom - size + sp(offsetY)
            null -> (height - paddingTop - paddingBottom) / 2f + paddingTop + sp(offsetY)
        }

        icon.setBounds(
            (centerX - halfSize).toInt(),
            (centerY - halfSize).toInt(),
            (centerX + halfSize).toInt(),
            (centerY + halfSize).toInt(),
        )
        icon.draw(canvas)
    }

    private fun drawSymbol(canvas: Canvas, text: String, isTop: Boolean = true) {
        // 绘制时只读缓存，不能等 Rime 线程（它可能正在处理按键）
        val showSymbol = rime.run { !getRuntimeOptionCached("_hide_key_symbol") }
        val showHint = rime.run { !getRuntimeOptionCached("_hide_key_hint") }

        if (isTop && !showSymbol) return
        if (!isTop && !showHint) return

        val textColor = key.getSymbolColor()
        val textSize = sp(key.symbolTextSize.takeIf { it > 0f } ?: keyboardView.symbolTextSize)
        val offsetX = if (isTop) key.keySymbolOffsetX else key.keyHintOffsetX
        val offsetY = if (isTop) key.keySymbolOffsetY else key.keyHintOffsetY

        if (text.isIconFont) {
            drawIcon(canvas, text, textSize.toInt(), textColor, offsetX, offsetY, isTop)
        } else {
            symbolPaint.apply {
                color = textColor
                this.textSize = textSize
                typeface = FontManager.getTypeface("symbol_font")
            }

            val lines = text.split("\n")
            val fontMetrics = symbolPaint.fontMetrics
            val lineHeight = fontMetrics.descent - fontMetrics.ascent
            val totalHeight = lineHeight * lines.size

            val centerX = (width - paddingLeft - paddingRight) / 2f + paddingLeft + sp(offsetX)
            val startY = if (isTop) {
                paddingTop - fontMetrics.top + sp(offsetY) - (totalHeight - lineHeight) / 2
            } else {
                height - paddingBottom - fontMetrics.bottom + sp(offsetY) - (totalHeight - lineHeight) / 2
            }

            for (i in lines.indices) {
                val lineY = startY + lineHeight * i
                canvas.drawText(lines[i], centerX, lineY, symbolPaint)
            }
        }
    }

    companion object {
        /** 长按退格连删多少次后改为整词删除（约 1.5 秒） */
        private const val WORD_DELETE_AFTER_REPEATS = 20

        /** 按键气泡总开关，等做出 iOS 的气泡样式后再打开 */
        private const val KEY_PREVIEW_ENABLED = false

        private const val SHIFT_ICON_OFF = "ic@arrow_up_bold_outline"
        private const val SHIFT_ICON_ON = "ic@arrow_up_bold"
        private const val SHIFT_ICON_LOCKED = "ic@apple_keyboard_caps"

        private val doubleTapTimeout by AppPrefs.defaultInstance().keyboard.doubleTapTimeout
    }
}
