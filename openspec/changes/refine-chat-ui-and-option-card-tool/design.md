## Context

当前 OpenWhale 已经具备可工作的移动端 Agent Demo，但 UI 仍明显服务于开发调试：顶部展示了大量状态和调试信息，API Key 入口直接暴露在首页，工具执行反馈与主内容卡视觉权重接近，消息缺乏头像层，聊天页整体更像“运行时检查面板”而不是演示型聊天产品。

这次改动同时跨越两个层面：

1. **聊天壳层 UI 重构**：需要重排首页结构、顶部栏、输入区、设置与历史入口、消息气泡和工具反馈卡。
2. **option 卡工具参数重构**：需要把当前由 `card_kind` 驱动的场景化 option 卡，调整为 agent 可直接填写主要文案与选项的通用卡，同时保持 App 对 schema 的最终控制与校验。

约束也比较明确：

- 第二版仍然是 demo，界面要显著更简洁，但不能牺牲当前 workflow pack、session state、显式卡片工具路径的稳定性。
- 这轮只放开 `emit_option_card`，暂不重构 route / hotel_list 等其他卡片工具参数，避免同时扩大协议面。
- 用户明确希望首页更接近 DeepSeek 一类简洁聊天 UI，因此调试能力要弱化为次级入口，而不是持续占据首页空间。

## Goals / Non-Goals

**Goals:**
- 将首页重构为简洁聊天壳层，顶部仅保留 App 名称 OpenWhale、模型信息、必要入口与工作流配置。
- 修复输入法弹出时输入区与键盘之间的大块空白，保证底部输入区紧贴软键盘。
- 让工具执行反馈在视觉上更轻，显著弱于普通聊天内容与业务卡片。
- 为“你 / 助手”消息提供 emoji 头像，提升消息流的角色感与可读性。
- 将 API Key 配置移动到设置入口中，并增加最近对话历史弹窗入口。
- 将 `emit_option_card` 升级为通用工具：允许 agent 直接填写标题、描述、选项列表和回调文本等主要参数。
- 保持现有 route / hotel_list card 渲染与 runtime loop 的主流程稳定。

**Non-Goals:**
- 这轮不引入完整的历史会话持久化系统，只需要一个可接入最近对话历史的 UI 入口或弹窗容器。
- 这轮不重构 route card、hotel list card 的参数协议。
- 这轮不追求完整设置中心信息架构，只需要承载 API Key 配置和未来扩展入口。
- 这轮不改变核心 provider、workflow prompt pack、card callback 的总体运行机制。

## Decisions

### 1. 首页采用“简洁聊天壳层 + 次级入口”的结构
首页将重构为更轻的聊天布局：
- 顶部为简单 app bar，显示 `OpenWhale`、当前模型、设置按钮、历史按钮。
- 工作流配置保留，但从大块面板收敛成紧凑选择区或次级模块。
- 原有大段调试状态、API Key 编辑表单不再常驻首页。

这样可以让用户第一眼看到的是“聊天产品”，而不是“可配置调试器”。

**Alternatives considered:**
- 继续保留当前大 Header，仅靠收起部分区域解决。Rejected，因为整体信息密度仍旧太高，无法根本贴近简洁聊天 UI。
- 完全隐藏模型和工作流信息。Rejected，因为 demo 场景仍然需要保留少量可见配置能力。

### 2. 设置与历史采用弹窗或面板入口，而不是首页常驻大区块
API Key 配置移入设置入口，最近对话历史通过历史按钮进入弹窗或底部面板。首页不再直接展示大块配置内容。

这样既能保留 demo 需要的操作入口，又不会挤占聊天主体空间。

**Alternatives considered:**
- 放到二级页面。Rejected，因为 demo 中切页成本更高，不如弹窗/面板快。
- 保持首页内嵌配置表单。Rejected，因为与“简洁聊天首页”的目标冲突。

### 3. 工具反馈降级为弱提示卡，而不是等价内容卡
工具执行反馈会保留，但改成更轻的字号、间距、背景层和标签表达，让它更像系统提示或操作 trace，而不是用户真正关注的主内容。

