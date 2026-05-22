## Why

当前首页顶部区域（ChatTopShell）作为 LazyColumn 的一个 item 随聊天内容一起滚动，且展示了大量调试导向的信息（状态 Pills、Workflow 选择器、SessionContext 调试摘要），需要对齐 DeepSeek 官方 app 的简洁体验：固定顶栏不随内容滚动，调试元素全部隐藏到二级界面。

## What Changes

- **固定顶栏**：新增不随 LazyColumn 滚动的 TopAppBar，左 hamburger 图标打开历史面板，中标题 "OpenWhale" + API Key 状态行，右 "新对话" 按钮
- **ChatTopShell 移除**：废除当前随聊天滚动的 ChatTopShell，其中的调试元素（StatusPill、CompactWorkflowSelector、SessionContextSummary）全部移出主界面
- **历史面板改造**：HistorySheet 从纯历史列表扩展为侧滑面板，增加设置入口和工作流选择器，状态信息藏到历史面板底部
- API Key 状态由顶栏副标题行承载：已就绪显示 🟢 模型名，未就绪显示 🔴 模型 API Key 未填写

## Capabilities

### Modified Capabilities
- `chat-surface-shell`: 收紧聊天外壳布局规范，要求顶栏固定不随聊天内容滚动，调试元素默认隐藏

## Impact

- MainScreen.kt：ChatTopShell 替换为固定 TopAppBar，Layout 需改为 Column(顶栏 + 聊天列表 + 底栏)
- HistorySheet → 改造为侧滑面板，添加设置/工作流入口
- ChatTopShell、CompactWorkflowSelector、SessionContextSummary 相关代码移除或迁移
- iosMain 对应文件如果存在也需要同步（如无则跳过）
