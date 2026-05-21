package com.example.openwhale.agent

import com.example.openwhale.agent.provider.ModelProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSessionTest {
  @Test
  fun sendMessage_runsToolLoopAndPersistsDestinationState() = runTest {
    val provider =
      FakeModelProvider(
        responses =
          listOf(
            ProviderResponse(
              text = null,
              toolCalls =
                listOf(
                  AgentToolCall(id = "call-1", name = "search_destination", arguments = buildJsonObject { put("query", JsonPrimitive("静安寺")) }),
                  AgentToolCall(
                    id = "call-2",
                    name = "emit_option_card",
                    arguments =
                      buildJsonObject {
                        put("title", JsonPrimitive("你想去哪一个？"))
                        put("description", JsonPrimitive("先确认目的地，我再给你路线。"))
                        put(
                          "options",
                          JsonArray(
                            listOf(
                              buildJsonObject {
                                put("id", JsonPrimitive("jingan-temple"))
                                put("title", JsonPrimitive("静安寺"))
                                put("supporting_text", JsonPrimitive("静安区 · 寺庙景点，适合直接导航"))
                                put("prompt_text", JsonPrimitive("我选 静安寺"))
                                put("selected_destination_name", JsonPrimitive("静安寺"))
                              },
                            ),
                          ),
                        )
                      },
                  ),
                ),
              finishReason = "tool_calls",
            ),
          ),
      )
    val session = createSession(provider)

    session.sendUserMessage("导航到静安寺")

    val snapshot = session.snapshot.value
    assertEquals(3, snapshot.sessionContextState.destinationCandidates.size)
    assertTrue(snapshot.timeline.any { it.role == TimelineItemRole.Tool && it.title == "search_destination" })
    assertTrue(snapshot.timeline.any { it.role == TimelineItemRole.Tool && it.title == "emit_option_card" })
    assertEquals(1, snapshot.timeline.count { it.cardPayload is OptionCardPayload })
  }

  @Test
  fun switchWorkflowPack_changesSystemPromptUsedByLoop() = runTest {
    val provider = FakeModelProvider(responses = listOf(ProviderResponse(text = "已切到通用模式。", toolCalls = emptyList(), finishReason = "stop")))
    val session = createSession(provider)

    session.switchWorkflowPack("general_chat")
    session.sendUserMessage("你好")

    assertTrue(provider.capturedSystemPrompts.last().contains("移动端助手"))
    assertEquals("general_chat", session.snapshot.value.selectedWorkflowPackId)
  }

  @Test
  fun sessionState_keepsSelectedDestinationAndHotelFiltersAcrossTurns() = runTest {
    val provider =
      FakeModelProvider(
        responses =
          listOf(
            ProviderResponse(
              text = null,
              toolCalls =
                listOf(
                  AgentToolCall(id = "call-1", name = "search_destination", arguments = buildJsonObject { put("query", JsonPrimitive("静安寺")) }),
                  AgentToolCall(id = "call-2", name = "emit_option_card", arguments = buildJsonObject { put("card_kind", JsonPrimitive("destination_candidates")) }),
                ),
              finishReason = "tool_calls",
            ),
            ProviderResponse(
              text = null,
              toolCalls =
                listOf(
                  AgentToolCall(id = "call-3", name = "get_route_options", arguments = buildJsonObject { put("destination_name", JsonPrimitive("静安寺")) }),
                  AgentToolCall(id = "call-4", name = "emit_route_card", arguments = buildJsonObject {}),
                ),
              finishReason = "tool_calls",
            ),
            ProviderResponse(
              text = null,
              toolCalls =
                listOf(
                  AgentToolCall(id = "call-5", name = "search_ctrip_hotels", arguments = buildJsonObject { put("max_price", JsonPrimitive(400)); put("max_distance_km", JsonPrimitive(2)) }),
                  AgentToolCall(id = "call-6", name = "search_meituan_hotels", arguments = buildJsonObject { put("max_price", JsonPrimitive(400)); put("max_distance_km", JsonPrimitive(2)) }),
                  AgentToolCall(id = "call-7", name = "emit_hotel_list_card", arguments = buildJsonObject { put("ranking", JsonPrimitive("balanced")) }),
                ),
              finishReason = "tool_calls",
            ),
          ),
      )
    val session = createSession(provider)

    session.sendUserMessage("导航到静安寺")
    session.sendUserMessage("选静安寺")
    session.sendUserMessage("这个附近的酒店帮我找个便宜的，预算 400 以内，2 公里内")

    val snapshot = session.snapshot.value
    assertEquals("静安寺", snapshot.sessionContextState.selectedDestination?.name)
    assertEquals(400, snapshot.sessionContextState.hotelFilterContext.maxPrice)
    assertEquals(2, snapshot.sessionContextState.hotelFilterContext.maxDistanceKm)
    assertTrue(snapshot.timeline.any { it.title == "search_ctrip_hotels" })
    assertTrue(snapshot.timeline.any { it.title == "search_meituan_hotels" })
    assertTrue(snapshot.timeline.any { it.title == "emit_hotel_list_card" })
  }

  @Test
  fun sendMessage_attachesDestinationOptionCardToAssistantTimeline() = runTest {
    val provider =
      FakeModelProvider(
        responses =
          listOf(
            ProviderResponse(
              text = null,
              toolCalls =
                listOf(
                  AgentToolCall(id = "call-1", name = "search_destination", arguments = buildJsonObject { put("query", JsonPrimitive("静安寺")) }),
                  AgentToolCall(
                    id = "call-2",
                    name = "emit_option_card",
                    arguments =
                      buildJsonObject {
                        put("card_kind", JsonPrimitive("destination_candidates"))
                        put("title", JsonPrimitive("你想去哪个点？"))
                      },
                  ),
                ),
              finishReason = "tool_calls",
            ),
          ),
      )

    val session = createSession(provider)

    session.sendUserMessage("导航到静安寺")

    val assistantItem = session.snapshot.value.timeline.last { it.role == TimelineItemRole.Assistant }
    assertNotNull(assistantItem.cardPayload)
    assertTrue(assistantItem.cardPayload is OptionCardPayload)
  }

  @Test
  fun submitSelection_updatesStateAndContinuesLoop() = runTest {
    val provider =
      FakeModelProvider(
        responses =
          listOf(
            ProviderResponse(
              text = null,
              toolCalls =
                listOf(
                  AgentToolCall(id = "call-1", name = "get_route_options", arguments = buildJsonObject { put("destination_name", JsonPrimitive("静安寺")) }),
                  AgentToolCall(id = "call-2", name = "emit_route_card", arguments = buildJsonObject {}),
                ),
              finishReason = "tool_calls",
            ),
          ),
      )

    val session = createSession(provider)

    session.switchWorkflowPack("navigation_hotel")
    session.submitSelection(SelectionAction(promptText = "我选 静安寺", selectedDestinationName = "静安寺", displayText = "静安寺", sourceCardId = "card-1"))

    val snapshot = session.snapshot.value
    assertEquals("我选 静安寺", provider.capturedUserMessages.last())
    assertEquals("静安寺", snapshot.sessionContextState.selectedDestination?.name)
    assertTrue(snapshot.timeline.any { it.cardPayload is RouteCardPayload })
    assertTrue("card-1" in snapshot.sessionContextState.consumedCallbackCardIds)
  }

  @Test
  fun scriptedDemoFlow_completesFromDestinationToHotelCard() = runTest {
    val provider =
      FakeModelProvider(
        responses =
          listOf(
            ProviderResponse(
              text = null,
              toolCalls =
                listOf(
                  AgentToolCall(id = "call-1", name = "search_destination", arguments = buildJsonObject { put("query", JsonPrimitive("静安寺")) }),
                  AgentToolCall(
                    id = "call-2",
                    name = "emit_option_card",
                    arguments =
                      buildJsonObject {
                        put("card_kind", JsonPrimitive("destination_candidates"))
                        put("title", JsonPrimitive("你想去哪一个？"))
                      },
                  ),
                ),
              finishReason = "tool_calls",
            ),
            ProviderResponse(
              text = null,
              toolCalls =
                listOf(
                  AgentToolCall(id = "call-3", name = "get_route_options", arguments = buildJsonObject { put("destination_name", JsonPrimitive("静安寺")) }),
                  AgentToolCall(id = "call-4", name = "emit_route_card", arguments = buildJsonObject {}),
                ),
              finishReason = "tool_calls",
            ),
            ProviderResponse(
              text = null,
              toolCalls =
                listOf(
                  AgentToolCall(
                    id = "call-5",
                    name = "emit_option_card",
                    arguments =
                      buildJsonObject {
                        put("title", JsonPrimitive("酒店预算想控制在多少？"))
                        put("description", JsonPrimitive("我会按静安寺附近筛便宜酒店。"))
                        put(
                          "options",
                          JsonArray(
                            listOf(
                              buildJsonObject {
                                put("id", JsonPrimitive("price-300"))
                                put("title", JsonPrimitive("¥300 以内"))
                                put("prompt_text", JsonPrimitive("酒店预算 300 元以内"))
                                put("max_price", JsonPrimitive(300))
                              },
                              buildJsonObject {
                                put("id", JsonPrimitive("price-400"))
                                put("title", JsonPrimitive("¥400 以内"))
                                put("prompt_text", JsonPrimitive("酒店预算 400 元以内"))
                                put("max_price", JsonPrimitive(400))
                              },
                            ),
                          ),
                        )
                      },
                  ),
                ),
              finishReason = "tool_calls",
            ),
            ProviderResponse(
              text = null,
              toolCalls =
                listOf(
                  AgentToolCall(
                    id = "call-6",
                    name = "emit_option_card",
                    arguments =
                      buildJsonObject {
                        put("title", JsonPrimitive("离静安寺多近比较合适？"))
                        put(
                          "options",
                          JsonArray(
                            listOf(
                              buildJsonObject {
                                put("id", JsonPrimitive("distance-2"))
                                put("title", JsonPrimitive("2 公里内"))
                                put("prompt_text", JsonPrimitive("离目的地 2 公里内"))
                                put("max_distance_km", JsonPrimitive(2))
                              },
                            ),
                          ),
                        )
                      },
                  ),
                ),
              finishReason = "tool_calls",
            ),
            ProviderResponse(
              text = null,
              toolCalls =
                listOf(
                  AgentToolCall(id = "call-7", name = "search_ctrip_hotels", arguments = buildJsonObject { put("max_price", JsonPrimitive(400)); put("max_distance_km", JsonPrimitive(2)) }),
                  AgentToolCall(id = "call-8", name = "search_meituan_hotels", arguments = buildJsonObject { put("max_price", JsonPrimitive(400)); put("max_distance_km", JsonPrimitive(2)) }),
                  AgentToolCall(id = "call-9", name = "emit_hotel_list_card", arguments = buildJsonObject { put("ranking", JsonPrimitive("balanced")) }),
                ),
              finishReason = "tool_calls",
            ),
          ),
      )

    val session = createSession(provider)

    session.sendUserMessage("导航到静安寺")
    val destinationCard = session.snapshot.value.timeline.last { it.cardPayload is OptionCardPayload }.cardPayload as OptionCardPayload
    session.submitSelection(destinationCard.options.first().action.copy(displayText = "静安寺"))
    session.sendUserMessage("这个附近的酒店帮我找个便宜的")
    val priceCard = session.snapshot.value.timeline.last { it.cardPayload is OptionCardPayload }.cardPayload as OptionCardPayload
    session.submitSelection(priceCard.options.first { it.title.contains("400") }.action.copy(displayText = "¥400 以内"))
    val distanceCard = session.snapshot.value.timeline.last { it.cardPayload is OptionCardPayload }.cardPayload as OptionCardPayload
    session.submitSelection(distanceCard.options.first { it.title.contains("2 公里") }.action.copy(displayText = "2 公里内"))

    val snapshot = session.snapshot.value
    assertEquals("静安寺", snapshot.sessionContextState.selectedDestination?.name)
    assertEquals(400, snapshot.sessionContextState.hotelFilterContext.maxPrice)
    assertEquals(2, snapshot.sessionContextState.hotelFilterContext.maxDistanceKm)
    assertTrue(snapshot.timeline.any { it.cardPayload is RouteCardPayload })
    assertTrue(snapshot.timeline.any { (it.cardPayload as? OptionCardPayload)?.title?.contains("预算") == true })
    assertTrue(snapshot.timeline.any { (it.cardPayload as? OptionCardPayload)?.title?.contains("多近") == true })
    assertTrue(snapshot.timeline.any { it.cardPayload is HotelListCardPayload })
    assertEquals(3, snapshot.timeline.count { it.cardPayload is OptionCardPayload })
  }

  @Test
  fun sendMessage_rejectsToolBatchWhenCardToolIsNotLast() = runTest {
    val provider =
      FakeModelProvider(
        responses =
          listOf(
            ProviderResponse(
              text = null,
              toolCalls =
                listOf(
                  AgentToolCall(id = "call-1", name = "emit_option_card", arguments = buildJsonObject { put("card_kind", JsonPrimitive("destination_candidates")) }),
                  AgentToolCall(id = "call-2", name = "search_destination", arguments = buildJsonObject { put("query", JsonPrimitive("静安寺")) }),
                ),
              finishReason = "tool_calls",
            ),
          ),
      )
    val session = createSession(provider)

    session.sendUserMessage("导航到静安寺")

    val snapshot = session.snapshot.value
    assertTrue(snapshot.errorMessage?.contains("数据工具必须先执行") == true)
    assertTrue(snapshot.timeline.any { it.role == TimelineItemRole.Status && it.title == "错误" })
    assertEquals(0, snapshot.timeline.count { it.cardPayload is OptionCardPayload })
  }

  @Test
  fun hotelCard_canRenderWhenOnlyOnePlatformHasMatches() {
    val state =
      SessionContextState(
        selectedDestination = DestinationCandidate(name = "静安寺", address = "南京西路1686号", area = "静安区", summary = "寺庙景点"),
        hotelFilterContext = HotelFilterContext(maxPrice = 320, maxDistanceKm = 2),
        hotelResultsByPlatform =
          mapOf(
            "携程" to emptyList(),
            "美团" to listOf(HotelResultItem(name = "静安精选酒店", platform = "美团", price = 299, distanceKm = 0.6, summary = "便宜且近", actionLabel = "打开美团 · 精选", actionUri = "https://i.meituan.com/hotel/?entry=test")),
          ),
      )

    val card = DemoCardPlanner.hotelCard(state, ranking = HotelListRanking.Cheapest)

    assertNotNull(card)
    assertEquals(1, card?.hotels?.size)
    assertTrue(card?.platformStatuses?.any { it.platform == "携程" && !it.hasMatches && it.statusText.contains("无匹配") } == true)
  }

  @Test
  fun submitSelection_ignoresConsumedCallbackCard() = runTest {
    val provider = FakeModelProvider(responses = listOf(ProviderResponse(text = "done", toolCalls = emptyList(), finishReason = "stop")))
    val session = createSession(provider)
    val action = SelectionAction(promptText = "酒店预算 400 元以内", displayText = "¥400 以内", maxPrice = 400, sourceCardId = "price-card")

    session.submitSelection(action)
    session.submitSelection(action)

    assertEquals(1, provider.capturedUserMessages.size)
    assertTrue("price-card" in session.snapshot.value.sessionContextState.consumedCallbackCardIds)
  }

  @Test
  fun emitOptionCard_acceptsGenericPayloadAndCarriesStructuredSelection() = runTest {
    val registry = DemoToolFactory.create()
    val currentState = SessionContextState()

    val finalized =
      registry.finalize(
        registry.execute(
          registry.prepare(
            AgentToolCall(
              id = "call-generic-option",
              name = "emit_option_card",
              arguments =
                buildJsonObject {
                  put("title", JsonPrimitive("酒店预算想控制在多少？"))
                  put(
                    "options",
                    JsonArray(
                      listOf(
                        buildJsonObject {
                          put("id", JsonPrimitive("budget-400"))
                          put("title", JsonPrimitive("¥400 以内"))
                          put("prompt_text", JsonPrimitive("酒店预算 400 元以内"))
                          put("display_text", JsonPrimitive("¥400 以内"))
                          put("max_price", JsonPrimitive(400))
                        },
                      ),
                    ),
                  )
                },
            ),
          ),
          currentState = currentState,
        ),
      )

    val payload = finalized.result.cardPayload as OptionCardPayload
    assertEquals("酒店预算想控制在多少？", payload.title)
    assertEquals(400, payload.options.first().action.maxPrice)
    assertEquals("¥400 以内", payload.options.first().action.displayText)
    assertEquals(payload.cardId, payload.options.first().action.sourceCardId)
  }

  @Test
  fun emitOptionCard_acceptsCallbackPromptAliasAndNestedSelection() = runTest {
    val registry = DemoToolFactory.create()

    val finalized =
      registry.finalize(
        registry.execute(
          registry.prepare(
            AgentToolCall(
              id = "call-nested-option",
              name = "emit_option_card",
              arguments =
                buildJsonObject {
                  put("title", JsonPrimitive("你想去哪一个？"))
                  put(
                    "options",
                    JsonArray(
                      listOf(
                        buildJsonObject {
                          put("id", JsonPrimitive("jingan-temple"))
                          put("title", JsonPrimitive("静安寺"))
                          put("callback_prompt_text", JsonPrimitive("我选 静安寺"))
                          put(
                            "selection",
                            buildJsonObject {
                              put("selected_destination_name", JsonPrimitive("静安寺"))
                            },
                          )
                        },
                      ),
                    ),
                  )
                },
            ),
          ),
          currentState = SessionContextState(),
        ),
      )

    val payload = finalized.result.cardPayload as OptionCardPayload
    assertEquals("我选 静安寺", payload.options.first().action.promptText)
    assertEquals("静安寺", payload.options.first().action.selectedDestinationName)
    assertEquals(payload.cardId, payload.options.first().action.sourceCardId)
  }

  @Test
  fun optionCardValidator_rejectsDuplicateOptionIds() {
    val invalidCard =
      OptionCardPayload(
        cardId = "duplicate-card",
        title = "请选择",
        options =
          listOf(
            OptionCardChoice(id = "same", title = "A", action = SelectionAction(promptText = "选 A", displayText = "A")),
            OptionCardChoice(id = "same", title = "B", action = SelectionAction(promptText = "选 B", displayText = "B")),
          ),
      )

    assertEquals(null, AgentCardValidator.validate(invalidCard))
  }

  @Test
  fun optionCardValidator_rejectsMismatchedSourceCardId() {
    val invalidCard =
      OptionCardPayload(
        cardId = "option-card-1",
        title = "请选择",
        options =
          listOf(
            OptionCardChoice(
              id = "destination-a",
              title = "静安寺",
              action = SelectionAction(promptText = "我选 静安寺", displayText = "静安寺", sourceCardId = "another-card"),
            ),
          ),
        allowCustomInput = true,
        customInputHint = "也可以继续输入",
      )

    assertEquals(null, AgentCardValidator.validate(invalidCard))
  }

  @Test
  fun updateApiKey_persistsLocalOverrideAndUsesItForNextRequest() = runTest {
    val provider = FakeModelProvider(responses = listOf(ProviderResponse(text = "你好", toolCalls = emptyList(), finishReason = "stop")))
    val configStore = FakeLocalModelConfigStore()
    val session =
      AgentSession(
        modelProvider = provider,
        modelConfig = ModelConfig(providerId = "deepseek", modelId = "deepseek-chat", baseUrl = "https://api.deepseek.com", apiKey = "build-key"),
        toolRegistry = DemoToolFactory.create(),
        workflowPromptPackRepository = DefaultWorkflowPromptPackRepository(),
        initialWorkflowPackId = "navigation_hotel",
        localModelConfigStore = configStore,
      )

    session.updateApiKey("local-key-1234")
    session.sendUserMessage("你好")

    assertEquals("local-key-1234", configStore.savedApiKey)
    assertEquals("local-key-1234", provider.capturedApiKeys.last())
    assertTrue(session.snapshot.value.hasLocalApiKeyOverride)
    assertTrue(session.snapshot.value.apiKeyConfigured)
  }

  private fun createSession(provider: ModelProvider): AgentSession {
    return AgentSession(
      modelProvider = provider,
      modelConfig = ModelConfig(providerId = "deepseek", modelId = "deepseek-chat", baseUrl = "https://api.deepseek.com", apiKey = "test-key"),
      toolRegistry = DemoToolFactory.create(),
      workflowPromptPackRepository = DefaultWorkflowPromptPackRepository(),
      initialWorkflowPackId = "navigation_hotel",
    )
  }
}

private class FakeModelProvider(private val responses: List<ProviderResponse>) : ModelProvider {
  val capturedSystemPrompts = mutableListOf<String>()
  val capturedUserMessages = mutableListOf<String>()
  val capturedApiKeys = mutableListOf<String>()
  private var index = 0

  override suspend fun complete(request: ProviderRequest): ProviderResponse {
    capturedSystemPrompts += request.systemPrompt
    capturedUserMessages += request.messages.lastOrNull { it.role == ProviderMessageRole.User }?.content.orEmpty()
    capturedApiKeys += request.modelConfig.apiKey
    return responses.getOrElse(index++) { ProviderResponse(text = "done", toolCalls = emptyList(), finishReason = "stop") }
  }
}

private class FakeLocalModelConfigStore : LocalModelConfigStore {
  var savedApiKey: String? = null

  override fun getApiKeyOverride(): String? = savedApiKey

  override fun saveApiKeyOverride(apiKey: String?) {
    savedApiKey = apiKey
  }
}
