package com.example.openwhale.ui.main

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.openwhale.agent.AgentSessionSnapshot
import com.example.openwhale.agent.HotelFilterContext
import com.example.openwhale.agent.OptionCardChoice
import com.example.openwhale.agent.OptionCardPayload
import com.example.openwhale.agent.SelectionAction
import com.example.openwhale.agent.SessionContextState
import com.example.openwhale.agent.TimelineItem
import com.example.openwhale.agent.TimelineItemRole
import com.example.openwhale.agent.WorkflowPromptPack
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.runBlocking

class MainScreenTest {

  @get:Rule val composeTestRule = createComposeRule()
  private lateinit var previewListState: LazyListState

  private fun setScreen(snapshot: AgentSessionSnapshot = previewSnapshot()) {
    composeTestRule.setContent {
      previewListState = rememberLazyListState()
      MainScreen(
        uiState = snapshot,
        listState = previewListState,
      )
    }
  }

  private fun previewSnapshot(
    timeline: List<TimelineItem> =
      listOf(
        TimelineItem(id = "status-1", role = TimelineItemRole.Status, title = "工作流", text = "当前工作流：导航找酒店 Demo。"),
        TimelineItem(id = "user-1", role = TimelineItemRole.User, title = "你", text = "导航到静安寺"),
        TimelineItem(id = "thinking-1", role = TimelineItemRole.Thinking, title = "思考轨迹", text = "先确认目的地，再生成选项卡。", turnId = "turn-1"),
        TimelineItem(id = "tool-1", role = TimelineItemRole.Tool, title = "search_destination", text = "已返回 3 个候选地点", turnId = "turn-1"),
        TimelineItem(
          id = "assistant-1",
          role = TimelineItemRole.Assistant,
          title = "助手",
          text = "你想去哪一个？",
          turnId = "turn-1",
          cardPayload =
            OptionCardPayload(
              cardId = "preview-option-card",
              label = "候选地点",
              title = "你想去哪个点？",
              options =
                listOf(
                  OptionCardChoice(
                    id = "jingan-temple",
                    title = "静安寺",
                    supportingText = "静安区 · 寺庙景点，适合直接导航",
                    action = SelectionAction(promptText = "我选 静安寺", selectedDestinationName = "静安寺"),
                  ),
                ),
            ),
        ),
      ),
  ): AgentSessionSnapshot {
    return AgentSessionSnapshot(
      timeline = timeline,
      availableWorkflowPacks =
        listOf(
          WorkflowPromptPack(id = "navigation_hotel", title = "导航找酒店 Demo", systemPrompt = "", starterPrompt = ""),
        ),
      selectedWorkflowPackId = "navigation_hotel",
      providerLabel = "deepseek",
      modelLabel = "deepseek-chat",
      supportedModelIds = listOf("deepseek-v4-flash", "deepseek-chat"),
      sessionContextState = SessionContextState(hotelFilterContext = HotelFilterContext()),
      isSending = false,
    )
  }

  @Test
  fun optionCard_isRendered() {
    setScreen()
    composeTestRule.onNodeWithText("候选地点").assertIsDisplayed()
    composeTestRule.onNodeWithText("你想去哪个点？").assertIsDisplayed()
    composeTestRule.onNodeWithText("静安寺").assertIsDisplayed()
  }

  @Test
  fun compactShell_isRendered() {
    setScreen(
      previewSnapshot(
        timeline = listOf(TimelineItem(id = "status-only", role = TimelineItemRole.Status, title = "工作流", text = "当前工作流：导航找酒店 Demo。")),
      ),
    )
    composeTestRule.onNodeWithText("OpenWhale").assertIsDisplayed()
    composeTestRule.onNodeWithText("历史").assertIsDisplayed()
    composeTestRule.onNodeWithText("设置").assertIsDisplayed()
  }

  @Test
  fun messageAvatar_isRendered() {
    setScreen()
    composeTestRule.onNodeWithContentDescription("助手头像").assertIsDisplayed()
  }

