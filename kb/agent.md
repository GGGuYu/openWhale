# Agent 运行时

> AgentSession 生命周期、AgentLoopRunner 执行流程、核心数据模型、ModelProvider 接口。适合理解 Agent 核心逻辑时阅读。

## AgentSession 概述

`AgentSession`（`agent/AgentSession.kt`）是 Agent 运行时的唯一入口，管理整个对话生命周期。

**构造函数参数：**
- `modelProvider: ModelProvider` — 大模型调用接口
- `modelConfig: ModelConfig` — 初始模型配置（providerId, modelId, baseUrl, apiKey, supportedModelIds, streamingEnabled）
- `toolRegistry: AgentToolRegistry` — 工具注册表
- `workflowPromptPackRepository: WorkflowPromptPackRepository` — 工作流仓库
- `initialWorkflowPackId: String` — 初始工作流 ID
- `debugLogger: AgentDebugLogger` — 调试日志（默认 NoopAgentDebugLogger）
- `localModelConfigStore: LocalModelConfigStore?` — 本地配置存储（API Key / ModelId 覆写）

**持有的核心资源：**
- `conversationHistory: MutableList<ProviderConversationMessage>` — 发送给模型的完整对话历史
- `loopRunner: AgentLoopRunner` — Agent Loop 执行器（构造时创建，不可替换）
- `modelConfig: ModelConfig` — 当前生效的模型配置（可能被本地覆写）
- `sessionContextState: SessionContextState` — 会话结构化状态（目的地、筛选条件、酒店结果等）
- `_snapshot: MutableStateFlow<AgentSessionSnapshot>` — UI 状态聚合，通过 `snapshot` 暴露为只读 StateFlow
- `debugEvents: StateFlow<List<DebugEvent>>` — 调试事件流

**初始化 Snapshot：**
- `initialTimeline()` 目前返回空列表（没有 starter 消息）
- snapshot 包含当前工作流的 workflowPacks 列表、模型信息、API Key 状态等

## 关键方法

### sendUserMessage(text: String)
用户发送消息的入口。流程：
1. 校验 text 非空 且 isSending == false（防重入）
2. 委托 `sendInput(userText, displayText)` 执行实际逻辑

### submitSelection(action: SelectionAction)
卡片选择回调入口。流程：
1. 校验 isSending == false
2. 检查 `consumedCallbackCardIds` 防重复点击
3. 调用 `sessionContextState.applySelection(action)` 更新状态（根据 action 更新 destination / hotelFilterContext / hotelResultsByPlatform 等）
4. 委托 `sendInput(userText = action.promptText, displayText = action.displayText, currentState)` 继续对话

### switchWorkflowPack(packId: String)
切换工作流。副作用：
- 清空 conversationHistory
- 重置 sessionContextState
- 重置 timeline 为 `initialTimeline(selectedPack)`（当前返回空列表）
- 更新 snapshot 中的 workflowPackId、modelLabel、isSending = false

### resetSession()
开始新对话。副作用：
- 清空 conversationHistory
- 重置 sessionContextState
- timeline 重置为 initialTimeline
- 保留当前 workflowPackId 不变

### updateApiKey(apiKey: String)
更新 API Key 覆写。副作用：
- 保存到 SharedPreferences（通过 localModelConfigStore）
- 重新计算 modelConfig（`resolvedModelConfig()`）
- 追加 Status TimelineItem 到 timeline，提示配置变更
- 本地 API Key 优先级高于 BuildConfig 中的 key

### updateModelId(modelId: String)
切换模型 ID。副作用：
- 校验 modelId 在 `supportedModelIds` 中
- 保存到 SharedPreferences
- 重新计算 modelConfig
- 追加 Status TimelineItem

### sendInput（私有）
实际的发送逻辑，核心流程：
1. 追加 `ProviderConversationMessage(role = User)` 到 conversationHistory
2. 设置 isSending = true，追加 User TimelineItem
3. 建立 `activeThinkingItemId` / `activeAssistantItemId` 追踪当前 streaming item
4. 调用 `loopRunner.run()` 并处理 onPlaybackEvent 回调（见 AgentLoopRunner 部分）
5. 成功后：`runCatching {}.onSuccess {}` 中更新 conversationHistory（替换为 loop 返回的完整版本）、sessionContextState、追加最终 timelineItems、设置 isSending = false
6. 失败后：停止所有 streaming item 的 isStreaming 标记，追加 Status TimelineItem(error)

