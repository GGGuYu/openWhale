package com.example.openwhale.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

data class ModelConfig(
  val providerId: String,
  val modelId: String,
  val baseUrl: String,
  val apiKey: String,
  val supportedModelIds: List<String> = listOf(modelId),
  val streamingEnabled: Boolean = true,
)

data class ProviderConversationMessage(
  val role: ProviderMessageRole,
  val content: String? = null,
  val reasoningContent: String? = null,
  val toolCalls: List<AgentToolCall> = emptyList(),
  val toolCallId: String? = null,
  val toolName: String? = null,
)

enum class ProviderMessageRole {
  User,
  Assistant,
  Tool,
}

data class ProviderRequest(
  val systemPrompt: String,
  val modelConfig: ModelConfig,
  val messages: List<ProviderConversationMessage>,
  val tools: List<AgentToolDefinition>,
)

sealed interface ProviderStreamEvent {
  data class TextDelta(
    val delta: String,
  ) : ProviderStreamEvent

  data class ThinkingDelta(
    val delta: String,
  ) : ProviderStreamEvent
}

data class ProviderResponse(
  val text: String?,
  val reasoningContent: String? = null,
  val toolCalls: List<AgentToolCall>,
  val finishReason: String?,
)

data class AgentToolCall(
  val id: String,
  val name: String,
  val arguments: JsonObject,
)

data class AgentToolDefinition(
  val name: String,
  val description: String,
  val parametersSchema: JsonObject,
)

data class DestinationCandidate(
  val name: String,
  val address: String,
  val area: String,
  val summary: String,
)

data class HotelFilterContext(
  val maxPrice: Int? = null,
  val maxDistanceKm: Int? = null,
)

enum class HotelListRanking(
  val wireValue: String,
  val label: String,
) {
  Cheapest("cheapest", "低价优先"),
  Nearest("nearest", "距离优先"),
  Balanced("balanced", "价格和距离兼顾");

  companion object {
    fun fromWireValue(value: String?): HotelListRanking {
      return entries.firstOrNull { it.wireValue == value } ?: Balanced
    }
  }
}

data class SessionContextState(
  val destinationCandidates: List<DestinationCandidate> = emptyList(),
  val selectedDestination: DestinationCandidate? = null,
  val latestRouteCard: RouteCardPayload? = null,
  val hotelFilterContext: HotelFilterContext = HotelFilterContext(),
  val hotelResultsByPlatform: Map<String, List<HotelResultItem>> = emptyMap(),
  val consumedCallbackCardIds: Set<String> = emptySet(),
)

data class ToolExecutionResult(
  val displayText: String,
  val modelPayload: String,
  val nextState: SessionContextState,
  val cardPayload: AgentCardPayload? = null,
)

data class AgentRuntimeOptions(
  val allowCompatibilityCardFallback: Boolean = false,
  val dataToolDelayMs: Long = 0L,
)

data class PreparedToolCall(
  val toolCall: AgentToolCall,
  val tool: RegisteredAgentTool,
)

data class ExecutedToolCall(
  val preparedCall: PreparedToolCall,
  val result: ToolExecutionResult,
)

data class FinalizedToolCall(
  val preparedCall: PreparedToolCall,
  val result: ToolExecutionResult,
  val toolMessage: ProviderConversationMessage,
  val toolFeedbackItem: TimelineItem,
) {
  val isCardEmission: Boolean
    get() = preparedCall.tool.kind == AgentToolKind.Card
}

class AgentRuntimeContractException(
  message: String,
) : IllegalStateException(message)

fun interface AgentToolExecutor {
  suspend fun execute(arguments: JsonObject, currentState: SessionContextState): ToolExecutionResult
}

data class RegisteredAgentTool(
  val definition: AgentToolDefinition,
  val executor: AgentToolExecutor,
  val kind: AgentToolKind = AgentToolKind.Data,
)

enum class AgentToolKind {
  Data,
  Card,
}

data class WorkflowPromptPack(
  val id: String,
  val title: String,
  val systemPrompt: String,
  val starterPrompt: String,
)

interface WorkflowPromptPackRepository {
  fun list(): List<WorkflowPromptPack>

  fun get(id: String): WorkflowPromptPack
}

interface LocalModelConfigStore {
  fun getApiKeyOverride(): String?

