package com.example.openwhale.data

import android.content.Context
import com.example.openwhale.BuildConfig
import com.example.openwhale.agent.AgentSession
import com.example.openwhale.agent.AgentToolRegistry
import com.example.openwhale.agent.DemoToolFactory
import com.example.openwhale.agent.DefaultWorkflowPromptPackRepository
import com.example.openwhale.agent.InMemoryAgentDebugLogger
import com.example.openwhale.agent.LocalModelConfigStore
import com.example.openwhale.agent.ModelConfig
import com.example.openwhale.agent.provider.DeepSeekModelProvider
import okhttp3.OkHttpClient

class OpenWhaleAppContainer(context: Context) {
  private val supportedModelIds = listOf("deepseek-v4-flash", "deepseek-chat")
  private val workflowPromptPackRepository = DefaultWorkflowPromptPackRepository()
  private val debugLogger = InMemoryAgentDebugLogger()
  private val toolRegistry: AgentToolRegistry = DemoToolFactory.create(debugLogger = debugLogger)
  private val localModelConfigStore: LocalModelConfigStore = SharedPreferencesLocalModelConfigStore(context.applicationContext)
  private val modelProvider =
    DeepSeekModelProvider(
      httpClient = OkHttpClient(),
      debugLogger = debugLogger,
    )

  fun createSession(): AgentSession {
    return AgentSession(
      modelProvider = modelProvider,
      modelConfig =
        ModelConfig(
          providerId = "deepseek",
          modelId = BuildConfig.DEEPSEEK_MODEL,
          baseUrl = BuildConfig.DEEPSEEK_BASE_URL,
          apiKey = BuildConfig.DEEPSEEK_API_KEY,
          supportedModelIds = supportedModelIds,
          streamingEnabled = true,
        ),
      toolRegistry = toolRegistry,
      workflowPromptPackRepository = workflowPromptPackRepository,
      initialWorkflowPackId = BuildConfig.DEFAULT_WORKFLOW_PACK,
      debugLogger = debugLogger,
      localModelConfigStore = localModelConfigStore,
    )
  }
}

private class SharedPreferencesLocalModelConfigStore(context: Context) : LocalModelConfigStore {
  private val preferences = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

  override fun getApiKeyOverride(): String? {
    return preferences.getString(KEY_DEEPSEEK_API_KEY_OVERRIDE, null)?.trim()?.takeIf(String::isNotEmpty)
  }

  override fun saveApiKeyOverride(apiKey: String?) {
    preferences.edit().apply {
      if (apiKey.isNullOrBlank()) {
        remove(KEY_DEEPSEEK_API_KEY_OVERRIDE)
      } else {
        putString(KEY_DEEPSEEK_API_KEY_OVERRIDE, apiKey.trim())
      }
    }.apply()
  }

  override fun getModelIdOverride(): String? {
    return preferences.getString(KEY_DEEPSEEK_MODEL_OVERRIDE, null)?.trim()?.takeIf(String::isNotEmpty)
  }

  override fun saveModelIdOverride(modelId: String?) {
    preferences.edit().apply {
      if (modelId.isNullOrBlank()) {
        remove(KEY_DEEPSEEK_MODEL_OVERRIDE)
      } else {
        putString(KEY_DEEPSEEK_MODEL_OVERRIDE, modelId.trim())
      }
    }.apply()
  }

  private companion object {
    const val PREFERENCE_NAME = "openwhale_local_settings"
    const val KEY_DEEPSEEK_API_KEY_OVERRIDE = "deepseek_api_key_override"
    const val KEY_DEEPSEEK_MODEL_OVERRIDE = "deepseek_model_override"
  }
}
