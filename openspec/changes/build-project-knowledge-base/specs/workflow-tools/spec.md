## ADDED Requirements

### Requirement: Workflow doc SHALL describe workflow packs, tool registry, and card system
工作流文档 SHALL 描述 WorkflowPromptPack 体系、AgentToolRegistry 的注册/分发机制、Tool 执行与 Card 发射流程、Demo 工具的完整列表、以及卡片类型体系。

#### Scenario: Reader understands workflow packs
- **WHEN** 读者查看 `kb/workflow.md`
- **THEN** 能理解：WorkflowPromptPack 的结构（id, title, systemPrompt, starterPrompt）、DefaultWorkflowPromptPackRepository 提供的两个工作流（general_chat, navigation_hotel）、切换工作流时 AgentSession 的行为

#### Scenario: Reader understands tool system
- **WHEN** 读者查看工具系统部分
- **THEN** 能理解：AgentToolDefinition / RegisteredAgentTool 的结构、AgentToolRegistry.register() 的注册方式、AgentToolKind（Data vs Card）的区分、Tool 执行流程（接收 arguments + currentState → 返回 ToolExecutionResult）

#### Scenario: Reader understands card emission
- **WHEN** 读者查看卡片系统部分
- **THEN** 能理解：Card 类 Tool 如何通过 ToolExecutionResult.cardPayload 发射卡片到 UI、OptionCard / RouteCard / HotelListCard 的用途和结构、DemoCardPlanner 的作用

#### Scenario: Reader knows how to extend
- **WHEN** 读者需要添加新工具或卡片
- **THEN** 文档中有清晰的扩展指南：注册新 Tool、定义新 CardPayload、在 UI 中添加对应渲染
