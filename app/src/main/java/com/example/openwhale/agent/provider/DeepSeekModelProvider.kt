package com.example.openwhale.agent.provider

import com.example.openwhale.agent.AgentToolCall
import com.example.openwhale.agent.AgentDebugLogger
import com.example.openwhale.agent.DebugEventLevel
import com.example.openwhale.agent.ModelConfig
import com.example.openwhale.agent.NoopAgentDebugLogger
import com.example.openwhale.agent.ProviderStreamEvent
import com.example.openwhale.agent.ProviderConversationMessage
import com.example.openwhale.agent.ProviderMessageRole
import com.example.openwhale.agent.ProviderRequest
import com.example.openwhale.agent.ProviderResponse
import java.io.EOFException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource

class DeepSeekModelProvider(
  private val httpClient: OkHttpClient,
  private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true },
  private val debugLogger: AgentDebugLogger = NoopAgentDebugLogger,
) : ModelProvider {
  override suspend fun complete(
    request: ProviderRequest,
    onStreamEvent: suspend (ProviderStreamEvent) -> Unit,
  ): ProviderResponse {
    return withContext(Dispatchers.IO) {
      require(request.modelConfig.apiKey.isNotBlank()) { "DeepSeek API Key 未配置，请通过 DEEPSEEK_API_KEY 环境变量或 Gradle 属性注入。" }
      debugLogger.log(
        category = "provider",
        message = "请求 ${request.modelConfig.providerId}/${request.modelConfig.modelId}，messages=${request.messages.size}，tools=${request.tools.size}",
      )

      val payload =
        ChatCompletionRequest(
          model = request.modelConfig.modelId,
          messages =
            listOf(ChatCompletionMessage(role = "system", content = request.systemPrompt)) +
              request.messages.map { it.toWireMessage(request.modelConfig) },
          tools = request.tools.map { tool -> ChatCompletionTool(function = ChatCompletionFunction(name = tool.name, description = tool.description, parameters = tool.parametersSchema)) },
          toolChoice = "auto",
          temperature = 0.2,
          stream = request.modelConfig.streamingEnabled,
          thinking = request.modelConfig.toThinkingConfig(),
        )
      val requestBody = json.encodeToString(ChatCompletionRequest.serializer(), payload).toRequestBody("application/json".toMediaType())
      val httpRequest =
        Request.Builder()
          .url(request.modelConfig.baseUrl.trimEnd('/') + "/chat/completions")
          .addHeader("Authorization", "Bearer ${request.modelConfig.apiKey}")
          .addHeader("Content-Type", "application/json")
          .post(requestBody)
          .build()

      httpClient.newCall(httpRequest).execute().use { response ->
        check(response.isSuccessful) { "DeepSeek 请求失败(${response.code}): ${response.body.string()}" }
        if (!request.modelConfig.streamingEnabled) {
          val responseText = response.body.string()
          debugLogger.log(category = "provider", message = "HTTP ${response.code}，响应长度=${responseText.length}")
          val completion = json.decodeFromString(ChatCompletionResponse.serializer(), responseText)
          val choice = completion.choices.firstOrNull() ?: error("DeepSeek 没有返回可用 choice")
          val toolCalls =
            choice.message.toolCalls.orEmpty().map { toolCall ->
              AgentToolCall(
                id = toolCall.id,
                name = toolCall.function.name,
                arguments = parseToolArguments(toolCall.function.arguments),
              )
            }
          debugLogger.log(
            category = "provider",
            message = "解析完成，finishReason=${choice.finishReason ?: "unknown"}，toolCalls=${toolCalls.size}，text=${choice.message.content?.trim()?.take(80) ?: "<empty>"}",
          )
          ProviderResponse(
            text = choice.message.content?.trim()?.ifEmpty { null },
            reasoningContent = choice.message.reasoningContent?.trim()?.ifEmpty { null },
            toolCalls = toolCalls,
            finishReason = choice.finishReason,
          )
        } else {
          debugLogger.log(category = "provider", message = "HTTP ${response.code}，进入流式读取")
          parseStreamingResponse(source = response.body.source(), onStreamEvent = onStreamEvent)
        }
      }
    }
  }

  private suspend fun parseStreamingResponse(
    source: BufferedSource,
    onStreamEvent: suspend (ProviderStreamEvent) -> Unit,
  ): ProviderResponse {
    val fullText = StringBuilder()
    val fullReasoning = StringBuilder()
    val toolCallBuilders = linkedMapOf<Int, StreamingToolCallBuilder>()
    var finishReason: String? = null

    while (true) {
      val line = try {
        source.readUtf8Line() ?: break
      } catch (_: EOFException) {
        break
      }
      if (line.isBlank() || !line.startsWith("data:")) {
        continue
      }
      val payload = line.removePrefix("data:").trim()
      if (payload == "[DONE]") {
        break
      }
      val chunk = runCatching { json.decodeFromString(ChatCompletionChunk.serializer(), payload) }.getOrElse {
        debugLogger.log(category = "provider", message = "忽略无法解析的流式分片：${payload.take(120)}", level = DebugEventLevel.Warning)
        continue
      }
      chunk.choices.forEach { choice ->
        finishReason = choice.finishReason ?: finishReason
        val delta = choice.delta ?: return@forEach
        delta.reasoningContent?.takeIf { it.isNotEmpty() }?.let { reasoningDelta ->
          fullReasoning.append(reasoningDelta)
          onStreamEvent(ProviderStreamEvent.ThinkingDelta(reasoningDelta))
        }
        delta.content?.takeIf { it.isNotEmpty() }?.let { contentDelta ->
          fullText.append(contentDelta)
          onStreamEvent(ProviderStreamEvent.TextDelta(contentDelta))
        }
        delta.toolCalls.orEmpty().forEach { toolCall ->
          val index = toolCall.index ?: toolCallBuilders.size
          val builder = toolCallBuilders.getOrPut(index) { StreamingToolCallBuilder() }
          toolCall.id?.let { builder.id = it }
          toolCall.function?.name?.let { builder.name = it }
          toolCall.function?.arguments?.let { builder.arguments.append(it) }
        }
      }
    }

    val toolCalls =
      toolCallBuilders.values.mapIndexedNotNull { index, builder ->
        val name = builder.name ?: return@mapIndexedNotNull null
        val argumentsText = builder.arguments.toString().ifBlank { "{}" }
        AgentToolCall(
          id = builder.id ?: "stream-tool-$index",
          name = name,
          arguments = parseToolArguments(argumentsText),
        )
      }
    val resolvedText = fullText.toString().trim().ifEmpty { null }
    val resolvedReasoning = fullReasoning.toString().trim().ifEmpty { null }
    debugLogger.log(
      category = "provider",
      message = "流式解析完成，finishReason=${finishReason ?: "unknown"}，toolCalls=${toolCalls.size}，text=${resolvedText?.take(80) ?: "<empty>"}",
    )
    return ProviderResponse(
      text = resolvedText,
      reasoningContent = resolvedReasoning,
      toolCalls = toolCalls,
      finishReason = finishReason,
    )
  }

  private fun ProviderConversationMessage.toWireMessage(modelConfig: ModelConfig): ChatCompletionMessage {
    return when (role) {
      ProviderMessageRole.User -> ChatCompletionMessage(role = "user", content = content.orEmpty())
      ProviderMessageRole.Assistant ->
        ChatCompletionMessage(
          role = "assistant",
          content = content,
          reasoningContent = reasoningContent.takeIf { shouldReplayReasoning(modelConfig) },
          toolCalls =
            toolCalls.takeIf { it.isNotEmpty() }?.map { toolCall ->
              ChatCompletionToolCall(
                id = toolCall.id,
                type = "function",
                function = ChatCompletionToolCallFunction(name = toolCall.name, arguments = toolCall.arguments.toString()),
              )
            },
        )
      ProviderMessageRole.Tool ->
        ChatCompletionMessage(
          role = "tool",
          content = content.orEmpty(),
          toolCallId = toolCallId,
          name = toolName,
        )
    }
  }

  private fun ProviderConversationMessage.shouldReplayReasoning(modelConfig: ModelConfig): Boolean {
    return modelConfig.isThinkingModeEnabled() && !reasoningContent.isNullOrBlank() && toolCalls.isNotEmpty()
  }

  private fun parseToolArguments(rawArguments: String): JsonObject {
    val normalized = rawArguments.trim().ifBlank { "{}" }
    return runCatching { json.parseToJsonElement(normalized).jsonObject }
      .recoverCatching {
        recoverTrailingJsonObject(normalized)
      }
      .getOrElse { throwable ->
        throw IllegalArgumentException("工具参数 JSON 解析失败: ${throwable.message}; 原始内容=${normalized.take(400)}", throwable)
      }
  }

  private fun recoverTrailingJsonObject(rawArguments: String): JsonObject {
    var candidate = rawArguments.trim()
    while (candidate.length > 2 && (candidate.last() == '}' || candidate.last() == ']')) {
      candidate = candidate.dropLast(1).trimEnd()
      runCatching { json.parseToJsonElement(candidate).jsonObject }.getOrNull()?.let { return it }
    }
    error("无法从工具参数中恢复合法 JSON 对象")
  }

  private fun ModelConfig.isThinkingModeEnabled(): Boolean {
    return modelId != "deepseek-chat"
  }

  private fun ModelConfig.toThinkingConfig(): DeepSeekThinkingConfig {
    return if (isThinkingModeEnabled()) {
      DeepSeekThinkingConfig(type = "enabled")
    } else {
      DeepSeekThinkingConfig(type = "disabled")
    }
  }
}

