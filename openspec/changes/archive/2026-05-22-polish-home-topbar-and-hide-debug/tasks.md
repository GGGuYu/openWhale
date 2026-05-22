## 1. 固定顶栏

- [x] 1.1 新增 `ChatTopBar` 组件：左侧 hamburger 图标，中间 "OpenWhale" 标题 + API Key 状态行（🟢模型名 / 🔴未填写），右侧新对话图标按钮
- [x] 1.2 将 Box 叠加布局改为 Column(顶栏 + 聊天区 + 底栏)，顶栏固定在顶部不随 LazyColumn 滚动
- [x] 1.3 新对话按钮点击后重置会话状态

## 2. 移除调试元素

- [x] 2.1 删除 `ChatTopShell` 组件（包含 StatusPill、CompactWorkflowSelector、SessionContextSummary）
- [x] 2.2 删除 `CompactWorkflowSelector` 组件
- [x] 2.3 删除 `SessionContextSummary` 组件

## 3. 历史面板改造

- [x] 3.1 `HistorySheet` 改造为全屏侧滑面板，增加设置入口按钮和工作流选择器
- [x] 3.2 历史面板打开方式改为由顶栏 hamburger 图标触发

## 4. 验证

- [x] 4.1 在手机上验证顶栏固定不随聊天滚动
- [x] 4.2 验证 API Key 就绪/未就绪状态显示正确
- [x] 4.3 验证历史面板中设置和工作流入口正常
- [x] 4.4 验证新对话按钮重置功能正常
