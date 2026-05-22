package com.example.openwhale.agent

import com.example.openwhale.agent.provider.ModelProvider
import java.util.UUID

class AgentLoopRunner(
  private val modelProvider: ModelProvider,
  private val toolRegistry: AgentToolRegistry,
  private val debugLogger: AgentDebugLogger = NoopAgentDebugLogger,
  private val runtimeOptions: AgentRuntimeOptions = AgentRuntimeOptions(),
  private val maxIterations: Int = 6,
) {
  suspend fun run(
    modelConfig: ModelConfig,
    workflowPromptPack: WorkflowPromptPack,
    conversationHistory: List<ProviderConversationMessage>,
    currentState: SessionContextState,
    onPlaybackEvent: suspend (AgentPlaybackEvent) -> Unit = {},
  ): AgentLoopResult {
    var sessionState = currentState
    val workingConversationHistory = conversationHistory.toMutableList()
    val generatedTimelineItems = mutableListOf<TimelineItem>()
    val turnId = UUID.randomUUID().toString()
    val visibleThinkingText = StringBuilder()

    repeat(maxIterations) {
      debugLogger.log(category = "loop", message = "开始第 ${it + 1} 轮，当前已选目的地=${sessionState.selectedDestination?.name ?: "无"}")
      val systemPrompt = workflowPromptPack.systemPrompt + "\n\n当前会话状态：\n" + sessionState.toPromptState()
      val streamedAssistantText = StringBuilder()
      val response =
        modelProvider.complete(
          ProviderRequest(
            systemPrompt = systemPrompt,
            modelConfig = modelConfig,
            messages = workingConversationHistory.toList(),
            tools = toolRegistry.definitions(),
          ),
          onStreamEvent = { streamEvent ->
            when (streamEvent) {
              is ProviderStreamEvent.TextDelta -> {
                if (streamEvent.delta.isNotEmpty()) {
                  streamedAssistantText.append(streamEvent.delta)
                  onPlaybackEvent(
                    AgentPlaybackEvent.AssistantUpdate(
                      turnId = turnId,
                      text = streamedAssistantText.toString(),
                      done = false,
                    ),
                  )
                }
              }

              is ProviderStreamEvent.ThinkingDelta -> {
                if (streamEvent.delta.isNotEmpty()) {
                  visibleThinkingText.append(streamEvent.delta)
                  onPlaybackEvent(
                    AgentPlaybackEvent.ThinkingUpdate(
                      turnId = turnId,
                      text = visibleThinkingText.toString(),
                      done = false,
                    ),
                  )
                }
              }
            }
          },
        )

      syncVisibleBuffer(target = visibleThinkingText, fullText = response.reasoningContent)
      syncVisibleBuffer(target = streamedAssistantText, fullText = response.text)

      debugLogger.log(
        category = "loop",
        message = "模型返回 finishReason=${response.finishReason ?: "unknown"}，文本=${response.text?.take(80) ?: "<empty>"}，toolCalls=${response.toolCalls.size}",
      )

      val assistantMessage = response.toConversationMessage()
      if (response.toolCalls.isEmpty()) {
        assistantMessage?.let(workingConversationHistory::add)
        val assistantCard = compatibilityFallbackCard(workflowPromptPack, response.text, sessionState, emptyList())
        assistantCard?.let { card ->
          if (streamedAssistantText.isEmpty()) {
            streamedAssistantText.append(DemoCardPlanner.fallbackText(card))
          }
        }
        if (visibleThinkingText.isNotEmpty()) {
          onPlaybackEvent(
            AgentPlaybackEvent.ThinkingUpdate(
              turnId = turnId,
              text = visibleThinkingText.toString(),
              done = true,
            ),
          )
        }
        onPlaybackEvent(
          AgentPlaybackEvent.AssistantUpdate(
            turnId = turnId,
            text = streamedAssistantText.toString(),
            cardPayload = assistantCard,
            done = true,
          ),
        )
        debugLogger.log(category = "loop", message = "本轮无工具调用，结束 agent loop")
        return AgentLoopResult(updatedState = sessionState, updatedConversationHistory = workingConversationHistory.toList(), timelineItems = generatedTimelineItems)
      }

      if (streamedAssistantText.isNotBlank()) {
        onPlaybackEvent(
          AgentPlaybackEvent.AssistantUpdate(
            turnId = turnId,
            text = streamedAssistantText.toString(),
            done = true,
          ),
        )
      }
      onPlaybackEvent(AgentPlaybackEvent.AssistantBoundary(turnId = turnId))

      val preparedToolCalls = prepareToolBatch(response.toolCalls)
      val batchResult =
        executeToolBatch(
          preparedToolCalls = preparedToolCalls,
          initialState = sessionState,
          turnId = turnId,
          onPlaybackEvent = onPlaybackEvent,
        )
      sessionState = batchResult.updatedState

      assistantMessage?.let(workingConversationHistory::add)
      batchResult.finalizedToolCalls.forEach { finalizedToolCall ->
        workingConversationHistory += finalizedToolCall.toolMessage
      }

      val assistantCard =
        batchResult.finalizedToolCalls
          .mapNotNull { it.result.cardPayload }
          .singleOrNull()
          ?: compatibilityFallbackCard(
            workflowPromptPack = workflowPromptPack,
            responseText = response.text,
            state = sessionState,
            executedToolNames = batchResult.finalizedToolCalls.map { it.preparedCall.toolCall.name },
          )

      if (batchResult.finalizedToolCalls.any { it.isCardEmission && it.result.cardPayload != null }) {
        assistantCard?.let { card ->
          onPlaybackEvent(
            AgentPlaybackEvent.AssistantUpdate(
              turnId = turnId,
              text = if (streamedAssistantText.isBlank()) DemoCardPlanner.fallbackText(card) else "",
              cardPayload = card,
              done = true,
            ),
          )
        }
        if (visibleThinkingText.isNotEmpty()) {
          onPlaybackEvent(
            AgentPlaybackEvent.ThinkingUpdate(
              turnId = turnId,
              text = visibleThinkingText.toString(),
              done = true,
            ),
          )
        }
        debugLogger.log(category = "loop", message = "本轮已显式发出卡片，结束当前 loop，等待下一次用户输入")
        return AgentLoopResult(updatedState = sessionState, updatedConversationHistory = workingConversationHistory.toList(), timelineItems = generatedTimelineItems)
      }
    }

    debugLogger.log(category = "loop", message = "达到最大轮次 $maxIterations，停止继续调用", level = DebugEventLevel.Warning)
    if (visibleThinkingText.isNotEmpty()) {
      onPlaybackEvent(
        AgentPlaybackEvent.ThinkingUpdate(
          turnId = turnId,
          text = visibleThinkingText.toString(),
          done = true,
        ),
      )
    }
    generatedTimelineItems += TimelineItem(id = UUID.randomUUID().toString(), role = TimelineItemRole.Status, title = "状态", text = "Agent loop 达到最大轮次，已停止继续调用。")
    return AgentLoopResult(updatedState = sessionState, updatedConversationHistory = workingConversationHistory.toList(), timelineItems = generatedTimelineItems)
  }

  private fun prepareToolBatch(toolCalls: List<AgentToolCall>): List<PreparedToolCall> {
    val preparedToolCalls = toolCalls.map(toolRegistry::prepare)
    validateToolBatch(preparedToolCalls)
    return preparedToolCalls
  }

  private suspend fun executeToolBatch(
    preparedToolCalls: List<PreparedToolCall>,
    initialState: SessionContextState,
    turnId: String,
    onPlaybackEvent: suspend (AgentPlaybackEvent) -> Unit,
  ): ToolBatchExecutionResult {
    var nextState = initialState
    val finalizedToolCalls = mutableListOf<FinalizedToolCall>()
    preparedToolCalls.forEach { preparedToolCall ->
      val toolItemId = java.util.UUID.randomUUID().toString()
      debugLogger.log(category = "loop", message = "进入工具阶段：${preparedToolCall.toolCall.name}")
      onPlaybackEvent(
        AgentPlaybackEvent.ToolStart(
          turnId = turnId,
          toolItemId = toolItemId,
          toolName = preparedToolCall.toolCall.name,
        ),
      )
      if (preparedToolCall.tool.kind == AgentToolKind.Data && runtimeOptions.dataToolDelayMs > 0) {
        kotlinx.coroutines.delay(runtimeOptions.dataToolDelayMs)
      }
      val executedToolCall = toolRegistry.execute(preparedToolCall = preparedToolCall, currentState = nextState)
      nextState = executedToolCall.result.nextState
      val finalizedToolCall = toolRegistry.finalize(executedToolCall, toolItemId = toolItemId)
      finalizedToolCalls += finalizedToolCall
      onPlaybackEvent(
        AgentPlaybackEvent.ToolFeedback(
          turnId = turnId,
          item = finalizedToolCall.toolFeedbackItem.copy(turnId = turnId),
        ),
      )
    }
    return ToolBatchExecutionResult(updatedState = nextState, finalizedToolCalls = finalizedToolCalls)
  }

  private fun validateToolBatch(preparedToolCalls: List<PreparedToolCall>) {
    val duplicateToolCallIds = preparedToolCalls.groupingBy { it.toolCall.id }.eachCount().filterValues { it > 1 }
    if (duplicateToolCallIds.isNotEmpty()) {
      throw AgentRuntimeContractException("同一轮出现重复 tool_call id：${duplicateToolCallIds.keys.joinToString()}。")
    }

    val cardCallIndices = preparedToolCalls.withIndex().filter { it.value.tool.kind == AgentToolKind.Card }
    if (cardCallIndices.size > 1) {
      throw AgentRuntimeContractException(
        "同一轮最多只允许发出一张用户可见卡片，当前收到：${cardCallIndices.joinToString { it.value.toolCall.name }}。",
      )
    }

    val firstCardIndex = cardCallIndices.firstOrNull()?.index ?: return
    val dataCallsAfterCard = preparedToolCalls.drop(firstCardIndex + 1).filter { it.tool.kind == AgentToolKind.Data }
    if (dataCallsAfterCard.isNotEmpty()) {
      throw AgentRuntimeContractException(
        "工具批次顺序无效：数据工具必须先执行，卡片工具必须放在最后。违规工具：${dataCallsAfterCard.joinToString { it.toolCall.name }}。",
      )
    }
  }

  private fun compatibilityFallbackCard(
    workflowPromptPack: WorkflowPromptPack,
    responseText: String?,
    state: SessionContextState,
    executedToolNames: List<String>,
  ): AgentCardPayload? {
    if (!runtimeOptions.allowCompatibilityCardFallback) {
      return null
    }
    debugLogger.log(category = "loop", message = "兼容卡片推断已开启，仅供调试使用。", level = DebugEventLevel.Warning)
    return AgentCardValidator.validate(
      DemoCardPlanner.maybeBuildAssistantCardFallback(
        workflowPackId = workflowPromptPack.id,
        responseText = responseText,
        state = state,
        lastExecutedToolNames = executedToolNames,
      ),
    )
  }
}

