package com.example.openwhale.agent.provider

import com.example.openwhale.agent.AgentToolCall
import com.example.openwhale.agent.AgentDebugLogger
import com.example.openwhale.agent.DebugEventLevel
import com.example.openwhale.agent.ModelConfig
import com.example.openwhale.agent.NoopAgentDebugLogger
import com.example.openwhale.agent.ProviderConversationMessage
import com.example.openwhale.agent.ProviderMessageRole
import com.example.openwhale.agent.ProviderRequest
import com.example.openwhale.agent.ProviderResponse
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

class DeepSeekModelProvider(
  private val httpClient: OkHttpClient,
  private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true },
  private val debugLogger: AgentDebugLogger = NoopAgentDebugLogger,
) : ModelProvider {
  override suspend fun complete(request: ProviderRequest): ProviderResponse {
    return withContext(Dispatchers.IO) {
      require(request.modelConfig.apiKey.isNotBlank()) { "DeepSeek API Key 未配置，请通过 DEEPSEEK_API_KEY 环境变量或 Gradle 属性注入。" }
      debugLogger.log(
        category = "provider",
        message = "请求 ${request.modelConfig.providerId}/${request.modelConfig.modelId}，messages=${request.messages.size}，tools=${request.tools.size}",
      )

      val payload =
        ChatCompletionRequest(
          model = request.modelConfig.modelId,
          messages = listOf(ChatCompletionMessage(role = "system", content = request.systemPrompt)) + request.messages.map { it.toWireMessage() },
          tools = request.tools.map { tool -> ChatCompletionTool(function = ChatCompletionFunction(name = tool.name, description = tool.description, parameters = tool.parametersSchema)) },
          toolChoice = "auto",
          temperature = 0.2,
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
        val responseText = response.body.string()
        debugLogger.log(category = "provider", message = "HTTP ${response.code}，响应长度=${responseText.length}")
        check(response.isSuccessful) { "DeepSeek 请求失败(${response.code}): $responseText" }
        val completion = json.decodeFromString(ChatCompletionResponse.serializer(), responseText)
        val choice = completion.choices.firstOrNull() ?: error("DeepSeek 没有返回可用 choice")
        val toolCalls =
          choice.message.toolCalls.orEmpty().map { toolCall ->
            AgentToolCall(
              id = toolCall.id,
              name = toolCall.function.name,
              arguments = json.parseToJsonElement(toolCall.function.arguments).jsonObject,
            )
          }
        debugLogger.log(
          category = "provider",
          message = "解析完成，finishReason=${choice.finishReason ?: "unknown"}，toolCalls=${toolCalls.size}，text=${choice.message.content?.trim()?.take(80) ?: "<empty>"}",
        )
        ProviderResponse(text = choice.message.content?.trim()?.ifEmpty { null }, toolCalls = toolCalls, finishReason = choice.finishReason)
      }
    }
  }

  private fun ProviderConversationMessage.toWireMessage(): ChatCompletionMessage {
    return when (role) {
      ProviderMessageRole.User -> ChatCompletionMessage(role = "user", content = content.orEmpty())
      ProviderMessageRole.Assistant ->
        ChatCompletionMessage(
          role = "assistant",
          content = content,
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
}

@Serializable
private data class ChatCompletionRequest(
  val model: String,
  val messages: List<ChatCompletionMessage>,
  val tools: List<ChatCompletionTool>,
  @SerialName("tool_choice")
  val toolChoice: String,
  val temperature: Double,
)

@Serializable
private data class ChatCompletionMessage(
  val role: String,
  val content: String? = null,
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
