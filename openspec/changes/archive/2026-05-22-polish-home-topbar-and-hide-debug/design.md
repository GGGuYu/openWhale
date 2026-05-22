## Context

当前 `MainScreen` 使用 `Box` 叠加 `LazyColumn` + `ComposerBar` 的布局结构，顶部 `ChatTopShell` 作为 `LazyColumn` 的第一个 item 随聊天内容一起滚动。这个 shell 承载了标题、API Key 状态、工作流选择器、调试状态摘要等大量内容，在演示视频中显得杂乱，不符合 DeepSeek 官方 App 的简洁体验。

参考 DeepSeek 官方 App 布局：固定顶栏（不滚动）+ 中间聊天区 + 底部输入栏。调试元素全部隐藏到二级界面（侧滑面板）。

## Goals / Non-Goals

**Goals:**
- 新增固定 TopAppBar，不随 LazyColumn 滚动
- TopAppBar 左侧 hamburger 图标（打开历史/设置面板），中间标题 + API Key 状态行，右侧 "新对话" 按钮
- API Key 状态用彩色圆点 + 文字表示：🟢 模型名 / 🔴 模型 API Key 未填写
- 历史面板增加设置入口和工作流选择器
- 隐藏 ChatTopShell 中的调试元素（StatusPill、CompactWorkflowSelector、SessionContextSummary）

**Non-Goals:**
- 不改动聊天消息渲染逻辑
- 不改动卡片显示逻辑
- 不改动后台 agent loop 逻辑
- 不新增真正的历史持久化功能
- 不改变设置面板的 API Key 配置功能

## Decisions

### 1. 用 Column(顶栏 + LazyColumn + 底栏) 替代 Box 叠加布局

当前布局是 `Box(fillMaxSize) { LazyColumn(...) + ComposerBar(align=BottomCenter) }`。改用 `Column { TopAppBar; Box(weight=1f) { LazyColumn; ComposerBar(align=BottomCenter) } }`，顶栏固定在顶部不随列表滚动。

### 2. 用 IconButton 替代 AssistChip 实现顶栏按钮

hamburger 用 `Icons.AutoMirrored.Filled.List`（两根杠），新对话用 `Icons.AutoMirrored.Filled.Edit`（编辑/新对话图标），比 AssistChip 更轻量、更像标准 App Bar。

### 3. HistorySheet 改造成侧滑面板

当前 `HistorySheet` 是一个 `ModalBottomSheet`，只显示历史列表。改造为从左侧滑出的 `DrawerState` 或用一个全屏 `ModalBottomSheet` 覆盖，内部区域分为：历史列表区 + 底部设置/工作流入口。

### 4. API Key 状态用圆点 + 文字显示在顶栏副标题

```kotlin
Row(verticalAlignment = CenterVertically, spacedBy = 6.dp) {
  Box(size = 8.dp, background = if (hasKey) Green else Red, CircleShape)
  Text(if (hasKey) modelLabel else "模型 API Key 未填写")
}
```

## Risks / Trade-offs

- [风险] `DrawerState` 需要 `ModalNavigationDrawer` 包裹整个屏幕，可能影响现有布局结构 → [缓解] 使用全屏 `ModalBottomSheet` 实现侧滑面板效果，简单可控
- [风险] 移除 ChatTopShell 后首屏缺少工作流选择入口 → [缓解] 在侧滑面板中提供工作流选择器
