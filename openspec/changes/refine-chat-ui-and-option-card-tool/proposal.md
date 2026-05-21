## Why

首个移动端 Agent Demo 已经证明了导航到酒店的核心流程可用，但当前界面仍然偏调试态，和面向演示的聊天产品体验有明显差距。同时，`option` 卡片工具的参数能力过于收敛，限制了 agent 直接组织通用选项卡文案与结构的能力。现在需要启动第二版迭代，先把主聊天界面收敛到更简洁稳定的产品形态，并同步放开通用选项卡的参数控制边界。

## What Changes

- 重构聊天主界面，使首页视觉与交互更接近简洁聊天产品，而不是调试面板堆叠页面。
- 调整输入区与软键盘联动，修复输入法弹出时底部出现过大空白的问题。
- 缩小工具执行反馈的视觉权重，让其明显弱于正常消息与内容卡片。
- 为“你 / 助手”消息增加 emoji 头像，增强对话感与时间线可读性。
- 收敛顶部区域信息，只保留 App 名称、模型信息、聊天主体、工作流配置等核心内容；将 API Key 设置移入设置入口，并增加最近对话历史弹窗入口。
- 将 `emit_option_card` 重构为更通用的卡片工具：允许 agent 直接填写标题、说明、选项文案和回调文本等主要参数，工具说明中提供示例，App 侧继续负责 schema 校验与渲染。
- 先不扩展 route / hotel_list 等其他卡片工具参数，保持当前行为稳定。

## Capabilities

### New Capabilities

- `chat-surface-shell`: 规范移动端聊天首页壳层，包括顶部标题、设置入口、历史入口、输入区、消息头像与简洁布局。

### Modified Capabilities

- `interactive-card-demo`: 将 option 卡从场景专用卡升级为 agent 可填写主要文案参数的通用卡片，同时更新卡片展示层级与工具反馈样式要求。
- `mobile-agent-runtime`: 调整运行时与界面配置入口的交互边界，支持更轻量的首页壳层、设置入口与最近对话历史入口，同时保持工作流配置与会话状态稳定可用。

## Impact

- Affected code: `app/src/main/java/com/example/openwhale/ui/main/` 下的 Compose 聊天页面、顶部壳层、输入区、设置与历史入口；`app/src/main/java/com/example/openwhale/agent/` 下的 option 卡工具定义、schema 校验、会话状态与工具调用路径。
- Affected specs: `openspec/specs/interactive-card-demo/spec.md`、`openspec/specs/mobile-agent-runtime/spec.md`，以及新增 `chat-surface-shell` capability。
- Product impact: 第二版 Demo 会从“可调试的技术验证版本”提升到“更适合对外演示的产品化聊天界面”，并为后续继续放宽其他卡片工具参数打基础。
