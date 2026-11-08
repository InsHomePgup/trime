# 方案：拖动调节键盘高度

状态：**待实现**（2026-11-08 记录）

## 目标

像 Gboard 一样，在键盘上直接拖动来调整键盘高度，所见即所得，不用改主题文件、不用重新部署。

## 现状

- 键盘高度完全由主题决定：`ios.trime.yaml` 里的 `style/keyboard_height`（竖屏，现为 236dp）
  和 `style/keyboard_height_land`（横屏，现为 180dp）。单个键盘布局也可以用自己的
  `keyboard_height` 覆盖。
- App 设置里**没有**任何调节高度的选项。
- 目前唯一的调整方法：在手机 `/sdcard/rime/` 放一个 `ios.trime.custom.yaml`：

  ```yaml
  patch:
    style/keyboard_height: 260
  ```

  然后在 App 里点「部署」。

## 交互设计

1. **入口**：工具栏（候选栏左边的 `•••` 展开后）加一个「调整键盘大小」按钮；
   也可以在「中」键的长按或上滑里加一个入口（二选一，实现时再定）。
2. **进入调整模式**：
   - 键盘按键全部变成半透明、不可点击；
   - 键盘**上边缘**出现一条横向拖动手柄（带上下箭头）；
   - 键盘中间显示当前高度百分比（如「100%」）；
   - 底部显示两个按钮：「重置」「完成」。
3. **拖动**：手指按住手柄上下拖，键盘实时变高或变矮，四行按键按比例缩放。
   - 范围限制：主题高度的 **70% ～ 150%**，并且不超过屏幕高度的一半；
   - 每 1% 一档，拖动时有轻微振动反馈（可选）。
4. **完成**：点「完成」或点键盘外区域，保存并退出调整模式。
5. **重置**：点「重置」恢复到主题默认高度（100%）。
6. 竖屏和横屏**分别记忆**，在哪个方向下调整，就只改哪个方向。

## 技术方案

### 1. 存储：保存一个缩放比例，而不是绝对高度

在 `AppPrefs.Keyboard`（`data/prefs/AppPrefs.kt` 第 118 行起）加两个偏好：

```kotlin
val keyboardHeightScale = int(KEYBOARD_HEIGHT_SCALE, 100)          // 竖屏，百分比
val keyboardHeightScaleLand = int(KEYBOARD_HEIGHT_SCALE_LAND, 100) // 横屏
```

存比例而不是 dp 值，换主题时依然有效，主题作者调整默认高度后也能跟着变。

### 2. 应用比例：在计算键盘高度的地方乘上去

`ime/keyboard/Keyboard.kt`：

- 第 147 行 `keyboardHeight` 取 `getKeyboardHeightFromKeyboardConfig` / `getKeyboardHeightFromTheme`
  的结果（已转成 px）。在这里统一乘以当前方向的缩放比例。
- 行高本来就是「按比例缩放到 keyboardHeight」（第 228–234 行），所以只要改总高度，
  四行会自动等比例变化，不用动行高逻辑。

### 3. 让改动立即生效

- `KeyboardWindow` 会缓存 `Keyboard` 和 `KeyboardView`（`cachedKeyboards`，第 112–118 行），
  高度在构造 `Keyboard` 时就算好了。比例变化后要**清空缓存并重新 attach 当前键盘**。
- `KeyboardWindow.attachKeyboard` 会通过 `_currentKeyboardHeight` 发出新高度，
  `InputView` 第 201 行在监听这个 Flow，会跟着调整布局。
- 简单做法：把两个新偏好加进 `TrimeInputMethodService.recreateInputViewPrefs`（第 99 行），
  变化时整个输入视图重建。但**拖动过程中不能这样做**（太重，会闪），见下一条。

### 4. 拖动过程中的实时预览

拖动时不要每一帧都重建 `Keyboard`，而是：

- 只改 `InputView` 里键盘容器的 `layoutParams.height`，并对 `KeyboardView` 做纵向
  `scaleY`（以底部为锚点），或者临时把行高按比例重算一次；
- 松手（或点「完成」）时才写入偏好，触发一次真正的重建。

### 5. 调整模式的界面

- 新建 `ime/keyboard/KeyboardResizeOverlay.kt`：覆盖在键盘上方的一层 View，
  包含拖动手柄、百分比文字、「重置」「完成」按钮，自己处理触摸事件
  （按下手柄后 `ACTION_MOVE` 换算成新的比例）。
- 由 `InputView` 负责显示和隐藏这一层，调整模式期间拦截键盘的触摸事件。

### 6. 入口：新增一个 function 命令

- `ime/keyboard/CommonKeyboardActionListener.kt` 第 196 行的 `when (action.command)` 里加：

  ```kotlin
  "resize_keyboard" -> inputView?.enterKeyboardResizeMode()
  ```

- 在 `ios.trime.yaml` 里加预设按键并放进工具栏或「中」键：

  ```yaml
  preset_keys/ios_resize: {label: 调整大小, send: function, command: resize_keyboard}
  ```

  工具栏按钮通过主题的 `tool_bar/buttons` 配置，`action` 填 `ios_resize`
  （`ime/bar/InputBarDelegate.kt` 第 127 行会把 action 交给 `KeyActionManager`）。

## 边界情况

- **横竖屏切换**：分别读取各自的比例；调整模式中旋转屏幕时直接退出调整模式（不保存）。
- **悬浮或单手模式**：如果以后加单手模式，高度比例照样适用，宽度另算。
- **液体键盘（emoji、剪贴板）**：高度跟随键盘高度，确认它读的是缩放后的值。
- **最小高度**：按键过矮时（如小于 32dp）字会被裁掉，下限要保证每行至少 32dp。
- **主题里单个键盘写了自己的 `keyboard_height`**：同样乘以比例，保持一致。

## 测试清单

- [ ] 拖动时键盘实时变化，不闪烁、不卡顿
- [ ] 点「完成」后切到其他 App 再回来，高度保持
- [ ] 重启手机后高度保持
- [ ] 竖屏、横屏分别调整，互不影响
- [ ] 「重置」回到 100%
- [ ] 切换数字、符号键盘时高度一致
- [ ] 切换主题后比例仍然生效
- [ ] 深色模式下调整界面颜色正常

## 工作量估计

| 部分 | 估计 |
|---|---|
| 偏好与高度计算（第 1–3 节） | 小 |
| 拖动预览与覆盖层界面（第 4–5 节） | 中，主要工作量在这里 |
| 入口与主题配置（第 6 节） | 小 |

如果想先快速可用，可以分两步：先做「设置里加滑块」（只需要第 1–3 节，再加一个设置项），
之后再在同一套偏好上补上拖动界面。