  @Test
  fun messageHeader_showsRoleLabelInlineWithAvatar() {
    setScreen(
      previewSnapshot(
        timeline =
          listOf(
            TimelineItem(id = "user-inline", role = TimelineItemRole.User, title = "你", text = "我想去静安寺"),
            TimelineItem(id = "assistant-inline", role = TimelineItemRole.Assistant, title = "助手", text = "我来帮你规划"),
          ),
      ),
    )

    composeTestRule.onNodeWithContentDescription("用户头像").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("助手头像").assertIsDisplayed()
    composeTestRule.onNodeWithText("用户").assertIsDisplayed()
    composeTestRule.onNodeWithText("助手").assertIsDisplayed()
  }

  @Test
  fun settingsSheet_canBeOpened() {
    setScreen(
      previewSnapshot(
        timeline = listOf(TimelineItem(id = "status-only", role = TimelineItemRole.Status, title = "工作流", text = "当前工作流：导航找酒店 Demo。")),
      ),
    )
    composeTestRule.onNodeWithText("设置").performClick()
    composeTestRule.onNodeWithText("DeepSeek Key").assertIsDisplayed()
    composeTestRule.onNodeWithText("保存本地 Key").assertIsDisplayed()
  }

  @Test
  fun toolFeedback_markerIsRendered() {
    setScreen()
    composeTestRule.onNodeWithText("🔎").assertIsDisplayed()
    composeTestRule.onNodeWithText("工具反馈").assertIsDisplayed()
  }

  @Test
  fun historySheet_canBeOpened() {
    setScreen(
      previewSnapshot(
        timeline = listOf(TimelineItem(id = "user-only", role = TimelineItemRole.User, title = "你", text = "导航到静安寺")),
      ),
    )
    composeTestRule.onNodeWithText("历史").performClick()
    composeTestRule.onNodeWithText("最近对话").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("导航到静安寺").assertCountEquals(2)
  }

  @Test
  fun thinkingTrace_canCollapseAndExpandIndependently() {
    setScreen(
      previewSnapshot(
        timeline =
          listOf(
            TimelineItem(id = "thinking-one", role = TimelineItemRole.Thinking, title = "思考轨迹", text = "先确认目的地，再生成选项卡。\n确认后再决定是否继续调用工具。", turnId = "turn-thinking"),
            TimelineItem(id = "thinking-two", role = TimelineItemRole.Thinking, title = "思考轨迹", text = "第二条思考会继续跟进酒店过滤条件。", turnId = "turn-thinking"),
            TimelineItem(id = "assistant-only", role = TimelineItemRole.Assistant, title = "助手", text = "已准备好候选项。", turnId = "turn-thinking"),
          ),
      ),
    )
    composeTestRule.onAllNodesWithText("已折叠思考轨迹").assertCountEquals(2)
    composeTestRule.onAllNodesWithText("先确认目的地，再生成选项卡。\n确认后再决定是否继续调用工具。").assertCountEquals(0)
    composeTestRule.onAllNodesWithText("第二条思考会继续跟进酒店过滤条件。").assertCountEquals(0)

    composeTestRule.onAllNodesWithText("已折叠思考轨迹")[0].performClick()
    composeTestRule.onNodeWithText("先确认目的地，再生成选项卡。\n确认后再决定是否继续调用工具。").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("第二条思考会继续跟进酒店过滤条件。").assertCountEquals(0)
    composeTestRule.onAllNodesWithText("已折叠思考轨迹").assertCountEquals(1)

    composeTestRule.onAllNodesWithText("收起")[0].performClick()
    composeTestRule.onAllNodesWithText("已折叠思考轨迹").assertCountEquals(2)
  }

