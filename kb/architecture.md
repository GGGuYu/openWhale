# OpenWhale 项目总览

> 项目定位、技术栈、模块地图、数据流向、关键设计决策。适合首次了解项目时阅读。

## 项目定位

OpenWhale 是一个 Android 端 Agent Demo App，面向生活服务场景（导航、找酒店等）。当前处于 **Demo 阶段**，使用 mock 工具模拟真实 API 调用。后续计划接入真实后端 API，Agent Loop 和 Agent Runtime 向 PI 项目看齐。

## 技术栈

- **语言**：Kotlin
- **UI 框架**：Jetpack Compose + Material3（`androidx.compose.material3`）
- **HTTP 客户端**：OkHttp（`okhttp3`）
- **序列化**：kotlinx.serialization（`kotlinx.serialization.json`）
- **导航**：Navigation3（`androidx.navigation3`，当前只有单屏 Main，入口极简）
- **状态管理**：Kotlin StateFlow + ViewModel + `collectAsStateWithLifecycle`
- **构建配置**：Gradle BuildConfig 注入 DeepSeek API Key / Base URL / 默认工作流

## 模块地图

源码位于 `app/src/main/java/com/example/openwhale/`，按 package 分为 4 个模块：

### `agent/` — Agent 运行时（核心）

| 文件 | 职责 |
|------|------|
| `AgentSession.kt` | Agent 会话管理：持有 AgentLoopRunner、conversationHistory、sessionContextState，暴露 snapshot StateFlow 给 UI |
| `AgentLoopRunner.kt` | Agent Loop 执行器：调用 ModelProvider、处理工具调用循环、通过 onPlaybackEvent 回调驱动 UI 增量更新 |
| `AgentModels.kt` | 所有核心数据模型：AgentSessionSnapshot、TimelineItem、AgentPlaybackEvent 体系、AgentCardPayload 子类型、SessionContextState、ModelConfig 等 |
| `AgentToolRegistry.kt` | 工具注册表：管理 RegisteredAgentTool 集合，提供 prepare / execute / finalize 三阶段工具执行管线 |
| `AgentDebugLogger.kt` | 调试日志：AgentDebugLogger 接口 + NoopAgentDebugLogger + InMemoryAgentDebugLogger 实现 |
| `DemoCardPlanner.kt` | 卡片工厂 + 卡片校验器：构建 OptionCardPayload、RouteCardPayload、HotelListCardPayload，以及 AgentCardValidator |
| `DemoToolFactory.kt` | Demo 工具工厂：创建所有 demo 工具（search_destination, get_route_options, search_ctrip_hotels, search_meituan_hotels, emit_option_card, emit_route_card, emit_hotel_list_card） |
| `WorkflowPromptPacks.kt` | 工作流系统：WorkflowPromptPack 数据结构 + DefaultWorkflowPromptPackRepository（提供 general_chat 和 navigation_hotel 两个工作流） |

**子包 `agent/provider/`：**

| 文件 | 职责 |
|------|------|
| `ModelProvider.kt` | ModelProvider 接口：定义 `complete(request, onStreamEvent) -> ProviderResponse` 契约 |
| `DeepSeekModelProvider.kt` | DeepSeek API 实现：OkHttp + SSE 流式解析，支持 thinking mode 和 reasoning_content 续传 |

### `ui/main/` — UI 层

| 文件 | 职责 |
|------|------|
| `MainScreen.kt` | 主聊天界面：ChatTopBar + LazyColumn(TimelineBubble...) + ComposerBar，内嵌 HistorySheet、SettingsSheet，以及所有 Composable 组件（TimelineBubble、OptionCard、RouteCard、HotelListCard、ThinkingTraceCard、ToolExecutionCard 等） |
| `MainScreenViewModel.kt` | ViewModel：包装 AgentSession，通过 `combine(snapshot, debugEvents)` 生成 uiState StateFlow，暴露 sendMessage / selectWorkflowPack / submitSelection 等方法 |

### `data/` — DI 和数据持久化

| 文件 | 职责 |
|------|------|
| `DataRepository.kt` | OpenWhaleAppContainer（DI 容器）：组装 AgentSession、AgentToolRegistry、DeepSeekModelProvider、ModelConfig。内嵌 SharedPreferencesLocalModelConfigStore（本地 API Key / ModelId 覆写存储） |

### `theme/` — 品牌主题

| 文件 | 职责 |
|------|------|
| `Color.kt` | 品牌色定义：WhaleInk, WhaleCanvas, WhaleAccent, WhaleSurface 等 |
| `Theme.kt` | Material3 主题：OpenWhaleTheme Composable，包含 LightColorScheme 和 DarkColorScheme |
| `Type.kt` | Typography 配置：headlineMedium、titleLarge、titleMedium、bodyLarge |

### 根 package