@Serializable
private data class ChatCompletionRequest(
  val model: String,
  val messages: List<ChatCompletionMessage>,
  val tools: List<ChatCompletionTool>,
  @SerialName("tool_choice")
  val toolChoice: String,
  val temperature: Double,
  val stream: Boolean = false,
  val thinking: DeepSeekThinkingConfig? = null,
  // 思考强度：支持 "high" / "max"，目前写死 max 以获得最佳推理质量
  @SerialName("reasoning_effort")
  val reasoningEffort: String = "max",
)

@Serializable
private data class DeepSeekThinkingConfig(
  val type: String,
)

@Serializable
private data class ChatCompletionMessage(
  val role: String,
  val content: String? = null,
  @SerialName("reasoning_content")
  val reasoningContent: String? = null,
  val name: String? = null,
  @SerialName("tool_call_id")
  val toolCallId: String? = null,
  @SerialName("tool_calls")
  val toolCalls: List<ChatCompletionToolCall>? = null,
)

@Serializable
private data class ChatCompletionTool(
  val type: String = "function",
  val function: ChatCompletionFunction,
)

@Serializable
private data class ChatCompletionFunction(
  val name: String,
  val description: String,
  val parameters: JsonObject,
)

@Serializable
private data class ChatCompletionResponse(
  val choices: List<ChatCompletionChoice>,
)

