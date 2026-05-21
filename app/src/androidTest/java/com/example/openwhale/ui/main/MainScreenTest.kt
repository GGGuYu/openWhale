package com.example.openwhale.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.openwhale.agent.AgentSessionSnapshot
import com.example.openwhale.agent.HotelFilterContext
import com.example.openwhale.agent.OptionCardChoice
import com.example.openwhale.agent.OptionCardPayload
import com.example.openwhale.agent.SelectionAction
import com.example.openwhale.agent.SessionContextState
import com.example.openwhale.agent.TimelineItem
import com.example.openwhale.agent.TimelineItemRole
import com.example.openwhale.agent.WorkflowPromptPack
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    composeTestRule.setContent {
      MainScreen(
        uiState =
          AgentSessionSnapshot(
            timeline =
              listOf(
                TimelineItem(id = "status-1", role = TimelineItemRole.Status, title = "工作流", text = "当前工作流：导航找酒店 Demo。"),
                TimelineItem(id = "user-1", role = TimelineItemRole.User, title = "你", text = "导航到静安寺"),
                TimelineItem(
                  id = "assistant-1",
                  role = TimelineItemRole.Assistant,
                  title = "助手",
                  text = "你想去哪一个？",
                  cardPayload =
                    OptionCardPayload(
                      cardId = "preview-option-card",
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
            availableWorkflowPacks =
              listOf(
                WorkflowPromptPack(id = "navigation_hotel", title = "导航找酒店 Demo", systemPrompt = "", starterPrompt = ""),
              ),
            selectedWorkflowPackId = "navigation_hotel",
            providerLabel = "deepseek",
            modelLabel = "deepseek-chat",
            sessionContextState = SessionContextState(hotelFilterContext = HotelFilterContext()),
            isSending = false,
          ),
      )
    }
  }

  @Test
  fun optionCard_isRendered() {
    composeTestRule.onNodeWithText("你想去哪个点？").assertIsDisplayed()
    composeTestRule.onNodeWithText("静安寺").assertIsDisplayed()
  }

  @Test
  fun compactShell_isRendered() {
    composeTestRule.onNodeWithText("OpenWhale").assertIsDisplayed()
    composeTestRule.onNodeWithText("历史").assertIsDisplayed()
    composeTestRule.onNodeWithText("设置").assertIsDisplayed()
  }

  @Test
  fun messageAvatar_isRendered() {
    composeTestRule.onNodeWithContentDescription("助手头像").assertIsDisplayed()
  }

  @Test
  fun settingsSheet_canBeOpened() {
    composeTestRule.onNodeWithText("设置").performClick()
    composeTestRule.onNodeWithText("DeepSeek Key").assertIsDisplayed()
    composeTestRule.onNodeWithText("保存本地 Key").assertIsDisplayed()
  }

  @Test
  fun historySheet_canBeOpened() {
    composeTestRule.onNodeWithText("历史").performClick()
    composeTestRule.onNodeWithText("最近对话").assertIsDisplayed()
    composeTestRule.onNodeWithText("导航到静安寺").assertIsDisplayed()
  }
}
