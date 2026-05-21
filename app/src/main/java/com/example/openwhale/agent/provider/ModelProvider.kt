package com.example.openwhale.agent.provider

import com.example.openwhale.agent.ProviderRequest
import com.example.openwhale.agent.ProviderResponse

fun interface ModelProvider {
  suspend fun complete(request: ProviderRequest): ProviderResponse
}
