## ADDED Requirements

### Requirement: Chat shell SHALL display a fixed top bar
聊天界面 SHALL 在顶部显示固定不随列表滚动的 TopAppBar，左侧 hamburger 按钮打开历史面板，中间显示应用标题和模型/ApiKey 状态，右侧新对话按钮。

#### Scenario: Top bar is fixed
- **WHEN** 用户滚动聊天列表
- **THEN** 顶栏保持在视口顶部不动，不会随内容滚动而消失

#### Scenario: API key is configured
- **WHEN** API Key 已就绪
- **THEN** 顶栏副标题显示绿色圆点 + 当前模型名称

#### Scenario: API key is not configured
- **WHEN** API Key 未填写
- **THEN** 顶栏副标题显示红色圆点 + "模型 API Key 未填写"

#### Scenario: Hamburger opens history panel
- **WHEN** 用户点击顶栏左侧 hamburger 图标
- **THEN** 弹出侧滑面板，包含历史对话列表、设置按钮、工作流选择器

#### Scenario: New conversation button
- **WHEN** 用户点击顶栏右侧新对话按钮
- **THEN** 当前会话被重置为初始状态

### Requirement: Debug elements SHALL be hidden from main chat view
工作流选择器、会话状态 Pills、SessionContext 调试摘要 SHALL 默认不在主聊天界面展示。

#### Scenario: Debug elements hidden
- **WHEN** 用户查看聊天主界面
- **THEN** 不显示 StatusPill、CompactWorkflowSelector、SessionContextSummary 等调试元素
- **THEN** 工作流选择可在历史面板中访问
