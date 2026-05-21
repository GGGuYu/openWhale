## 1. Chat Shell UI Restructure

- [x] 1.1 重构聊天首页顶部壳层，只保留 OpenWhale 标题、模型信息、设置入口、历史入口与紧凑工作流配置。
- [x] 1.2 将 API Key 配置从首页移入设置弹窗或设置面板，并保留当前本地覆写能力。
- [x] 1.3 增加最近对话历史入口与弹窗容器，先支持展示最近会话列表或占位数据。
- [x] 1.4 为“你 / 助手”消息增加 emoji 头像，并调整消息排版使对话流更接近简洁聊天产品。

## 2. Input and Timeline Presentation Polish

- [x] 2.1 修复输入法弹出时底部输入区与 IME 之间的过大空白，统一 Scaffold 与输入区 inset 策略。
- [x] 2.2 降低工具执行反馈的字体、间距和容器权重，使其弱于普通内容卡与业务卡。
- [x] 2.3 收敛首页默认调试信息展示，将调试内容下沉到次级入口、折叠区或非主路径区域。
- [x] 2.4 基于真机复现继续排查 IME 空白问题：当前在真机上键盘弹出后，发送按钮下方仍保留大块空白灰底区域。需要结合截图、布局树与 WindowInsets 行为复核是否存在 LazyColumn 底部预留、overlay 输入区测量高度、navigationBars/IME inset 叠加或厂商输入法窗口策略带来的二次占位，并在真机上完成回归确认。

## 3. UI Aesthetic Refinement

- [x] 3.1 在完成前述 UI 结构调整后、开始 tools 参数重构前，基于设计相关 skills 对聊天首页、消息流、卡片层级和视觉细节做一轮 UI 审美重构。

## 4. Generic Option Card Tool Refactor

- [x] 4.1 重构 `emit_option_card` 的工具 schema，让 agent 可以直接填写标题、描述、选项列表、callback prompt text 等主要参数。
- [x] 4.2 为通用 option 卡定义稳定的 App-owned payload 校验规则，并兼容当前 selection action 所需的结构化字段。
- [x] 4.3 更新 workflow prompt pack 与工具描述，为通用 option card 提供明确示例并引导模型正确使用。

## 5. Validation and Demo Regression

- [x] 5.1 更新或补充单测，覆盖新聊天壳层状态、新 option card payload 校验和通用卡片回调路径。
- [x] 5.2 完成构建、必要测试与真机回归，重点验证键盘交互、简洁首页、设置入口、历史入口和整条 demo 流程。