private fun syncVisibleBuffer(target: StringBuilder, fullText: String?) {
  val normalized = fullText?.trim().orEmpty()
  if (normalized.isEmpty()) {
    return
  }
  val current = target.toString()
  when {
    current.isEmpty() -> target.append(normalized)
    normalized.startsWith(current) -> target.append(normalized.removePrefix(current))
    current != normalized -> {
      target.clear()
      target.append(normalized)
    }
  }
}

data class AgentLoopResult(
  val updatedState: SessionContextState,
  val updatedConversationHistory: List<ProviderConversationMessage>,
  val timelineItems: List<TimelineItem>,
)

private data class ToolBatchExecutionResult(
  val updatedState: SessionContextState,
  val finalizedToolCalls: List<FinalizedToolCall>,
)

private fun ProviderResponse.toConversationMessage(): ProviderConversationMessage? {
  if (text.isNullOrBlank() && reasoningContent.isNullOrBlank() && toolCalls.isEmpty()) {
    return null
  }
  return ProviderConversationMessage(
    role = ProviderMessageRole.Assistant,
    content = text,
    reasoningContent = reasoningContent,
    toolCalls = toolCalls,
  )
}

private fun SessionContextState.toPromptState(): String {
  val destination = selectedDestination?.let { "${it.name} / ${it.address}" } ?: "未选择"
  val candidates = destinationCandidates.joinToString("；") { "${it.name}(${it.summary})" }.ifBlank { "无" }
  val maxPrice = hotelFilterContext.maxPrice?.toString() ?: "未设置"
  val maxDistance = hotelFilterContext.maxDistanceKm?.toString() ?: "未设置"
  val hotelPlatforms = hotelResultsByPlatform.entries.joinToString("；") { (platform, results) -> "$platform=${results.size}" }.ifBlank { "无" }
  val routeReady = if (latestRouteCard != null) "已就绪" else "未就绪"
  return "selectedDestination=$destination\ndestinationCandidates=$candidates\nrouteCardData=$routeReady\nhotelMaxPrice=$maxPrice\nhotelMaxDistanceKm=$maxDistance\nhotelPlatforms=$hotelPlatforms"
}
