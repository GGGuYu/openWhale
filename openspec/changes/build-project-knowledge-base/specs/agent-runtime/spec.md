## ADDED Requirements

### Requirement: Agent runtime doc SHALL describe session lifecycle and loop execution
Agent 运行时文档 SHALL 描述 AgentSession 的生命周期管理、AgentLoopRunner 的执行流程、核心数据模型、以及 ModelProvider 接口。

#### Scenario: Reader understands AgentSession lifecycle
- **WHEN** 读者查看 `kb/agent.md`
- **THEN** 能理解：AgentSession 的创建（构造函数参数）、snapshot StateFlow 的更新时机、switchWorkflowPack / resetSession / sendUserMessage / updateApiKey 等关键方法的副作用

#### Scenario: Reader understands loop execution
- **WHEN** 读者查看 loop 流程描述
- **THEN** 能理解 AgentLoopRunner.run() 的输入输出、conversationHistory 的作用、AgentPlaybackEvent 事件类型和回放机制、timeline 如何随事件增量更新

#### Scenario: Reader understands key data models
- **WHEN** 读者查看数据模型部分
- **THEN** 能理解 AgentSessionSnapshot（UI 状态聚合）、TimelineItem（时间线条目及其 role 枚举）、AgentCardPayload 子类型体系、SessionContextState 的用途

#### Scenario: Reader understands ModelProvider
- **WHEN** 读者查看 Provider 部分
- **THEN** 能理解 ModelProvider 接口的契约、DeepSeekModelProvider 的实现方式、流式响应的数据格式