**ModelConfig 解析优先级：**
`resolvedModelConfig()` 中：本地 modelId 覆写 > BuildConfig；本地 apiKey 覆写 > BuildConfig。

## AgentLoopRunner 执行流程

`AgentLoopRunner`（`agent/AgentLoopRunner.kt`）是 Agent Loop 的核心执行器。

**构造函数参数：**
- `modelProvider, toolRegistry, debugLogger` — 依赖注入
- `runtimeOptions: AgentRuntimeOptions` — 运行时配置（`allowCompatibilityCardFallback = false`, `dataToolDelayMs = 1500L`）
- `maxIterations: Int = 6` — 最大 Loop 轮数

**run() 方法：**
```kotlin
suspend fun run(
    modelConfig: ModelConfig,
    workflowPromptPack: WorkflowPromptPack,
    conversationHistory: List<ProviderConversationMessage>,
    currentState: SessionContextState,
    onPlaybackEvent: suspend (AgentPlaybackEvent) -> Unit = {},
): AgentLoopResult
```

**执行流程：**

```
repeat(maxIterations) {
    1. 拼接 systemPrompt = workflowPromptPack.systemPrompt + "\n\n当前会话状态：\n" + currentState.toPromptState()
    2. 调用 modelProvider.complete(request, onStreamEvent)
        - TextDelta → onPlaybackEvent(AssistantUpdate(text, done=false))
        - ThinkingDelta → onPlaybackEvent(ThinkingUpdate(text, done=false))
    3. 模型返回 ProviderResponse（含 text, reasoningContent, toolCalls, finishReason）
    4. syncVisibleBuffer 同步流式 buffer 和完整响应文本
    5. 如果 toolCalls.isEmpty()：
        - 发送最终的 AssistantUpdate（含 cardPayload，如果兼容卡片推断匹配）
        - 发送 ThinkingUpdate(done=true)
        - return AgentLoopResult  // 结束 loop
    6. 如果 toolCalls 不为空：
        - 先发送 AssistantUpdate(done=true) 结束文本流
        - 发送 AssistantBoundary 标记文本段结束
        - prepareToolBatch() → validateToolBatch() → executeToolBatch()
          - 每个工具：ToolStart → execute（Data 工具延迟 1500ms）→ ToolFeedback
        - 将 assistantMessage 加入 workingConversationHistory
        - 将 tool result message 加入 workingConversationHistory
        - 收集 cardTool 的 cardPayload
        - 如果 cardTool 产生了 cardPayload：
            - 发送 AssistantUpdate(text, cardPayload, done=true) 展示卡片
            - 发送 ThinkingUpdate(done=true)
            - return AgentLoopResult  // 卡片产生时结束 loop，等待用户交互
    7. 继续下一轮 loop（模型看到 tool results 后继续思考）
}
达到 maxIterations 时：
    - 追加 Status TimelineItem "Agent loop 达到最大轮次"
    - return AgentLoopResult
```

**关键设计点：**
- **卡片终止 loop**：当 Card 工具（`AgentToolKind.Card`）产生 cardPayload 时，loop 立即结束，等待用户操作卡片。这确保了用户总能在卡片后停下来做选择。
- **Data 工具延迟**：所有 Data 类工具执行前延迟 1500ms（`dataToolDelayMs`），在 UI 上营造 "查询中" 的演示节奏感。Card 工具不延迟。
- **工具批次校验（validateToolBatch）**：三个约束 — 不重复 tool_call id；最多一个 Card 工具；Card 工具必须在所有 Data 工具之后。
- **兼容卡片回退**：当 `allowCompatibilityCardFallback = false`（当前值）时，不回退到文本推断卡片。这是之前为了确保 "显式卡片路径" 的设计决策。

## AgentPlaybackEvent 事件体系

`AgentPlaybackEvent`（`AgentModels.kt:194`）是 sealed interface，agent loop 通过回调发射事件，AgentSession 处理事件并更新 StateFlow，驱动 UI 增量更新。

