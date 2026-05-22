package com.example.openwhale.agent.provider

import com.example.openwhale.agent.ProviderStreamEvent
import com.example.openwhale.agent.ProviderRequest
import com.example.openwhale.agent.ProviderResponse

interface ModelProvider {
  suspend fun complete(
    request: ProviderRequest,
    onStreamEvent: suspend (ProviderStreamEvent) -> Unit = {},
  ): ProviderResponse
}
