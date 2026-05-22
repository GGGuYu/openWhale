package com.example.openwhale.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.openwhale.agent.AgentSession
import com.example.openwhale.agent.AgentSessionSnapshot
import com.example.openwhale.agent.SelectionAction
import com.example.openwhale.data.OpenWhaleAppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainScreenViewModel(private val agentSession: AgentSession) : ViewModel() {
  val uiState: StateFlow<AgentSessionSnapshot> =
    combine(agentSession.snapshot, agentSession.debugEvents) { snapshot, debugEvents ->
      snapshot.copy(debugEvents = debugEvents)
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = agentSession.snapshot.value,
    )

  fun sendMessage(text: String) {
    viewModelScope.launch { agentSession.sendUserMessage(text) }
  }

  fun selectWorkflowPack(packId: String) {
    viewModelScope.launch { agentSession.switchWorkflowPack(packId) }
  }

  fun submitSelection(action: SelectionAction) {
    viewModelScope.launch { agentSession.submitSelection(action) }
  }

  fun updateApiKey(apiKey: String) {
    viewModelScope.launch { agentSession.updateApiKey(apiKey) }
  }

  fun resetSession() {
    viewModelScope.launch { agentSession.resetSession() }
  }

  fun updateModelId(modelId: String) {
    viewModelScope.launch { agentSession.updateModelId(modelId) }
  }

  companion object {
    fun create(context: Context): MainScreenViewModel = MainScreenViewModel(OpenWhaleAppContainer(context).createSession())
  }
}
