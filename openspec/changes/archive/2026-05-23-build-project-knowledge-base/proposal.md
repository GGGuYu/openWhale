## Why

OpenWhale 当前处于 Demo 阶段，代码已经有一定规模（AgentSession、AgentLoopRunner、ToolRegistry、Card 体系、UI 组件树），但没有任何架构文档或知识库。后续计划包括：接入真实后端 API、扩展通用 tools/cards、Agent Loop 和 Agent Runtime 向 PI 项目看齐。为了让后续的 agent 和开发者能快速理解项目架构、模块分工、核心流程，需要建立第一版知识库（KB）。

## What Changes

- 新增 `kb/` 目录，包含 4 个 Markdown 文件：
  - `kb/architecture.md` — 项目总览：是什么、技术栈、模块地图、数据流向
  - `kb/agent.md` — Agent 运行时：AgentSession 生命周期、AgentLoopRunner 执行流程、核心数据模型、ModelProvider 接口
  - `kb/workflow.md` — 工作流与工具系统：WorkflowPromptPack、AgentToolRegistry、Tool 执行与 Card 发射、Demo 工具和卡片体系
  - `kb/interface.md` — UI 层：MainActivity/Navigation 入口、MainScreen 布局结构、ViewModel 状态管理、组件树和渲染分发

## Capabilities

### New Capabilities
- `project-knowledge-base`: 项目知识库，为后续 agent 和开发者提供架构、模块、流程的文档化参考

## Impact

- 新增 `kb/` 目录及 4 个 .md 文件（纯文档，不影响代码）
- 后续所有重构、扩展的 change 都可以引用 KB 文件作为上下文
