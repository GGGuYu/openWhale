# 工作流与工具系统

> WorkflowPromptPack、AgentToolRegistry、工具执行与卡片发射、Demo 工具清单、卡片类型体系、扩展指南。适合理解 Agent 能力系统和添加新工具/卡片时阅读。

## 工作流系统

工作流系统的核心数据类是 `WorkflowPromptPack`（`AgentModels.kt:153`）：

```kotlin
data class WorkflowPromptPack(
    val id: String,         // 唯一标识
    val title: String,      // 展示名称
    val systemPrompt: String,  // 系统提示词
    val starterPrompt: String, // 引导提示语（展示用）
)
```

### DefaultWorkflowPromptPackRepository

`DefaultWorkflowPromptPackRepository`（`WorkflowPromptPacks.kt`）实现 `WorkflowPromptPackRepository` 接口，硬编码两个工作流：

**1. `general_chat` — 通用聊天**
- **systemPrompt**：要求回答简短、直接、可信；优先使用工具；上下文不足时只补一个最关键问题
- **starterPrompt**："试试输入：今天天气适合去散步吗？"

**2. `navigation_hotel` — 导航找酒店 Demo**
- **systemPrompt**：详细的 12 条工作流规则，核心约束：
  1. 严格遵守两段式流程：先 data tool 后 card tool
  2. search_destination 只查数据，有歧义时调用 emit_option_card
  3. get_route_options 只查路线，结果拿到后调用 emit_route_card
  4. "这个附近" 优先复用会话里已选 destination
  5. 找酒店前补齐价格和距离，一次只补一个条件
  6. 同时调用 search_ctrip_hotels 和 search_meituan_hotels
  7. emit_hotel_list_card 排序策略：cheapest / nearest / balanced
  8-12. 语气和格式约束
- **starterPrompt**："建议从"导航到静安寺"开始，随后再说"这个附近的酒店帮我找个便宜的"。"

**工作流切换行为：**
`AgentSession.switchWorkflowPack()` 会清空 conversationHistory 和 sessionContextState，重置 timeline。当前工作流的信息通过 `AgentSessionSnapshot.selectedWorkflowPackId` 暴露给 UI。

## 工具注册表

`AgentToolRegistry`（`agent/AgentToolRegistry.kt`）管理工具的全生命周期。

### 核心类型

**AgentToolDefinition** — 工具定义（发送给模型的 schema）：
```kotlin
data class AgentToolDefinition(
    val name: String,           // 工具名
    val description: String,    // 描述（模型据此决定调用时机）
    val parametersSchema: JsonObject,  // JSON Schema 参数定义
)
```

**RegisteredAgentTool** — 注册后的完整工具：
```kotlin
data class RegisteredAgentTool(
    val definition: AgentToolDefinition,
    val executor: AgentToolExecutor,
    val kind: AgentToolKind = AgentToolKind.Data,
)
```

**AgentToolExecutor** — 函数式接口：
```kotlin
fun interface AgentToolExecutor {
    suspend fun execute(
        arguments: JsonObject,
        currentState: SessionContextState
    ): ToolExecutionResult
}
```

**AgentToolKind** — 工具类型：
- `Data` — 数据工具：返回数据，更新 sessionContextState，不直接产生 UI 卡片
- `Card` — 卡片工具：返回 cardPayload，驱动 UI 展示

### 三阶段执行管线

AgentToolRegistry 提供三个方法，形成标准执行管线：

1. **prepare(toolCall) → PreparedToolCall**：查找 RegisteredAgentTool，校验工具已注册
2. **execute(preparedToolCall, currentState) → ExecutedToolCall**：调用 executor.execute()，传入 arguments 和 currentState
3. **finalize(executedToolCall, toolItemId) → FinalizedToolCall**：生成 toolMessage（追加到 conversationHistory）和 toolFeedbackItem（TimelineItem 展示在 UI）

