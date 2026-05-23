# UI 层

> 入口链路、布局结构、ViewModel 状态管理、组件树、Timeline 渲染分发、卡片渲染、主题系统。适合理解 UI 架构和添加新 UI 组件时阅读。

## 入口链路

### MainActivity

`MainActivity`（`MainActivity.kt`）是 Android 应用入口：

1. `onCreate()` 中调用 `applyDemoOrientationLock()`
   - 小屏设备（`smallestScreenWidthDp < 600`）：锁定竖屏（`SCREEN_ORIENTATION_PORTRAIT`）
   - 大屏/平板：允许自由旋转（`SCREEN_ORIENTATION_FULL_USER`）
2. `enableEdgeToEdge()` — 系统栏沉浸
3. `WindowCompat.setDecorFitsSystemWindows(window, false)` — 内容延伸到系统栏后面
4. `SOFT_INPUT_ADJUST_RESIZE` — 键盘弹出时 resize 窗口（而非 pan）
5. `setContent { OpenWhaleTheme { MainNavigation() } }`

### Navigation

`MainNavigation()`（`Navigation.kt`）当前极简：直接调用 `MainScreen(modifier = Modifier, contentPadding = 16.dp)`。

`NavigationKeys.kt` 中定义了 `@Serializable data object Main : NavKey`（`androidx.navigation3.runtime.NavKey`），为后续多屏导航预留。

## 布局结构

`MainScreen` 有两个重载（`MainScreen.kt:114-340`）：

1. **公共入口**（line 114）：接收可选的 `viewModel`，若未传入则通过 `MainScreenViewModel.create(appContext)` 创建
2. **内部实现**（line 142）：接收 `uiState: AgentSessionSnapshot` 和回调

**布局层级（Column 结构）：**

```
Column(fillMaxSize, background, safeDrawing padding) {
    ChatTopBar                    // 固定顶栏
    Box(weight(1f)) {            // 占据剩余空间
        LazyColumn                // 聊天时间线（可滚动）
          → items(timeline, key=TimelineItem::id) {
              TimelineBubble       // 根据 role 分发渲染
          }
        // 空态引导（timeline.isEmpty()）
        // 居中 Column: 🐋 + "你好，我是 OpenWhale" + 副标题

        ComposerBar               // 底部输入栏（绝对定位 BottomCenter）
    }
}
```

**各组件位置关系：**
- `ChatTopBar`：屏幕顶部，包含菜单按钮、标题、模型状态指示灯、新对话按钮
- `LazyColumn`：内容区域，`contentPadding` 底部为 ComposerBar 高度 + 12dp，确保最后一条消息不被输入栏遮挡
- `ComposerBar`：绝对定位在 Box 底部，带顶部圆角和阴影
- `HistorySheet` 和 `SettingsSheet`：条件渲染的 ModalBottomSheet

## 状态管理

`MainScreenViewModel`（`MainScreenViewModel.kt`）包装 AgentSession，生成 UI 订阅的 StateFlow：

```kotlin
val uiState: StateFlow<AgentSessionSnapshot> =
    combine(agentSession.snapshot, agentSession.debugEvents) { snapshot, debugEvents ->
        snapshot.copy(debugEvents = debugEvents)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = agentSession.snapshot.value,
    )
```

- **combine**：将 snapshot 和 debugEvents 合并，每次 snapshot 更新时同步最新 debugEvents
- **WhileSubscribed(5_000)**：5 秒内无订阅者时停止上游收集（节省资源）
- UI 通过 `collectAsStateWithLifecycle()` 订阅

**ViewModel 暴露的方法：** 全部通过 `viewModelScope.launch` 委托给 AgentSession 的对应 suspend 方法：
`sendMessage`, `selectWorkflowPack`, `submitSelection`, `updateApiKey`, `resetSession`, `updateModelId`

**创建方式：** `MainScreenViewModel.create(context)` 通过 `OpenWhaleAppContainer(context).createSession()` 创建 AgentSession。

### 自动滚动逻辑

MainScreen 中有两个 `LaunchedEffect` 管理滚动：

