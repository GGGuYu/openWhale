## ADDED Requirements

### Requirement: UI doc SHALL describe screen hierarchy, state management, and component tree
UI 层文档 SHALL 描述从 MainActivity 到各个 Composable 组件的完整层级、MainScreenViewModel 的状态管理、布局结构、以及 TimelineBubble 的渲染分发逻辑。

#### Scenario: Reader understands entry and navigation
- **WHEN** 读者查看 `kb/interface.md`
- **THEN** 能理解：MainActivity（edge-to-edge、orientation lock）→ MainNavigation → MainScreen 的入口链路

#### Scenario: Reader understands layout structure
- **WHEN** 读者查看布局描述
- **THEN** 能理解 MainScreen 的 Column 布局：ChatTopBar（固定顶栏）+ Box(LazyColumn + ComposerBar)，以及各组件的位置关系

#### Scenario: Reader understands state management
- **WHEN** 读者查看状态管理部分
- **THEN** 能理解：MainScreenViewModel 包装 AgentSession，通过 combine(snapshot, debugEvents) 生成 uiState StateFlow，UI 通过 collectAsStateWithLifecycle 订阅

#### Scenario: Reader understands component tree
- **WHEN** 读者查看组件树
- **THEN** 能理解：ChatTopBar、HistorySheet、SettingsSheet、ComposerBar、TimelineBubble 的职责和层级关系

#### Scenario: Reader understands timeline rendering
- **WHEN** 读者查看渲染分发部分
- **THEN** 能理解 TimelineBubble 根据 TimelineItemRole 分发到不同渲染组件（User/Assistant → 聊天气泡，Thinking → ThinkingTraceCard，Tool → ToolExecutionCard，Status → StatusEventCard），以及 CardPayload 子类型的渲染（OptionCard, RouteCard, HotelListCard）
