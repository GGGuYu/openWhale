## 0. 准备：探索代码全貌

- [ ] 0.1 阅读 `app/src/main/java/com/example/openwhale/` 下所有 .kt 文件的头部（类/函数签名），建立全局印象
- [ ] 0.2 阅读 `openspec/specs/` 下所有 spec 文件（如果存在），了解已有规格说明
- [ ] 0.3 阅读 `openspec/changes/` 下所有 change 的 proposal.md 和 design.md，了解近期改动意图和设计决策
- [ ] 0.4 确认 `kb/` 目录不存在则创建

## 1. 编写 `kb/architecture.md` — 项目总览

- [ ] 1.1 写「项目定位」：OpenWhale 是什么（Android Agent Demo，生活服务场景），当前阶段，后续计划
- [ ] 1.2 写「技术栈」：Kotlin, Jetpack Compose + Material3, OkHttp, kotlinx.serialization, Navigation3
- [ ] 1.3 写「模块地图」：列出每个 package（agent/, ui/main/, data/, theme/）的职责、关键文件、关键类
- [ ] 1.4 写「数据流向」：从 UI 输入 → ViewModel → AgentSession → LoopRunner → ModelProvider → 流式回放 → StateFlow → UI 重绘，用文字描述全链路
- [ ] 1.5 写「关键设计决策」：为什么用 StateFlow 而非 LiveData、为什么 LazyColumn + 手动滚动控制、为什么 Card 通过 ToolExecutionResult 发射
- [ ] 1.6 标注版本信息：commit hash、Demo 阶段说明

## 2. 编写 `kb/agent.md` — Agent 运行时

- [ ] 2.1 阅读 `AgentSession.kt` 完整代码，写「AgentSession 概述」：构造函数参数、持有的资源、生命周期
- [ ] 2.2 写「关键方法」：sendUserMessage、submitSelection、switchWorkflowPack、resetSession、updateApiKey、updateModelId 的行为和副作用
- [ ] 2.3 阅读 `AgentLoopRunner.kt` 完整代码，写「Loop 执行流程」：输入（modelConfig, workflowPromptPack, conversationHistory, currentState）、输出（通过 onPlaybackEvent 回调）、工具调用循环
- [ ] 2.4 写「AgentPlaybackEvent 事件体系」：枚举所有事件类型（ThinkingUpdate, ToolStart, ToolResult, AssistantBoundary 等），说明每种事件如何影响 timeline
- [ ] 2.5 阅读 `AgentModels.kt`，写「核心数据模型」：AgentSessionSnapshot 字段说明、TimelineItem 和 TimelineItemRole、AgentCardPayload 类型体系、SessionContextState 的用途
- [ ] 2.6 阅读 `provider/ModelProvider.kt` 和 `provider/DeepSeekModelProvider.kt`，写「ModelProvider 接口」：契约方法、DeepSeek 实现的请求/响应格式、流式处理

## 3. 编写 `kb/workflow.md` — 工作流与工具系统

- [ ] 3.1 阅读 `WorkflowPromptPacks.kt`，写「工作流系统」：WorkflowPromptPack 结构、DefaultWorkflowPromptPackRepository、当前两个工作流及其 systemPrompt/starterPrompt 设计意图
- [ ] 3.2 阅读 `AgentToolRegistry.kt`，写「工具注册表」：RegisteredAgentTool 结构、AgentToolDefinition、AgentToolKind（Data vs Card）、register() 和工作流绑定的机制
- [ ] 3.3 阅读 `DemoToolFactory.kt`，写「Demo 工具清单」：列出所有工具（search_destination, get_route_options, search_hotels 等），每个工具的输入参数和输出格式
- [ ] 3.4 阅读 `DemoCardPlanner.kt` 和卡片相关 Payload 类，写「卡片系统」：OptionCard、RouteCard（含 RouteModePanel）、HotelListCard 的结构和渲染时机
- [ ] 3.5 写「扩展指南」：如何添加新 Tool（注册 + 实现 executor）、如何定义新 CardPayload、如何在 UI 中添加对应渲染

## 4. 编写 `kb/interface.md` — UI 层

- [ ] 4.1 阅读 `MainActivity.kt`、`Navigation.kt`，写「入口链路」：MainActivity 的 edge-to-edge / orientation lock / setContent → MainNavigation → MainScreen
- [ ] 4.2 阅读 `MainScreen.kt` 的 MainScreen 函数（两个重载），写「布局结构」：Column(ChatTopBar + Box(LazyColumn + ComposerBar))，标注各组件位置
- [ ] 4.3 阅读 `MainScreenViewModel.kt`，写「状态管理」：ViewModel 包装 AgentSession，combine snapshot + debugEvents 生成 uiState，UI 通过 collectAsStateWithLifecycle 订阅
- [ ] 4.4 绘制「组件树」（文字层级）：MainScreen → ChatTopBar / HistorySheet / SettingsSheet / LazyColumn(TimelineBubble...) / ComposerBar
- [ ] 4.5 写「Timeline 渲染分发」：TimelineBubble 根据 TimelineItemRole 分发 → User/Assistant 聊天气泡、Thinking → ThinkingTraceCard、Tool → ToolExecutionCard、Status → StatusEventCard
- [ ] 4.6 写「卡片渲染」：OptionCard、RouteCard、HotelListCard 的渲染组件和交互（选择、链接跳转）
- [ ] 4.7 阅读 `theme/` 下的 Color.kt、Theme.kt、Type.kt，写「主题系统」：品牌色（WhaleAccent, WhaleInk 等）、Material3 主题配置

## 5. 校对与收尾

- [ ] 5.1 交叉校对：重读每个 KB 文件，对照源码确认类名、方法名、文件路径准确无误
- [ ] 5.2 检查 KB 文件之间的一致性：同一个概念在不同文件中的描述不矛盾
- [ ] 5.3 确认每个 KB 文件顶部有简短的一句话概述（方便 agent 快速判断是否需要细读）
- [ ] 5.4 确认 commit hash 已标注在 architecture.md 版本信息中