  fun getModelIdOverride(): String?

  fun saveApiKeyOverride(apiKey: String?)

  fun saveModelIdOverride(modelId: String?)
}

enum class TimelineItemRole {
  User,
  Assistant,
  Thinking,
  Tool,
  Status,
}

data class TimelineItem(
  val id: String,
  val role: TimelineItemRole,
  val title: String,
  val text: String,
  val turnId: String? = null,
  val cardPayload: AgentCardPayload? = null,
  val isStreaming: Boolean = false,
)

sealed interface AgentPlaybackEvent {
  val turnId: String

  data class ThinkingUpdate(
    override val turnId: String,
    val text: String,
    val done: Boolean,
  ) : AgentPlaybackEvent

  data class AssistantUpdate(
    override val turnId: String,
    val text: String,
    val cardPayload: AgentCardPayload? = null,
    val done: Boolean,
  ) : AgentPlaybackEvent

  data class ToolStart(
    override val turnId: String,
    val toolItemId: String,
    val toolName: String,
  ) : AgentPlaybackEvent

  data class ToolFeedback(
    override val turnId: String,
    val item: TimelineItem,
  ) : AgentPlaybackEvent

  data class AssistantBoundary(
    override val turnId: String,
  ) : AgentPlaybackEvent
}

data class AgentSessionSnapshot(
  val timeline: List<TimelineItem>,
  val availableWorkflowPacks: List<WorkflowPromptPack>,
  val selectedWorkflowPackId: String,
  val providerLabel: String,
  val modelLabel: String,
  val supportedModelIds: List<String> = emptyList(),
  val sessionContextState: SessionContextState,
  val apiKeyConfigured: Boolean = false,
  val hasLocalApiKeyOverride: Boolean = false,
  val apiKeyStatusText: String = "",
  val debugEvents: List<DebugEvent> = emptyList(),
  val isSending: Boolean,
  val errorMessage: String? = null,
)

enum class DebugEventLevel {
  Info,
  Warning,
  Error,
}

data class DebugEvent(
  val id: String,
  val category: String,
  val message: String,
  val level: DebugEventLevel,
  val timestampMs: Long,
)

sealed interface AgentCardPayload {
  val type: String
}

data class OptionCardPayload(
  val cardId: String,
  val label: String,
  val title: String,
  val description: String? = null,
  val options: List<OptionCardChoice>,
  val allowCustomInput: Boolean = false,
  val customInputHint: String? = null,
) : AgentCardPayload {
  override val type: String = "option"
}

data class OptionCardChoice(
  val id: String,
  val title: String,
  val supportingText: String? = null,
  val action: SelectionAction,
)

data class RouteCardPayload(
  val destinationName: String,
  val destinationAddress: String,
  val routes: List<RouteCardMode>,
  val openMapAction: ExternalLinkAction,
) : AgentCardPayload {
  override val type: String = "route"
}

data class RouteCardMode(
  val mode: String,
  val title: String,
  val durationMinutes: Int,
  val distanceKm: Double,
  val summary: String,
)

data class HotelListCardPayload(
  val title: String,
  val anchorDestination: String,
  val filterSummary: String,
  val rankingLabel: String,
  val platformStatuses: List<HotelPlatformStatus>,
  val hotels: List<HotelCardItem>,
) : AgentCardPayload {
  override val type: String = "hotel_list"
}

data class HotelPlatformStatus(
  val platform: String,
  val statusText: String,
  val hasMatches: Boolean,
)

data class HotelCardItem(
  val name: String,
  val distanceKm: Double,
  val summary: String,
  val platformQuotes: List<HotelPlatformQuote>,
)

data class HotelPlatformQuote(
  val platform: String,
  val price: Int,
  val actionLabel: String,
  val actionUri: String,
)

data class ExternalLinkAction(
  val label: String,
  val uri: String,
)

data class SelectionAction(
  val promptText: String,
  val displayText: String = promptText,
  val selectedDestinationName: String? = null,
  val maxPrice: Int? = null,
  val maxDistanceKm: Int? = null,
  val sourceCardId: String? = null,
)

@Serializable
data class HotelResultItem(
  val name: String,
  val platform: String,
  val price: Int,
  val distanceKm: Double,
  val summary: String,
  val actionLabel: String,
  val actionUri: String,
)