1. **底部跟随**（`LaunchedEffect(tailChangeKey)`）：
   - `tailChangeKey` 基于 timeline.size + last.id + last.cardPayload.type + last.isStreaming + last.text.length 计算
   - 当用户在底部（`listState.isNearBottom(96dp)`）时：
     - 卡片/工具类大内容：通过 `listState.scroll` 以 ~2000px/s 平滑滚动
     - 文本/其他：通过 `listState.animateScrollToItem` 动画滚动
   - 用户不在底部时不跟随

2. **用户消息强制滚动**（`LaunchedEffect(uiState.timeline.size)`）：
   - 当最后一条是 User 角色时，总是执行 `animateScrollToItem`

**`isNearBottom(thresholdPx)` 检测逻辑：**
- 判断 0-2 个 item 在视口下方时认为用户 "在底部"（新插入的卡片可能还未滚动进入视口）
- 3 个以上 item 在视口下方时认为用户在浏览历史，不跟随

## 组件树（文字层级）

```
MainScreen
├── ChatTopBar
│   ├── IconButton(菜单 → showHistorySheet)
│   ├── Column(标题 + 模型状态)
│   │   ├── Text("OpenWhale")
│   │   └── Row(状态指示灯 + 模型名)
│   └── IconButton(新对话 → onResetSession)
├── Box
│   ├── LazyColumn
│   │   └── TimelineBubble (for each timeline item)
│   │       ├── [Thinking] → ThinkingTraceCard
│   │       ├── [Tool] → ToolExecutionCard
│   │       ├── [Status] → StatusEventCard
│   │       └── [User/Assistant] → Card(聊天气泡)
│   │           ├── MessageHeader (avatar + roleLabel + streaming 状态)
│   │           ├── MarkdownText / Text
│   │           └── CardContent (if cardPayload != null)
│   │               ├── OptionCardPayload → OptionCard
│   │               ├── RouteCardPayload → RouteCard
│   │               └── HotelListCardPayload → HotelListCard
│   ├── 空态引导 (timeline 为空时)
│   └── ComposerBar
│       ├── OutlinedTextField (消息输入)
│       └── Button (发送 / 处理中)
├── HistorySheet (条件渲染)
│   ├── 工作流选择器 (AssistChip 切换)
│   └── 设置入口
└── SettingsSheet (条件渲染)
    ├── ApiKeyPanel
    ├── ModelSelectionPanel
    └── DebugPanel
```

## Timeline 渲染分发

`TimelineBubble`（`MainScreen.kt:724`）根据 `TimelineItem.role` 分发：

| Role | 渲染组件 | 视觉特征 |
|------|----------|----------|
| `Thinking` | `ThinkingTraceCard` | 折叠/展开 切换，显示思考轨迹原文，"🧠" 标记，流式时有加载动画 |
| `Tool` | `ToolExecutionCard` | 紧凑卡片，emoji marker（🔎/🧭/🏨/🪄/🛠️），显示 toolName 和结果文本 |
| `Status` | `StatusEventCard` | 全宽浅色条，title + text（错误、配置变更等状态事件） |
| `User` | Card(聊天气泡) | 右对齐，primaryContainer 背景色，"🙂" 头像，纯文本 |
| `Assistant` | Card(聊天气泡) | 左对齐，surfaceContainerLow 背景色，"🐋" 头像，Markdown 渲染 + 可能的 cardPayload |

**工具反馈 marker 映射：**
- 包含 "search" → 🔎
- 包含 "route" 或 "map" → 🧭
- 包含 "hotel" → 🏨
- 包含 "emit" → 🪄
- 其他 → 🛠️

## 卡片渲染

### OptionCard

渲染 `OptionCardPayload`：
- `BusinessCardSurface(label, trailingChip)` 外壳
- title + description
- options 列表：每个 option 一个 `OutlinedButton`，点击调用 `onSelectionSubmit(option.action)`
- `isConsumed`（cardId 在 consumedCallbackCardIds 中）时所有按钮 disabled，显示 "已处理"
- `allowCustomInput` 时显示提示面板

### RouteCard

渲染 `RouteCardPayload`：
- `BusinessCardSurface(label="路线方案")` 外壳
- destinationName + destinationAddress
- 4 个出行模式 FilterChip 切换（🚗/🚌/🚶/🚲）
- `RouteModePanel`：选中模式的详细信息（title, durationMinutes, distanceKm, summary）
- "打开地图" 按钮调用 `onOpenLink(openMapAction.uri)`
- 不支持的高德 scheme（`amapuri://`）在 `openExternalLink` 中有 fallback 处理：降级到 `https://uri.amap.com/navigation`

