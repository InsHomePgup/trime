/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.content.Context
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.util.isLandscape

object KeyboardPrefs {
    private val prefs = AppPrefs.defaultInstance()

    private const val WIDE_SCREEN_WIDTH_DP = 600

    /**
     * 调整模式中的临时预览比例（百分比）。非空时优先于偏好设置，
     * 让拖动时只重建键盘、不写偏好。
     */
    var previewHeightScale: Int? = null

    /** 当前屏幕方向下的键盘高度偏好 */
    fun Context.keyboardHeightScalePref() = if (resources.configuration.isLandscape()) {
        prefs.keyboard.keyboardHeightScaleLand
    } else {
        prefs.keyboard.keyboardHeightScale
    }

    /** 当前生效的键盘高度比例（百分比） */
    fun Context.keyboardHeightScale(): Int = previewHeightScale ?: keyboardHeightScalePref().getValue()

    fun Context.isLandscapeMode(): Boolean = when (prefs.keyboard.landscapeMode.getValue()) {
        AppPrefs.Keyboard.LandscapeMode.WIDE -> resources.configuration.isLandscape() || isWideScreen()
        AppPrefs.Keyboard.LandscapeMode.LANDSCAPE -> resources.configuration.isLandscape()
        AppPrefs.Keyboard.LandscapeMode.ALWAYS -> true
        else -> false
    }

    private fun Context.isWideScreen(): Boolean {
        val metrics = resources.displayMetrics
        return metrics.widthPixels / metrics.density > WIDE_SCREEN_WIDTH_DP
    }
}
