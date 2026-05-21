package com.example.openwhale.agent

import com.example.openwhale.agent.provider.ModelProvider
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AgentSession(
  private val modelProvider: ModelProvider,
  modelConfig: ModelConfig,
  private val toolRegistry: AgentToolRegistry,
  private val workflowPromptPackRepository: WorkflowPromptPackRepository,
  initialWorkflowPackId: String,
  private val debugLogger: AgentDebugLogger = NoopAgentDebugLogger,
  private val localModelConfigStore: LocalModelConfigStore? = null,
) {
  private val conversationHistory = mutableListOf<ProviderConversationMessage>()
  private val loopRunner =
    AgentLoopRunner(
      modelProvider = modelProvider,
      toolRegistry = toolRegistry,
      debugLogger = debugLogger,
      runtimeOptions = AgentRuntimeOptions(allowCompatibilityCardFallback = false),
    )
  private val availableWorkflowPacks = workflowPromptPackRepository.list()
  private val baseModelConfig = modelConfig
  private var sessionContextState = SessionContextState()
  private var localApiKeyOverride = localModelConfigStore?.getApiKeyOverride()
  private var modelConfig = resolvedModelConfig()

  private val _snapshot =
    MutableStateFlow(
      AgentSessionSnapshot(
        timeline = initialTimeline(workflowPromptPackRepository.get(initialWorkflowPackId)),
        availableWorkflowPacks = availableWorkflowPacks,
        selectedWorkflowPackId = workflowPromptPackRepository.get(initialWorkflowPackId).id,
        providerLabel = modelConfig.providerId,
        modelLabel = modelConfig.modelId,
        sessionContextState = sessionContextState,
        apiKeyConfigured = modelConfig.apiKey.isNotBlank(),
        hasLocalApiKeyOverride = localApiKeyOverride != null,
        apiKeyStatusText = apiKeyStatusText(),
        isSending = false,
      ),
    )
  val snapshot: StateFlow<AgentSessionSnapshot> = _snapshot.asStateFlow()
  val debugEvents: StateFlow<List<DebugEvent>> = debugLogger.events

  suspend fun switchWorkflowPack(packId: String) {
    val selectedPack = workflowPromptPackRepository.get(packId)
    conversationHistory.clear()
    sessionContextState = SessionContextState()
    debugLogger.log(category = "session", message = "切换工作流到 ${selectedPack.id}")
    _snapshot.value =
      _snapshot.value.copy(
        timeline = initialTimeline(selectedPack),
        selectedWorkflowPackId = selectedPack.id,
        sessionContextState = sessionContextState,
        apiKeyConfigured = modelConfig.apiKey.isNotBlank(),
        hasLocalApiKeyOverride = localApiKeyOverride != null,
        apiKeyStatusText = apiKeyStatusText(),
        errorMessage = null,
        isSending = false,
      )
  }

  suspend fun updateApiKey(apiKey: String) {
    val normalizedOverride = apiKey.trim().takeIf(String::isNotEmpty)
    if (normalizedOverride == localApiKeyOverride) {
      return
    }

    localModelConfigStore?.saveApiKeyOverride(normalizedOverride)
    localApiKeyOverride = normalizedOverride
    modelConfig = resolvedModelConfig()

    val statusText =
      if (normalizedOverride == null) {
        if (baseModelConfig.apiKey.isBlank()) {
          "已清空本地 DeepSeek API Key，当前没有可用密钥。"
        } else {
          "已清空本地 DeepSeek API Key，后续请求将回退到构建配置。"
        }
      } else {
        "已保存本地 DeepSeek API Key，后续请求会直接使用新密钥。"
      }

    debugLogger.log(category = "session", message = statusText)
    _snapshot.value =
      _snapshot.value.copy(
        timeline =
          _snapshot.value.timeline +
            TimelineItem(
              id = UUID.randomUUID().toString(),
              role = TimelineItemRole.Status,
              title = "模型配置",
              text = statusText,
            ),
        apiKeyConfigured = modelConfig.apiKey.isNotBlank(),
        hasLocalApiKeyOverride = localApiKeyOverride != null,
        apiKeyStatusText = apiKeyStatusText(),
        errorMessage = null,
      )
  }

  suspend fun sendUserMessage(text: String) {
    if (text.isBlank() || _snapshot.value.isSending) {
      return
    }

    sendInput(userText = text.trim(), displayText = text.trim())
  }

  suspend fun submitSelection(action: SelectionAction) {
    if (_snapshot.value.isSending) {
      return
    }
    if (action.sourceCardId != null && action.sourceCardId in sessionContextState.consumedCallbackCardIds) {
      debugLogger.log(category = "session", message = "忽略重复卡片点击：${action.displayText}", level = DebugEventLevel.Warning)
      return
    }
    debugLogger.log(category = "session", message = "收到卡片选择：${action.displayText}")
    sessionContextState = sessionContextState.applySelection(action)
    sendInput(userText = action.promptText, displayText = action.displayText, currentState = sessionContextState)
  }

  private suspend fun sendInput(userText: String, displayText: String, currentState: SessionContextState = sessionContextState) {
    debugLogger.log(category = "session", message = "发送输入：$userText")
    conversationHistory += ProviderConversationMessage(role = ProviderMessageRole.User, content = userText)
    _snapshot.value =
      _snapshot.value.copy(
        isSending = true,
        errorMessage = null,
        sessionContextState = currentState,
        apiKeyConfigured = modelConfig.apiKey.isNotBlank(),
        hasLocalApiKeyOverride = localApiKeyOverride != null,
        apiKeyStatusText = apiKeyStatusText(),
        debugEvents = debugLogger.events.value,
        timeline = _snapshot.value.timeline + TimelineItem(id = UUID.randomUUID().toString(), role = TimelineItemRole.User, title = "你", text = displayText),
      )

    runCatching {
      loopRunner.run(
        modelConfig = modelConfig,
        workflowPromptPack = workflowPromptPackRepository.get(_snapshot.value.selectedWorkflowPackId),
        conversationHistory = conversationHistory,
        currentState = currentState,
      )
    }.onSuccess { result ->
      conversationHistory.clear()
      conversationHistory += result.updatedConversationHistory
      sessionContextState = result.updatedState
      debugLogger.log(category = "session", message = "本次会话更新完成，目的地=${sessionContextState.selectedDestination?.name ?: "无"}，预算=${sessionContextState.hotelFilterContext.maxPrice ?: "未设"}，距离=${sessionContextState.hotelFilterContext.maxDistanceKm ?: "未设"}")
      _snapshot.value =
        _snapshot.value.copy(
          timeline = _snapshot.value.timeline + result.timelineItems,
          sessionContextState = sessionContextState,
          apiKeyConfigured = modelConfig.apiKey.isNotBlank(),
          hasLocalApiKeyOverride = localApiKeyOverride != null,
          apiKeyStatusText = apiKeyStatusText(),
          debugEvents = debugLogger.events.value,
          isSending = false,
        )
    }.onFailure { throwable ->
      debugLogger.log(category = "session", message = throwable.message ?: "请求失败", level = DebugEventLevel.Error)
      _snapshot.value =
        _snapshot.value.copy(
          timeline =
            _snapshot.value.timeline +
              TimelineItem(
                id = UUID.randomUUID().toString(),
                role = TimelineItemRole.Status,
                title = "错误",
                text = throwable.message ?: "请求失败",
              ),
          apiKeyConfigured = modelConfig.apiKey.isNotBlank(),
          hasLocalApiKeyOverride = localApiKeyOverride != null,
          apiKeyStatusText = apiKeyStatusText(),
          errorMessage = throwable.message,
          debugEvents = debugLogger.events.value,
          isSending = false,
        )
    }
  }

  private fun initialTimeline(selectedPack: WorkflowPromptPack): List<TimelineItem> {
    val configHint = if (modelConfig.apiKey.isBlank()) "未检测到可用的 DeepSeek API Key，联网对话会失败。" else "已就绪，可直接开始。"
    return listOf(
      TimelineItem(id = UUID.randomUUID().toString(), role = TimelineItemRole.Status, title = "工作流", text = "当前工作流：${selectedPack.title}。${selectedPack.starterPrompt}"),
      TimelineItem(id = UUID.randomUUID().toString(), role = TimelineItemRole.Status, title = "模型", text = "${modelConfig.providerId} / ${modelConfig.modelId}。$configHint"),
    )
  }

  private fun resolvedModelConfig(): ModelConfig = baseModelConfig.copy(apiKey = localApiKeyOverride ?: baseModelConfig.apiKey)

  private fun apiKeyStatusText(): String {
    return when {
      localApiKeyOverride != null -> "当前使用本地 API Key · ${localApiKeyOverride?.maskedKeyLabel().orEmpty()}"
      baseModelConfig.apiKey.isNotBlank() -> "当前使用构建配置中的 API Key"
      else -> "当前未配置 API Key，请先在应用内填写"
    }
  }
}

