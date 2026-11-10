# 方案：拖动调节键盘高度

状态：**已实现**（2026-11-08 记录，2026-11-10 按代码核对后修正并实现，模拟器测试通过）

## 目标

像 Gboard 一样，在键盘上直接拖动来调整键盘高度，所见即所得，不用改主题文件、不用重新部署。

## 现状（实现前）

- 键盘高度完全由主题决定：`ios.trime.yaml` 里的 `style/keyboard_height`（竖屏，236dp）
  和 `style/keyboard_height_land`（横屏，180dp）。单个键盘布局也可以用自己的
  `keyboard_height` 覆盖。
- App 设置里没有调节高度的选项，只能写 `ios.trime.custom.yaml` 再部署。

## 交互设计

1. **入口**
   - 工具栏 `•••` 菜单里的「调整键盘高度」（任何主题都有）；
   - 键盘上：「中/英」键**下滑**（`ios.trime.yaml` 里的 `ios_resize` 预设键）；
   - 设置里：「虚拟键盘」页面的「键盘高度（竖屏）」「键盘高度（横屏）」两个滑块。
2. **进入调整模式**：
   - 键盘上盖一层半透明遮罩，按键不可点击；
   - 遮罩**上边缘**是一条拖动手柄（带上下箭头）；
   - 中间显示当前高度百分比（如「100%」）；
   - 底部两个按钮：「重置」「完成」。
3. **拖动**：按住手柄上下拖，键盘实时变高变矮，各行按比例缩放。
   - 范围：主题高度的 **70% ～ 150%**；同时不超过屏幕高度的一半，每行不低于 32dp；
   - 每 1% 一档，变化时有轻微振动反馈。
4. **完成**：只能点「完成」退出并保存。
   （~~点键盘外区域完成~~：输入法收不到自己窗口以外的触摸事件，做不到。）
5. **重置**：恢复到 100%，仍停留在调整模式，点「完成」才保存。
6. **横竖屏分别记忆**：按屏幕实际方向（`Configuration.orientation`）区分，
   在哪个方向下调整就只改哪个方向。
7. **中途放弃**：调整模式中收起键盘或旋转屏幕，直接退出，不保存。

## 技术方案

### 1. 存储：保存缩放比例

`AppPrefs.Keyboard` 加两个带设置界面的偏好（70～150，单位 %）：

```kotlin
val keyboardHeightScale = int(R.string.keyboard_height_scale, KEYBOARD_HEIGHT_SCALE, 100, 70, 150, "%")
val keyboardHeightScaleLand = int(R.string.keyboard_height_scale_land, KEYBOARD_HEIGHT_SCALE_LAND, 100, 70, 150, "%")
```

存比例而不是 dp，换主题后依然有效。

### 2. 应用比例

`ime/keyboard/Keyboard.kt` 的 `keyboardHeight`（原第 147 行）：先算出主题高度
`baseKeyboardHeight`，再乘以 `KeyboardPrefs.keyboardHeightScale()`。
行高本来就按总高度等比分配（第 228–240 行），不用改。

`KeyboardPrefs.keyboardHeightScale()` 优先返回调整模式中的**临时预览比例**
（`previewHeightScale`），没有时才读偏好。

### 3. 只重建键盘，不重建整个输入视图

- `KeyboardWindow` 新增 `reloadKeyboards()`：清空 `cachedKeyboards`（第 79 行），
  按当前比例重新构造**当前**键盘并挂上，再通过 `_currentKeyboardHeight` 发出新高度，
  `InputView` 第 201 行收到后改窗口高度。其他键盘下次切换时按新比例构造。
- 重建时保留当前键盘的 `lastAsciiMode`，且不走 `attachKeyboard` 里切换中英文的逻辑，
  避免调整高度时把中英文状态切掉。
- 偏好变化（设置页滑块、调整模式点「完成」）由 `TrimeInputMethodService` 监听，
  调用 `inputView?.onKeyboardHeightScaleChanged()` → `reloadKeyboards()`；
  **不**放进 `recreateInputViewPrefs`，避免整个输入视图重建造成闪烁。

### 4. 拖动过程中的实时预览

不用 `scaleY`（会把字纵向拉伸）。拖动时每变化 1% 就：

1. 更新 `previewHeightScale`；
2. 调用 `reloadKeyboards()` 重建当前键盘（只是一个键盘的布局计算，开销很小）；
3. 用 `post` 合并同一帧内的多次变化，避免快速拖动时重复重建。

位移用 `MotionEvent.rawY` 计算：拖动时手柄会随键盘一起移动，用相对坐标数值会来回跳。

### 5. 调整模式的界面

新建 `ime/keyboard/KeyboardResizeOverlay.kt`：一个 `FrameLayout`，在 `InputView` 里
与键盘区域（`windowManager.view`）约束到同一位置，默认隐藏。

- 自己消费所有触摸事件，保证下面的按键不会被点到；
- 上边缘手柄处理 `ACTION_DOWN/MOVE/UP`；
- `onConfigurationChanged`（旋转）时取消调整。

`InputView` 提供 `enterKeyboardResizeMode()` / `exitKeyboardResizeMode(save)`；
`TrimeInputMethodService.onFinishInputView` 时调用 `exitKeyboardResizeMode(false)`。

### 6. 入口：新增 function 命令

`CommonKeyboardActionListener.handleFunctionCommand`（第 196 行）加：

```kotlin
"resize_keyboard" -> inputView.enterKeyboardResizeMode()
```

`ios.trime.yaml`：

```yaml
preset_keys/ios_resize: {label: 调整高度, send: function, command: resize_keyboard}
# 「中/英」键加 swipe_down: ios_resize
```

`•••` 菜单（`ime/switches/SwitchOptionWindow.kt`）里加一个固定项
`SwitchOptionEntry.Static.Type.ResizeKeyboard`，点击直接调用 `inputView.enterKeyboardResizeMode()`。

工具栏按钮暂不加：iOS 主题没有配置 `tool_bar`，图标走字体文件，单独为它配一套工具栏改动面大，以后需要再加。

## 边界情况

- **横竖屏切换**：分别读各自的比例；调整中旋转直接退出，不保存。
- **液体键盘（emoji、剪贴板）**：沿用同一个窗口的高度，自动跟随。
- **最小高度**：调整下限取 `max(70%, 行数 × 32dp / 主题高度)`。
- **单个键盘自带 `keyboard_height`**：同样乘以比例。
- **调整中切换到别的键盘（数字、符号）**：调整模式下按键被遮罩挡住，不会发生。

## 测试清单

- [ ] `•••` 菜单「调整键盘高度」进入调整模式
- [ ] 「中/英」键下滑进入调整模式
- [ ] 拖动时键盘实时变化，不闪烁、不卡顿，字形不变形
- [ ] 上下限：最小 70%，最大不超过 150% 或半屏
- [ ] 点「完成」后切到其他 App 再回来，高度保持；重启后保持
- [ ] 竖屏、横屏分别调整，互不影响
- [ ] 「重置」回到 100%
- [ ] 切换数字、符号键盘、emoji 面板时高度一致
- [ ] 调整中收起键盘或旋转屏幕：退出且不保存
- [ ] 调整前是英文模式，调整后仍是英文模式
- [ ] 设置页滑块修改后，键盘高度跟着变
- [ ] 切换主题后比例仍然生效
- [ ] 深色模式下调整界面正常