@Serializable
private data class ChatCompletionChoice(
  val message: ChatCompletionResponseMessage,
  @SerialName("finish_reason")
  val finishReason: String? = null,
)

@Serializable
private data class ChatCompletionResponseMessage(
  val content: String? = null,
  @SerialName("reasoning_content")
  val reasoningContent: String? = null,
  @SerialName("tool_calls")
  val toolCalls: List<ChatCompletionToolCall>? = null,
)

@Serializable
private data class ChatCompletionToolCall(
  val id: String,
  val type: String,
  val function: ChatCompletionToolCallFunction,
)

@Serializable
private data class ChatCompletionToolCallFunction(
  val name: String,
  val arguments: String,
)

@Serializable
private data class ChatCompletionChunk(
  val choices: List<ChatCompletionChunkChoice> = emptyList(),
)

@Serializable
private data class ChatCompletionChunkChoice(
  val delta: ChatCompletionChunkDelta? = null,
  @SerialName("finish_reason")
  val finishReason: String? = null,
)

@Serializable
private data class ChatCompletionChunkDelta(
  val content: String? = null,
  @SerialName("reasoning_content")
  val reasoningContent: String? = null,
  @SerialName("tool_calls")
  val toolCalls: List<ChatCompletionChunkToolCall>? = null,
)

@Serializable
private data class ChatCompletionChunkToolCall(
  val index: Int? = null,
  val id: String? = null,
  val function: ChatCompletionChunkToolCallFunction? = null,
)

@Serializable
private data class ChatCompletionChunkToolCallFunction(
  val name: String? = null,
  val arguments: String? = null,
)

private data class StreamingToolCallBuilder(
  var id: String? = null,
  var name: String? = null,
  val arguments: StringBuilder = StringBuilder(),
)