**Alternatives considered:**
- 完全隐藏工具反馈。Rejected，因为 demo 仍需要一定可见性来帮助观察工具链路。
- 保持当前样式不变。Rejected，因为它和正常卡片几乎同权重，会破坏聊天阅读节奏。

### 4. 输入区改为真正跟随 IME 的底部壳层
需要检查 Compose Scaffold / bottomBar 与 window insets 的叠加方式，避免 `contentPadding + innerPadding + imePadding/navigationBarsPadding` 叠加后产生额外空白。底部输入区应由单一、明确的 inset 策略控制。

**Alternatives considered:**
- 仅通过减小 bottom padding 微调。Rejected，因为问题本质通常是 inset 叠加，不是单纯数值过大。

### 5. `emit_option_card` 改为通用参数化工具，但保留 App-owned schema
当前 `emit_option_card` 只接受 `card_kind`，过于依赖 App 预制模板。第二版改为：
- agent 直接填写 `title`、`description`、`options[]`、`allow_custom_input`、`custom_input_hint` 等主要参数；
- 每个选项至少允许填写展示文案、补充文案、回传 prompt text，以及必要的结构化 selection hint；
- App 仍负责严格校验字段完整性、限制只渲染允许的 OptionCard schema。

这使 option 卡成为真正的“通用卡片工具”，同时不把渲染权完全交给模型。

**Alternatives considered:**
- 继续沿用 `card_kind` 方案，只多加几个枚举。Rejected，因为仍然是场景卡，不足以支撑通用 option card 的目标。
- 直接允许模型输出任意 JSON/DSL 卡片。Rejected，因为协议面太大，容易破坏当前 demo 稳定性。

### 6. Route / Hotel 等其他卡片工具暂不改参数协议
为了把这轮控制在“UI 明显升级 + option 卡通用化”这个范围内，其他卡片工具先保持现状，避免同一轮同时改多个 card contract。

**Alternatives considered:**
- 一次性重构所有卡片工具参数。Rejected，因为测试面和 prompt 适配成本都会明显扩大。

## Risks / Trade-offs

- **[Risk]** 首页大幅简化后，开发调试效率可能下降。 → **Mitigation:** 保留调试能力，但下沉到次级入口或折叠区域，而不是完全移除。
- **[Risk]** IME 空白问题可能来自多处 inset 叠加，调整时容易影响不同机型。 → **Mitigation:** 统一底部输入区 inset 策略，并在真机上复测。
- **[Risk]** Option 卡放开参数后，模型可能构造出不完整或不一致的选项。 → **Mitigation:** 在 App 侧对 options 数量、文案、promptText 和 selection 字段做强校验，并在 tool description 提供明确示例。
- **[Risk]** 首页新增设置与历史入口后，状态管理复杂度会增加。 → **Mitigation:** 将其作为壳层 UI 状态处理，不与 agent loop 核心状态耦合。
- **[Risk]** UI 更接近聊天产品后，工作流配置可见性下降。 → **Mitigation:** 保留紧凑工作流配置区，确保仍可快速切换 demo workflow。

## Migration Plan

1. 先重构聊天壳层与底部输入区，使 UI 结构先稳定。
2. 再调整工具反馈、头像、顶部栏、设置/历史入口。
3. 随后重构 `emit_option_card` schema 与 tool description，并同步更新 prompt pack 与测试。
4. 完成后进行单测、构建和真机回归，重点验证键盘交互与整条 demo 流程。

回滚策略也比较直接：
- 若 option 卡通用化不稳定，可临时回退到旧的 `card_kind` 模式；
- 若新壳层影响 demo，可先保留旧布局分支并逐步切换。

## Open Questions

- 最近对话历史这轮是先做静态示例入口，还是最小可用的会话快照列表？
- 工作流配置最终是放在首页顶部次级区域，还是合并进设置抽屉中？
- `emit_option_card` 的通用参数是否需要支持更明确的结构化 selection 字段，如 `selectedDestinationName` / `maxPrice` / `maxDistanceKm` 的可选 typed fields？
