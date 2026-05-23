## Context

OpenWhale 是一个 Android 端 Agent Demo App，使用 Kotlin + Jetpack Compose + Material3。当前代码集中在一个单模块 Android 工程中，核心逻辑在 `app/src/main/java/com/example/openwhale/` 下，按 package 分为：

- `agent/` — Agent 运行时（Session、Loop、Tools、Models、Provider）
- `ui/main/` — UI 层（MainScreen、ViewModel）
- `data/` — DI 容器、本地存储
- `theme/` — 品牌主题

目前没有架构文档。后续计划大规模重构（真实 API、通用 tools/cards、PI 级 loop），需要知识库支撑。

## Goals / Non-Goals

**Goals:**
- 产出 4 个 KB 文件，覆盖架构、Agent 运行时、工作流/工具、UI 层
- 每个文件聚焦「整体逻辑 + 关键概念 + 模块分工 + 核心类/函数」，不罗列代码细节
- 内容必须基于实际代码和现有 spec 文件校对，不能凭空编造
- 让后续 agent 读完后能理解：项目是什么、怎么跑起来的、各模块怎么协作

**Non-Goals:**
- 不写 README（后续单独 change）
- 不修改任何代码
- 不写 API 文档级别的细节（参数列表等）
- 不画 UML 图（文字描述架构即可）

## Decisions

### 1. KB 拆分为 4 个文件

| 文件 | 目的 | 核心内容 |
|------|------|----------|
| `kb/architecture.md` | 全局地图 | 项目定位、技术栈、package 职责、数据流向（UI → VM → Session → Loop → Provider → 回放 → UI） |
| `kb/agent.md` | Agent 核心 | AgentSession（生命周期、状态管理）、AgentLoopRunner（loop 流程）、AgentModels（关键数据类）、ModelProvider 接口 |
| `kb/workflow.md` | 能力系统 | WorkflowPromptPack、AgentToolRegistry（注册/分发）、AgentToolKind（Data vs Card）、DemoToolFactory、卡片类型体系、如何扩展 |
| `kb/interface.md` | UI 层 | 入口链路（MainActivity → Navigation → MainScreen）、布局结构、ViewModel、组件树、TimelineBubble 分发逻辑 |

### 2. 内容原则

- **写 "为什么" 而不是 "是什么"**：解释设计意图，而不只是罗列类名
- **写流程而非枚举**：描述 "用户发消息后发生了什么" 比列出所有类更有价值
- **引用代码位置**：标注关键类/函数的文件路径和大致行号，方便定位
- **基于代码校对**：必须阅读源代码和现有 spec 文件，确保内容准确

### 3. 校对来源

Agent 在写 KB 时必须交叉参考：
- 源代码：`app/src/main/java/com/example/openwhale/` 下的所有 .kt 文件
- 现有 spec：`openspec/specs/` 下的 spec 文件（如果存在）
- 现有 change：`openspec/changes/` 下的 design.md / proposal.md（了解近期改动意图）

## Risks / Trade-offs

- [风险] KB 内容和代码不同步 → [缓解] 标注 "版本：Demo 阶段，commit: <hash>"，后续重构时更新
- [风险] 写得太细变成代码复述 → [缓解] Task 中明确要求 "聚焦逻辑和架构，不罗列参数"
