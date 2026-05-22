## ADDED Requirements

### Requirement: Architecture doc SHALL describe project overview and data flow
项目架构文档 SHALL 描述 OpenWhale 的项目定位、技术栈、模块职责划分、以及从用户输入到 UI 更新的完整数据流向。

#### Scenario: Reader understands project purpose
- **WHEN** 读者打开 `kb/architecture.md`
- **THEN** 能在前 3 段内了解：项目是什么（Android Agent Demo）、解决什么问题（生活服务 Agent）、当前阶段（Demo，后续计划接入真实 API）

#### Scenario: Reader understands module map
- **WHEN** 读者查看架构文档
- **THEN** 能清楚知道 `agent/`、`ui/`、`data/`、`theme/` 每个 package 的职责和关键文件

#### Scenario: Reader understands data flow
- **WHEN** 读者查看数据流描述
- **THEN** 能理解：用户在 UI 输入 → ViewModel.sendMessage() → AgentSession.sendUserMessage() → AgentLoopRunner.run() → ModelProvider 调用 DeepSeek API → 流式事件回放 → AgentSessionSnapshot 更新 StateFlow → UI collectAsState 重绘

#### Scenario: Reader can find key files
- **WHEN** 读者需要定位某个模块的代码
- **THEN** 文档中标注了关键类/函数的文件路径，可以直接搜索定位
