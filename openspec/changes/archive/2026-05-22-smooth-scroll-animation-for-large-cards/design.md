## Context

当前 `LaunchedEffect(tailChangeKey)` 中使用 `listState.animateScrollToItem(uiState.timeline.size)` 触发跟随滚动。此 API 使用 Compose 内部的默认 spring 动画，无法自定义动画曲线或时长。对于大卡片（option/route/hotel）插入时的大量内容变化，spring 动画过快地"弹"到目标位置，缺少平滑过渡感。

## Goals / Non-Goals

**Goals:**
- 将贴底跟随滚动从默认 spring 动画改为自定义 tween 动画，使滚动过渡平滑可感知。
- 动画速度自适应：内容变化大时过渡更明显，变化小时快速跟上。
- 用户发消息的强制跟随也使用同款平滑动画。
- 不改变现有的 isNearBottom 检测逻辑。

**Non-Goals:**
- 不改动 agent loop、卡片 schema、工具协议。
- 不添加滚动动画配置 UI。

## Decisions

### 1. 用 `animateScrollBy` + `tween` 替代 `animateScrollToItem`

`animateScrollToItem` 无法自定义动画参数。改用 `listState.scroll { animateScrollBy(value, tween) }`：

- `value = 10_000f`（远大于实际需要的像素数），动画会自动 clamp 到内容底部。
- `tween(durationMillis = 350, easing = FastOutSlowInEasing)`：350ms 的 decelerate 曲线，滚动速度一眼可感知，不突兀。

备选方案是手动计算目标 offset 再用 `Animatable` 驱动，但实现复杂且易出错；`overscroll + clamp` 方案更稳健。

### 2. 保留 `withFrameNanos` 等待布局

和现有逻辑一样，先用 `withFrameNanos { }` 等一帧确保 LazyColumn 完成新内容的 layout，再决定是否需要滚动。

### 3. 不再需要区分「已到底」和「接近底部」

`animateScrollBy(10_000f)` 在已到底部时实际滚动距离为 0，动画立即结束。所以不需要先判断是否已在底部——让动画自然处理即可。

## Risks / Trade-offs

- [风险] `animateScrollBy` 在快速连续调用时可能产生动画堆积 → [缓解] LaunchedEffect restart 自动取消前一个协程（含动画），行为与现有方案一致。
- [风险] overscroll 值太大可能导致某些 Compose 版本行为异常 → [缓解] 10_000px 是合理的安全值，远小于 Int 上限，且 Compose 内部对越界 scrollBy 有 clamp 保护。
