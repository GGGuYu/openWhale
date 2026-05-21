package com.example.openwhale.agent

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AgentDebugLogger {
  val events: StateFlow<List<DebugEvent>>

  fun log(category: String, message: String, level: DebugEventLevel = DebugEventLevel.Info)
}

object NoopAgentDebugLogger : AgentDebugLogger {
  private val emptyEvents = MutableStateFlow<List<DebugEvent>>(emptyList())

  override val events: StateFlow<List<DebugEvent>> = emptyEvents.asStateFlow()

  override fun log(category: String, message: String, level: DebugEventLevel) = Unit
}

class InMemoryAgentDebugLogger(private val maxEntries: Int = 80) : AgentDebugLogger {
  private val _events = MutableStateFlow<List<DebugEvent>>(emptyList())
  override val events: StateFlow<List<DebugEvent>> = _events.asStateFlow()

  override fun log(category: String, message: String, level: DebugEventLevel) {
    println("[OpenWhale][$category][$level] $message")
    val event =
      DebugEvent(
        id = UUID.randomUUID().toString(),
        category = category,
        message = message,
        level = level,
        timestampMs = System.currentTimeMillis(),
      )
    _events.value = (_events.value + event).takeLast(maxEntries)
  }
}
