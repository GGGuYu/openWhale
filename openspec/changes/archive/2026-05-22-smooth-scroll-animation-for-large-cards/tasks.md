## 1. Replace scroll animation (attempted, partially reverted)

- [x] 1.1~1.3 尝试用 `animateScrollBy` / `scroll { animate { scrollBy } }` 自定义 tween，均不理想，已回退为混合方案。
- [x] 文字流式跟随已恢复为 `animateScrollToItem`（spring），行为正常。

## 2. Verify

- [x] 2.1 Run existing unit tests, ensure no regressions.
- [ ] 2.2 Manual validation: send "导航到静安寺" and observe card appearance scrolls smoothly with visible tween transition.

## 3. UNRESOLVED: 大卡片插入无平滑滚动

- [x] 3.1 当 option card / route card / hotel list card / 大型 tool feedback 插入到时间线时，聊天内容大幅增长，列表使用 snapshotFlow 等待 LazyColumn 完成布局后再以 scroll + withFrameNanos 循环驱动 500ms FastOutSlowInEasing 平滑滚动动画。

### 现象
- 文字流式输出时，`animateScrollToItem` 的 spring 动画跟随正常。
- 但当大卡片（option/route/hotel/tool）出现时，即使代码走到了 `scroll { animate { scrollBy(delta) } }` + 500ms `tween(FastOutSlowInEasing)` 分支，用户体感仍然是"闪现"——没有可见的、持续 0.5 秒的从容滑动。
- 当前代码在 `MainScreen.kt` 的 `LaunchedEffect(tailChangeKey)` 中采用混合策略：
  - 距离 < 100px 或非卡片/tool → `animateScrollToItem`（spring）
  - 距离 >= 100px 且是卡片/tool → `scroll { animate { scrollBy } }` + 500ms tween

### 已尝试的方案（均不理想）
1. `listState.animateScrollBy(10_000f, tween(350))` — 10_000f overscroll 太大，动画第 1 帧就消耗完，体感生硬。
2. `scroll { animate { scrollBy(delta) } }` + `distanceFromContentBottom()` — 初版有方向 bug（`distanceFromContentBottom` 对超出屏幕的卡片返回 0，导致不滚动）。
3. 修复方向 bug + 自适应时长 — 流式时动画频繁被 `LaunchedEffect` 重启打断，变成卡顿抖动。
4. 混合方案（文字用 spring，卡片用 tween 500ms）— 文字跟随正常了，但卡片滚动依然不平滑。

### 关键文件
- `app/src/main/java/com/example/openwhale/ui/main/MainScreen.kt`
  - `distanceFromContentBottom()` 函数（line ~1270）
  - `LaunchedEffect(tailChangeKey)` 自动跟随逻辑（line ~184）
  - `LaunchedEffect(uiState.timeline.size)` 用户消息强制跟随（line ~210）

### 参考
- Pi 项目（`/Users/bytedance/guyu/project/pi`）的 agent loop / tool / provider 模式（TypeScript），但没有 UI 层参考价值。
- 当前 OpenWhale 使用 Jetpack Compose `LazyColumn` + `LazyListState`。
- `animateScrollToItem` 内部实现：`scroll { snapToItemIndexInner(); animate(0f, target, spring()) { scrollBy(delta) } }`，spring 不可替换。