  @Test
  fun timelineAutoFollow_keepsLatestItemVisibleWhenPinned() {
    lateinit var listState: LazyListState
    var uiState by mutableStateOf(
      previewSnapshot(
        timeline =
          (1..18).map { index ->
            TimelineItem(
              id = "status-$index",
              role = TimelineItemRole.Status,
              title = "状态 $index",
              text = "历史片段 $index",
            )
          },
      ),
    )

    composeTestRule.setContent {
      listState = rememberLazyListState()
      MainScreen(uiState = uiState, listState = listState)
    }

    composeTestRule.runOnIdle {
      runBlocking { listState.scrollToItem(uiState.timeline.size) }
    }

    composeTestRule.runOnUiThread {
      uiState =
        uiState.copy(
          timeline =
            uiState.timeline +
              TimelineItem(
                id = "tool-latest",
                role = TimelineItemRole.Tool,
                title = "emit_option_card",
                text = "新增工具反馈",
              ),
        )
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("新增工具反馈").assertIsDisplayed()
  }

  @Test
  fun timelineAutoFollow_doesNotInterruptWhenUserScrolledUp() {
    lateinit var listState: LazyListState
    var uiState by mutableStateOf(
      previewSnapshot(
        timeline =
          (1..18).map { index ->
            TimelineItem(
              id = "status-$index",
              role = TimelineItemRole.Status,
              title = "状态 $index",
              text = "历史片段 $index",
            )
          },
      ),
    )

    composeTestRule.setContent {
      listState = rememberLazyListState()
      MainScreen(uiState = uiState, listState = listState)
    }

    composeTestRule.runOnIdle {
      runBlocking { listState.scrollToItem(0) }
    }

    composeTestRule.runOnUiThread {
      uiState =
        uiState.copy(
          timeline =
            uiState.timeline +
              TimelineItem(
                id = "assistant-latest",
                role = TimelineItemRole.Assistant,
                title = "助手",
                text = "用户上滑时不应强制跟随",
              ),
        )
    }

    composeTestRule.waitForIdle()
    composeTestRule.onAllNodesWithText("用户上滑时不应强制跟随").assertCountEquals(0)
  }

  @Test
  fun streamingUpdate_followsWhenPinnedToBottom() {
    lateinit var listState: LazyListState
    var uiState by mutableStateOf(
      previewSnapshot(
        timeline =
          (1..18).map { index ->
            TimelineItem(
              id = "status-$index",
              role = TimelineItemRole.Status,
              title = "状态 $index",
              text = "历史片段 $index",
            )
          } + TimelineItem(
            id = "assistant-streaming",
            role = TimelineItemRole.Assistant,
            title = "助手",
            text = "正在生成",
            isStreaming = true,
          ),
      ),
    )

    composeTestRule.setContent {
      listState = rememberLazyListState()
      MainScreen(uiState = uiState, listState = listState)
    }

    composeTestRule.runOnIdle {
      runBlocking { listState.scrollToItem(uiState.timeline.size) }
    }

    composeTestRule.runOnUiThread {
      uiState =
        uiState.copy(
          timeline =
            uiState.timeline.dropLast(1) +
              TimelineItem(
                id = "assistant-streaming",
                role = TimelineItemRole.Assistant,
                title = "助手",
                text = "流式回复的完整内容已经生成",
                isStreaming = true,
              ),
        )
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("流式回复的完整内容已经生成").assertIsDisplayed()
  }

  @Test
  fun cardInsertion_followsWhenPinnedToBottom() {
    lateinit var listState: LazyListState
    var uiState by mutableStateOf(
      previewSnapshot(
        timeline =
          (1..18).map { index ->
            TimelineItem(
              id = "status-$index",
              role = TimelineItemRole.Status,
              title = "状态 $index",
              text = "历史片段 $index",
            )
          },
      ),
    )

    composeTestRule.setContent {
      listState = rememberLazyListState()
      MainScreen(uiState = uiState, listState = listState)
    }

    composeTestRule.runOnIdle {
      runBlocking { listState.scrollToItem(uiState.timeline.size) }
    }

    composeTestRule.runOnUiThread {
      uiState =
        uiState.copy(
          timeline =
            uiState.timeline +
              TimelineItem(
                id = "assistant-card",
                role = TimelineItemRole.Assistant,
                title = "助手",
                text = "你想去哪一个？",
                cardPayload =
                  OptionCardPayload(
                    cardId = "test-card",
                    label = "候选地点",
                    title = "你想去哪个点？",
                    options =
                      listOf(
                        OptionCardChoice(
                          id = "jingan-temple",
                          title = "静安寺卡片",
                          supportingText = "静安区 · 寺庙景点",
                          action = SelectionAction(promptText = "我选 静安寺", selectedDestinationName = "静安寺"),
                        ),
                      ),
                  ),
              ),
        )
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("静安寺卡片").assertIsDisplayed()
    composeTestRule.onNodeWithText("候选地点").assertIsDisplayed()
  }

  @Test
  fun autoFollow_resumesWhenReturningToBottom() {
    lateinit var listState: LazyListState
    var uiState by mutableStateOf(
      previewSnapshot(
        timeline =
          (1..18).map { index ->
            TimelineItem(
              id = "status-$index",
              role = TimelineItemRole.Status,
              title = "状态 $index",
              text = "历史片段 $index",
            )
          },
      ),
    )

    composeTestRule.setContent {
      listState = rememberLazyListState()
      MainScreen(uiState = uiState, listState = listState)
    }

    // Step 1: scroll away from bottom
    composeTestRule.runOnIdle {
      runBlocking { listState.scrollToItem(0) }
    }

    // Step 2: add item while not pinned — should NOT follow
    composeTestRule.runOnUiThread {
      uiState =
        uiState.copy(
          timeline =
            uiState.timeline +
              TimelineItem(
                id = "tool-while-up",
                role = TimelineItemRole.Tool,
                title = "search_destination",
                text = "用户上滑时不应跟随",
              ),
        )
    }

    composeTestRule.waitForIdle()
    composeTestRule.onAllNodesWithText("用户上滑时不应跟随").assertCountEquals(0)

    // Step 3: return to bottom
    composeTestRule.runOnIdle {
      runBlocking {
        listState.scrollToItem(uiState.timeline.size)
      }
    }

    // Step 4: add new item while pinned — should follow
    composeTestRule.runOnUiThread {
      uiState =
        uiState.copy(
          timeline =
            uiState.timeline +
              TimelineItem(
                id = "tool-resumed",
                role = TimelineItemRole.Tool,
                title = "emit_route_card",
                text = "回到底部后恢复跟随",
              ),
        )
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("回到底部后恢复跟随").assertIsDisplayed()
  }

  @Test
  fun streamingUpdate_doesNotInterruptWhenUserScrolledUp() {
    lateinit var listState: LazyListState
    var uiState by mutableStateOf(
      previewSnapshot(
        timeline =
          (1..18).map { index ->
            TimelineItem(
              id = "status-$index",
              role = TimelineItemRole.Status,
              title = "状态 $index",
              text = "历史片段 $index",
            )
          } + TimelineItem(
            id = "assistant-streaming",
            role = TimelineItemRole.Assistant,
            title = "助手",
            text = "正在",
            isStreaming = true,
          ),
      ),
    )

    composeTestRule.setContent {
      listState = rememberLazyListState()
      MainScreen(uiState = uiState, listState = listState)
    }

    composeTestRule.runOnIdle {
      runBlocking { listState.scrollToItem(0) }
    }

    composeTestRule.runOnUiThread {
      uiState =
        uiState.copy(
          timeline =
            uiState.timeline.dropLast(1) +
              TimelineItem(
                id = "assistant-streaming",
                role = TimelineItemRole.Assistant,
                title = "助手",
                text = "正在持续生成更长的回复内容",
                isStreaming = true,
              ),
        )
    }

    composeTestRule.waitForIdle()
    composeTestRule.onAllNodesWithText("正在持续生成更长的回复内容").assertCountEquals(0)
  }
}