`FinalizedToolCall` 的结果包含：
- `preparedCall` — 原始工具调用信息
- `result: ToolExecutionResult` — 执行结果（displayText, modelPayload, nextState, cardPayload?）
- `toolMessage: ProviderConversationMessage` — 追加到对话历史的 Tool 消息
- `toolFeedbackItem: TimelineItem` — UI 时间线中展示的反馈条目
- `isCardEmission: Boolean` — 是否为卡片发射

## Demo 工具清单

`DemoToolFactory`（`agent/DemoToolFactory.kt`）通过 `create()` 方法创建 `AgentToolRegistry`，注册 7 个工具：

### Data 工具

| 工具名 | Kind | 输入参数 | 输出 | 副作用 |
|--------|------|----------|------|--------|
| `search_destination` | Data | `query: String` | candidates 列表（DestinationCandidate） | 更新 destinationCandidates，清空 selectedDestination、latestRouteCard、hotelFilterContext、hotelResultsByPlatform |
| `get_route_options` | Data | `destination_name: String` | 4 种路线模式（drive/transit/walk/bike） | 更新 selectedDestination、latestRouteCard，清空 hotelResultsByPlatform |
| `search_ctrip_hotels` | Data | `destination_name?, max_price?, max_distance_km?` | 携程平台酒店列表 | 更新 hotelFilterContext，追加 hotelResultsByPlatform["携程"] |
| `search_meituan_hotels` | Data | `destination_name?, max_price?, max_distance_km?` | 美团平台酒店列表 | 更新 hotelFilterContext，追加 hotelResultsByPlatform["美团"] |

### Card 工具

| 工具名 | Kind | 输入参数 | 输出 | 数据来源 |
|--------|------|----------|------|----------|
| `emit_option_card` | Card | `card_kind?, title?, description?, options[], allow_custom_input?, custom_input_hint?` 等 | `OptionCardPayload` | 直接传参 或 通过 card_kind 引用 DemoCardPlanner 预制模板（destination_candidates / hotel_price / hotel_distance） |
| `emit_route_card` | Card | 无 | `RouteCardPayload` | 从 `currentState.latestRouteCard` 读取 |
| `emit_hotel_list_card` | Card | `ranking?` (cheapest/nearest/balanced) | `HotelListCardPayload` | 从 `currentState.hotelResultsByPlatform` 整合排序 |

**AgentToolKind 控制执行行为（AgentLoopRunner:206-208）：**
- Data 工具执行前延迟 `dataToolDelayMs`（1500ms）
- Card 工具不延迟

**目的地解析（resolveDestination）：** 优先从 destinationCandidates 匹配，若 destinationName 为空则用 currentState.selectedDestination，都不满足则抛异常要求先调用 search_destination。

### 酒店工具的设计细节

`search_ctrip_hotels` 和 `search_meituan_hotels` 共享同一个 `hotelTool()` 工厂方法，通过参数区分：
- **携程**：basePrice=328，distanceOffset=0.1
- **美团**：basePrice=299，distanceOffset=0.0

每个平台返回 3 个 mock 酒店，按 maxPrice 和 maxDistanceKm 过滤。两个平台的酒店通过 `hotelResultsByPlatform` map 分别存储，最终由 `HotelListCardPayload` 整合跨平台比价。

## 卡片系统

### AgentCardPayload 体系