### HotelListCard

渲染 `HotelListCardPayload`：
- `BusinessCardSurface(label="酒店列表")` 外壳
- 锚点目的地 + 筛选条件摘要 + 排序标签
- 平台状态 chips（携程/美团，含匹配数/无匹配）
- 酒店列表：每个酒店一个 `HotelRow`
  - 酒店名称 + 距离 + 简介
  - 各平台报价行（`PlatformQuoteRow`）：平台名 + ¥价格（最低价高亮）+ "打开XX" 按钮

### BusinessCardPalette

`rememberBusinessCardPalette()` 为所有业务卡片提供统一的配色方案（containerColor/surface, outlineColor, contentColor/surface, supportingColor/surfaceVariant 等），确保卡片与普通消息气泡的视觉层级分离。

## 主题系统

品牌色定义在 `theme/Color.kt`：

| 颜色 | 值 | 用途 |
|------|-----|------|
| `WhaleInk` | `#1C2431` | 主文本色 |
| `WhaleInkSoft` | `#344052` | 次要文本色 |
| `WhaleCanvas` | `#F4F7FB` | 浅色背景 |
| `WhaleSurface` | `#EAF0F7` | 浅色表面 |
| `WhaleAccent` | `#2563FF` | 品牌强调色（蓝） |
| `WhaleAccentSoft` | `#D8E4FF` | 柔和强调色 |
| `WhaleSuccess` | `#1D7A53` | 成功/就绪 |
| `WhaleWarning` | `#C97812` | 警告 |

暗色模式：
| 颜色 | 值 |
|------|-----|
| `WhaleDarkCanvas` | `#101722` |
| `WhaleDarkSurface` | `#182231` |
| `WhaleDarkAccent` | `#81A7FF` |

**Material3 主题（`Theme.kt`）：**
- `OpenWhaleTheme(darkTheme = false)` Composable
- `LightColorScheme`：primary=WhaleAccent，background=WhaleCanvas，surface=White，onSurface=WhaleInk
- `DarkColorScheme`：primary=WhaleDarkAccent，background=WhaleDarkCanvas，surface=WhaleDarkSurface

**Typography（`Type.kt`）：**
- `headlineMedium`：28sp SemiBold
- `titleLarge`：22sp SemiBold
- `titleMedium`：16sp Medium
- `bodyLarge`：16sp Normal，lineHeight=24sp

## 子组件一览

| 组件 | 位置 | 职责 |
|------|------|------|
| `ChatTopBar` | MainScreen 顶部 | 标题栏 + 菜单/新对话按钮 + 模型状态指示灯 |
| `ComposerBar` | Box BottomCenter | 消息输入 + 发送按钮，带顶部圆角和阴影 |
| `HistorySheet` | ModalBottomSheet | 工作流选择器 + 设置入口 |
| `SettingsSheet` | ModalBottomSheet | API Key 配置 + 模型选择 + 调试信息 |
| `ApiKeyPanel` | SettingsSheet 内 | DeepSeek Key 输入/保存/恢复，带状态显示 |
| `ModelSelectionPanel` | SettingsSheet 内 | 模型选择 FilterChip 横向滚动 |
| `DebugPanel` | SettingsSheet 内 | 最近 8 条调试事件，按严重程度着色 |
| `TimelineBubble` | LazyColumn items | 根据 role 分发到不同渲染组件 |
| `ThinkingTraceCard` | TimelineBubble 内 | 思考轨迹折叠/展开 |
| `ToolExecutionCard` | TimelineBubble 内 | 工具反馈紧凑卡片 |
| `StatusEventCard` | TimelineBubble 内 | 状态事件全宽条 |
| `CardContent` | User/Assistant 气泡内 | 根据 cardPayload type 分发到 OptionCard/RouteCard/HotelListCard |
| `MarkdownText` | Assistant 气泡内 | 简易 Markdown 渲染（加粗 + 列表符号） |
| `MessageHeader` | User/Assistant 气泡内 | 头像 marker + 角色标签 + streaming 状态 |
| `BusinessCardSurface` | 卡片内 | 统一卡片外壳（label chip + 内容） |