private fun String.maskedKeyLabel(): String {
  return when {
    length <= 8 -> "已配置"
    else -> "${take(4)}••••${takeLast(4)}"
  }
}

private fun SessionContextState.applySelection(action: SelectionAction): SessionContextState {
  val updatedSelectedDestination =
    action.selectedDestinationName?.let { destinationName ->
      destinationCandidates.firstOrNull { it.name == destinationName } ?: this.selectedDestination
    } ?: this.selectedDestination
  val updatedFilters =
    hotelFilterContext.copy(
      maxPrice = action.maxPrice ?: hotelFilterContext.maxPrice,
      maxDistanceKm = action.maxDistanceKm ?: hotelFilterContext.maxDistanceKm,
    )
  val shouldClearHotelResults = action.maxPrice != null || action.maxDistanceKm != null || action.selectedDestinationName != null
  return copy(
    selectedDestination = updatedSelectedDestination,
    latestRouteCard = if (action.selectedDestinationName != null) null else latestRouteCard,
    hotelFilterContext = updatedFilters,
    hotelResultsByPlatform = if (shouldClearHotelResults) emptyMap() else hotelResultsByPlatform,
    consumedCallbackCardIds = action.sourceCardId?.let { consumedCallbackCardIds + it } ?: consumedCallbackCardIds,
  )
}
