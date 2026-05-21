package com.example.openwhale.agent

class AgentToolRegistry(
  private val tools: Map<String, RegisteredAgentTool>,
  private val debugLogger: AgentDebugLogger = NoopAgentDebugLogger,
) {
  fun definitions(): List<AgentToolDefinition> = tools.values.map(RegisteredAgentTool::definition)

  fun isCardTool(name: String): Boolean = tools[name]?.kind == AgentToolKind.Card

  fun prepare(toolCall: AgentToolCall): PreparedToolCall {
    val tool = tools[toolCall.name] ?: throw AgentRuntimeContractException("未注册工具: ${toolCall.name}")
    debugLogger.log(category = "tool", message = "准备执行 ${toolCall.name}，参数=${toolCall.arguments}")
    return PreparedToolCall(toolCall = toolCall, tool = tool)
  }

  suspend fun execute(preparedToolCall: PreparedToolCall, currentState: SessionContextState): ExecutedToolCall {
    val toolCall = preparedToolCall.toolCall
    debugLogger.log(category = "tool", message = "执行 ${toolCall.name}")
    val result = preparedToolCall.tool.executor.execute(toolCall.arguments, currentState)
    return ExecutedToolCall(preparedCall = preparedToolCall, result = result)
  }

  fun finalize(executedToolCall: ExecutedToolCall): FinalizedToolCall {
    val toolCall = executedToolCall.preparedCall.toolCall
    val result = executedToolCall.result
    debugLogger.log(category = "tool", message = "${toolCall.name} 完成，结果摘要=${result.displayText}")
    return FinalizedToolCall(
      preparedCall = executedToolCall.preparedCall,
      result = result,
      toolMessage =
        ProviderConversationMessage(
          role = ProviderMessageRole.Tool,
          content = result.modelPayload,
          toolCallId = toolCall.id,
          toolName = toolCall.name,
        ),
      toolFeedbackItem =
        TimelineItem(
          id = java.util.UUID.randomUUID().toString(),
          role = TimelineItemRole.Tool,
          title = toolCall.name,
          text = result.displayText,
        ),
    )
  }
}