| 事件 | 携带数据 | 触发时机 | Session 处理 |
|------|----------|----------|-------------|
| `ThinkingUpdate` | turnId, text, done | 模型 streaming 输出 reasoning_content | 增量创建/更新 Thinking TimelineItem（title="思考轨迹"，isStreaming=!done），done 时重置 activeThinkingItemId |
| `AssistantUpdate` | turnId, text, cardPayload, done | 模型 streaming 输出文本 或 工具完成后附带卡片 | 增量创建/更新 Assistant TimelineItem（title="Deepseek"），支持增量 text 追加和 cardPayload 附加 |
| `ToolStart` | turnId, toolItemId, toolName | 开始执行一个工具 | 插入 Tool TimelineItem（isStreaming=true，text 为空） |
| `ToolFeedback` | turnId, item (TimelineItem) | 工具执行完成 | 更新对应 Tool TimelineItem（填充 text、isStreaming=false），或新增 |
| `AssistantBoundary` | turnId | 文本输出结束，准备开始工具执行 | 重置 activeAssistantItemId，标记文本段结束（下一个 AssistantUpdate 会创建新 item） |

**事件时序（典型一轮 tool-using turn）：**

```
ThinkingUpdate(done=false) → ... → ThinkingUpdate(done=true)
  → AssistantUpdate(text, done=false) → ... → AssistantUpdate(text, done=true)
    → AssistantBoundary
      → ToolStart → ToolFeedback
      → ToolStart → ToolFeedback
      → ...
        → [Card 工具] AssistantUpdate(text, cardPayload, done=true)
        → [或] ThinkingUpdate(done=true) → AssistantUpdate(text, done=true)  // 继续下一轮 loop
```

## 核心数据模型

所有核心模型定义在 `agent/AgentModels.kt`。

### AgentSessionSnapshot
UI 状态聚合（`AgentSessionSnapshot`），通过 `AgentSession.snapshot: StateFlow<AgentSessionSnapshot>` 暴露：

- `timeline: List<TimelineItem>` — 时间线列表
- `availableWorkflowPacks: List<WorkflowPromptPack>` — 可用工作流
- `selectedWorkflowPackId: String` — 当前选中工作流
- `providerLabel: String` — provider 标识（如 "deepseek"）
- `modelLabel: String` — 当前模型标识
- `supportedModelIds: List<String>` — 可切换的模型列表
- `sessionContextState: SessionContextState` — 会话结构化状态
- `apiKeyConfigured: Boolean` — API Key 是否已配置
- `hasLocalApiKeyOverride: Boolean` — 是否有本地 API Key 覆写
- `apiKeyStatusText: String` — API Key 状态描述文本
- `debugEvents: List<DebugEvent>` — 调试事件列表
- `isSending: Boolean` — 是否正在发送
- `errorMessage: String?` — 错误信息

### TimelineItem
时间线条目，`timeline` 列表的元素：

- `id: String` — 唯一标识
- `role: TimelineItemRole` — 角色枚举（User, Assistant, Thinking, Tool, Status）
- `title: String` — 展示标题
- `text: String` — 展示文本
- `turnId: String?` — 所属 turn
- `cardPayload: AgentCardPayload?` — 嵌入的卡片数据（仅 Assistant TimelineItem 可能有）
- `isStreaming: Boolean` — 是否仍在流式输出中

### AgentCardPayload 类型体系

`AgentCardPayload` 是 sealed interface，三种子类型：

| 类型 | `type` 字段 | 用途 |
|------|-----------|------|
| `OptionCardPayload` | `"option"` | 通用选择卡片：title, description, options(list), allowCustomInput, customInputHint, cardId |
| `RouteCardPayload` | `"route"` | 路线卡片：destinationName, destinationAddress, routes(list of RouteCardMode), openMapAction(ExternalLinkAction) |
| `HotelListCardPayload` | `"hotel_list"` | 酒店列表卡片：title, anchorDestination, filterSummary, rankingLabel, platformStatuses, hotels(list of HotelCardItem) |