| 文件 | 职责 |
|------|------|
| `MainActivity.kt` | 应用入口：开启 edge-to-edge，设置竖屏锁定（小屏设备），`setContent { OpenWhaleTheme { MainNavigation() } }` |
| `Navigation.kt` | MainNavigation Composable：当前极简，直接调用 MainScreen |
| `NavigationKeys.kt` | NavKey 定义：`@Serializable data object Main : NavKey`（为后续多屏导航预留） |

## 数据流向

完整数据链路（用户发消息到 UI 更新）：

```
UI (ComposerBar 发送按钮)
  → MainScreenViewModel.sendMessage(text)
    → AgentSession.sendUserMessage(text)
      1. 追加 User 消息到 conversationHistory
      2. 追加 User TimelineItem 到 snapshot.timeline
      3. 设置 isSending = true
      4. 调用 AgentLoopRunner.run()
        → ModelProvider.complete()  // DeepSeek API 流式请求
          → onStreamEvent 回调中：
            - TextDelta → AgentPlaybackEvent.AssistantUpdate → 增量更新 Assistant TimelineItem 的 text
            - ThinkingDelta → AgentPlaybackEvent.ThinkingUpdate → 增量更新 Thinking TimelineItem
        → 解析 toolCalls
        → AgentToolRegistry.prepare() / execute() / finalize() 三阶段执行工具
          → ToolStart → 插入 Tool TimelineItem
          → ToolFeedback → 更新 Tool TimelineItem
        → 若 Card 工具产生 cardPayload：
          → AssistantUpdate(text, cardPayload) → 将卡片附加到 Assistant TimelineItem
      5. onSuccess: 更新 conversationHistory, sessionContextState, 追加 timelineItems, isSending = false
      6. onFailure: 停止所有 streaming item，追加 Status TimelineItem(error)
    → AgentSession.snapshot (StateFlow) 更新
  → MainScreenViewModel.uiState = combine(snapshot, debugEvents)
    → UI collectAsStateWithLifecycle 订阅
      → LazyColumn 重绘 timeline
```

关键点：
- **流式回放**：AgentLoopRunner 通过 `onPlaybackEvent` 回调增量发射事件，AgentSession.sendInput() 在处理每个 event 时立即更新 StateFlow，UI 实时看到文本增长
- **工具执行**：Data 工具会延迟 1500ms（`runtimeOptions.dataToolDelayMs`），Card 类工具不会延迟
- **Loop 迭代**：AgentLoopRunner 最多执行 6 轮（`maxIterations = 6`），每轮可能包含多个工具调用 + 模型继续思考

## 关键设计决策

**为什么用 StateFlow 而非 LiveData？**
StateFlow 与 Compose 的 `collectAsStateWithLifecycle` 天然集成，不需要额外 lifecycle owner 绑定。StateFlow 也有更好的冷流/热流语义和 combine 操作符支持（MainScreenViewModel 通过 combine 合并 snapshot + debugEvents）。

**为什么 LazyColumn + 手动滚动控制？**
LazyColumn 适合动态 timeline 列表。手动滚动控制（`LaunchedEffect` + `listState.animateScrollToItem` / `listState.scroll`）实现了：
- 用户在底部时自动跟随新内容（文本流式增长时用 animateScrollToItem，大卡片插入时用 ~2000px/s 平滑滚动）
- 用户主动滚动到历史消息时不打断阅读
- 检测逻辑在 `LazyListState.isNearBottom()` 中（96dp 阈值，允许 0-2 个 item 在视口外）

**为什么 Card 通过 ToolExecutionResult.cardPayload 发射？**
Card 类工具（`AgentToolKind.Card`）的 executor 返回 `ToolExecutionResult(cardPayload = card)`。AgentLoopRunner 检测到有效的 cardPayload 后，通过 `AgentPlaybackEvent.AssistantUpdate` 将卡片附加到 Assistant TimelineItem 上，从而在当前 turn 直接展示，不需要额外模型 round-trip。这解决了 "显式卡片路径" 设计：data 工具负责产出数据，card 工具负责组装展示，两层分离。

**工具批次约束：**
AgentLoopRunner 的 `validateToolBatch()` 强制：
- 同一轮不能有重复 tool_call id
- 同一轮最多一个 Card 工具
- Card 工具必须排在所有 Data 工具之后执行

**为什么用 Mock 数据而非 Web 搜索？**
Demo 阶段，所有工具返回 scripted 数据（静安寺周边）。真实 API 接入后续替换 DemoToolFactory 即可，不影响 AgentSession / AgentLoopRunner 的核心逻辑。

## 版本信息

- **Commit**: `af7dbeb4a1272dff11048f7bccfc43f3fa493c32`
- **阶段**: Demo，所有工具使用 mock 数据
- **后续计划**: 接入真实后端 API → 扩展通用 tools/cards → Agent Loop 向 PI 项目看齐
