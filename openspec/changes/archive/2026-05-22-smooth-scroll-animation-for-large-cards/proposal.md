## Why

当前贴底自动跟随使用 Compose 默认的 `animateScrollToItem` 动画，对于 option/route/hotel 等大卡片出现时，滚动动作过快、缺乏可感知的平滑过渡，看起来像瞬跳而非从容滑动。demo 视频需要更优雅的视觉节奏。

## What Changes

- 自动跟随滚动从默认 spring 动画改为更柔和的自定义动画（如 tween/ease），让大卡片出现时的下滑有明显的平滑过渡感。
- 动画速度可根据内容变化量自适应：小变化（流式文字追加）快速跟随，大变化（卡片插入）使用更长的动画时长。
- 保留现有的 isNearBottom 检测逻辑和离开底部不打断的约束不变。

## Capabilities

### New Capabilities

<!-- None -->

### Modified Capabilities

- `mobile-agent-runtime`: 收紧贴底自动跟随的动画质量要求，要求滚动动画平滑可感知，不再使用默认瞬跳式动画。

## Impact

- 受影响代码仅限 `MainScreen.kt` 中 `LaunchedEffect(tailChangeKey)` 的动画调用。
- 不改变触发逻辑、不改变卡片 schema、不改变 agent loop。