所有卡片类型详见 [agent.md#agencardpayload-类型体系](agent.md)。卡片通过 `ToolExecutionResult.cardPayload` 从 Card 工具返回到 AgentLoopRunner，后者检测到 cardPayload 后：
1. 通过 `AgentPlaybackEvent.AssistantUpdate(cardPayload = card)` 发送
2. AgentSession 将 cardPayload 附加到当前 Assistant TimelineItem 上
3. UI 的 `TimelineBubble` → `CardContent` 分发到具体卡片渲染组件

### DemoCardPlanner

`DemoCardPlanner`（`agent/DemoCardPlanner.kt`）是卡片工厂，提供预制模板：

| 方法 | 产出卡片 | 用途 |
|------|----------|------|
| `destinationCard(candidates)` | `OptionCardPayload` | 候选目的地选择（含 3 个候选地点选项） |
| `priceCard(destinationName)` | `OptionCardPayload` | 酒店预算选择（¥300/¥400/¥500 三档） |
| `distanceCard(destinationName)` | `OptionCardPayload` | 距离范围选择（1km/2km/3km 三档） |
| `routeCard(destination, routes, mapAction)` | `RouteCardPayload` | 路线展示（4 种出行方式 + 高德地图链接） |
| `hotelCard(state, ranking)` | `HotelListCardPayload?` | 酒店列表（整合多平台、排序、标出无匹配平台） |
| `genericOptionCard(title, desc, options, ...)` | `OptionCardPayload` | 通用 option 卡片 |
| `buildOptionCardByKind(cardKind, state)` | `OptionCardPayload` | 按 card_kind 构建预制模板卡片 |

**兼容回退（maybeBuildAssistantCardFallback）：** 在 `allowCompatibilityCardFallback` 开启时，根据 lastExecutedToolNames 和 responseText 推断卡片。当前 runtimeOptions 中默认为 false，不启用。

### AgentCardValidator

`AgentCardValidator.validate()` 校验卡片有效性：
- **OptionCard**：cardId 非空、title 非空、options 1-6 个、option.id 不重复、每个 option 的 id/title/promptText/displayText 非空、sourceCardId 匹配 cardId、maxPrice/maxDistanceKm > 0
- **RouteCard**：destinationName 非空、exactly 4 routes、每个 route.title 非空、openMapAction.uri 非空
- **HotelListCard**：hotels 非空、platformStatuses 非空、每个 hotel 有 name 和 quotes

校验失败的卡片会被静默丢弃（返回 null），工具返回 displayText 提示失败。

### 卡片和工具的数据关系

```
search_destination → destinationCandidates
  → emit_option_card → OptionCardPayload (destination_candidates template)
    → 用户选择 → SelectionAction(selectedDestinationName)
      → selectedDestination

get_route_options → latestRouteCard (RouteCardPayload)
  → emit_route_card → 读取 latestRouteCard 发射

search_ctrip_hotels / search_meituan_hotels → hotelResultsByPlatform
  → emit_hotel_list_card → HotelListCardPayload (整合排序)
```

## 扩展指南

### 添加新 Data 工具

1. 在 `DemoToolFactory` 中添加一个 private fun，返回 `RegisteredAgentTool(kind = AgentToolKind.Data)`
2. 定义 `AgentToolDefinition`（name, description, parametersSchema 符合 JSON Schema）
3. 实现 `AgentToolExecutor`：解析 arguments → 执行业务逻辑 → 返回 `ToolExecutionResult`（更新 nextState）
4. 在 `create()` 方法的 tools 列表中加入新工具

### 定义新 CardPayload

1. 在 `AgentModels.kt` 中定义新的 data class，实现 `AgentCardPayload`（需要 `override val type: String`）
2. 在 `AgentCardValidator.validate()` 中添加校验逻辑
3. 在 `DemoCardPlanner` 中添加构建方法（如果需要预制模板）

### 添加新 Card 工具

1. 在 `DemoToolFactory` 中添加 private fun，返回 `RegisteredAgentTool(kind = AgentToolKind.Card)`
2. executor 中调用 `DemoCardPlanner` 或自行构建 cardPayload，返回 `ToolExecutionResult(cardPayload = card)`
3. 在 `create()` 方法的 tools 列表中加入新工具

### 在 UI 中添加卡片渲染

1. 在 `MainScreen.kt` 的 `CardContent()` Composable 中，添加新 payload 类型的 when 分支
2. 创建对应的 Composable 函数（参考 `OptionCard`、`RouteCard`、`HotelListCard`）
3. 卡片通过 `onSelectionSubmit`（结构化回调）或 `onOpenLink`（外部链接）与 AgentSession 交互