**相关辅助类型：**
- `OptionCardChoice` — 选项（id, title, supportingText, action: SelectionAction）
- `SelectionAction` — 选择行为（promptText, displayText, selectedDestinationName?, maxPrice?, maxDistanceKm?, sourceCardId?）
- `RouteCardMode` — 路线模式（mode, title, durationMinutes, distanceKm, summary）
- `HotelCardItem` — 酒店条目（name, distanceKm, summary, platformQuotes: List<HotelPlatformQuote>）
- `HotelPlatformQuote` — 平台报价（platform, price, actionLabel, actionUri）
- `ExternalLinkAction` — 外部链接（label, uri）
- `HotelListRanking` — 排序枚举（Cheapest, Nearest, Balanced）

### SessionContextState
会话结构化状态，在工具执行过程中被逐步填充：

- `destinationCandidates: List<DestinationCandidate>` — 候选目的地
- `selectedDestination: DestinationCandidate?` — 已确认目的地
- `latestRouteCard: RouteCardPayload?` — 最近查询的路线卡片
- `hotelFilterContext: HotelFilterContext` — 酒店筛选条件（maxPrice?, maxDistanceKm?）
- `hotelResultsByPlatform: Map<String, List<HotelResultItem>>` — 各平台酒店结果（key 为 "携程"/"美团" 等）
- `consumedCallbackCardIds: Set<String>` — 已处理的卡片 ID 集合（防重复点击）

### ModelConfig

- `providerId: String` — 如 "deepseek"
- `modelId: String` — 如 "deepseek-chat" 或 "deepseek-v4-flash"
- `baseUrl: String` — API 地址
- `apiKey: String` — API Key
- `supportedModelIds: List<String>` — 可切换的模型列表
- `streamingEnabled: Boolean` — 是否启用流式

## ModelProvider 接口

`ModelProvider`（`agent/provider/ModelProvider.kt`）定义单方法契约：

```kotlin
interface ModelProvider {
    suspend fun complete(
        request: ProviderRequest,
        onStreamEvent: suspend (ProviderStreamEvent) -> Unit = {},
    ): ProviderResponse
}
```

**ProviderRequest** 包含：systemPrompt, modelConfig, messages (List<ProviderConversationMessage>), tools (List<AgentToolDefinition>)

**ProviderStreamEvent** 是 sealed interface，两种增量事件：
- `TextDelta(delta: String)` — 文本增量
- `ThinkingDelta(delta: String)` — 思考内容增量

**ProviderResponse** 包含：text, reasoningContent, toolCalls (List<AgentToolCall>), finishReason

### DeepSeekModelProvider 实现

`DeepSeekModelProvider`（`agent/provider/DeepSeekModelProvider.kt`）：

- **构造函数**：httpClient (OkHttpClient), json (kotlinx.serialization), debugLogger
- **请求路径**：`POST {baseUrl}/chat/completions`（Bearer Auth）
- **请求体**：ChatCompletionRequest（model, messages, tools, tool_choice="auto", temperature=0.2, stream, thinking config）
- **Thinking 模式**：modelId 不是 "deepseek-chat" 时启用 thinking（`type="enabled"`），同时设置 `reasoning_effort="max"`
- **非流式**：直接解析 `ChatCompletionResponse`，提取 choices[0].message.content / reasoningContent / toolCalls
- **流式**：逐行读取 `data:` SSE 行，解析 `ChatCompletionChunk`，累积 text（→ TextDelta）、reasoningContent（→ ThinkingDelta）、toolCalls（流式构建 tool call builder）
- **reasoning_content 续传**：`shouldReplayReasoning()` 在 thinking mode 启用且存在 reasoningContent 且有 toolCalls 时，在 assistant message 中回传 reasoning_content，确保后续 turn 不会因缺 reasoning_content 而报 400 错误
- **JSON 解析容错**：`parseToolArguments()` 中尝试正常解析，失败后调用 `recoverTrailingJsonObject()`（逐字符删除尾部 `}` 和 `]` 尝试恢复合法 JSON）

**ConversationMessage 到 Wire Message 的转换：**
- User → `{role:"user", content}`
- Assistant → `{role:"assistant", content, reasoning_content(条件性), tool_calls}`
- Tool → `{role:"tool", content, tool_call_id, name}`
