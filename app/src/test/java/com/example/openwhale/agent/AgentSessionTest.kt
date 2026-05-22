package com.example.openwhale.agent

import com.example.openwhale.agent.provider.ModelProvider
import com.example.openwhale.agent.provider.DeepSeekModelProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
  fun emitOptionCard_infersHotelPriceTemplateAndHydratesMissingCallbackPrompt() = runTest {
    val registry = DemoToolFactory.create()
    val currentState =
      SessionContextState(
        selectedDestination = DestinationCandidate(name = "静安寺", address = "南京西路1686号", area = "静安区", summary = "寺庙景点"),
      )

    val finalized =
      registry.finalize(
        registry.execute(
          registry.prepare(
            AgentToolCall(
              id = "call-infer-price-option",
              name = "emit_option_card",
              arguments =
                buildJsonObject {
                  put("title", JsonPrimitive("酒店预算想控制在多少？"))
                  put(
                    "options",
                    JsonArray(
                      listOf(
                        buildJsonObject {
                          put("id", JsonPrimitive("price-400"))
                          put("title", JsonPrimitive("¥400 以内"))
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
    val option = payload.options.single()
    assertEquals("酒店预算想控制在多少？", payload.title)
    assertEquals(400, option.action.maxPrice)
    assertEquals("酒店预算 400 元以内", option.action.promptText)
    assertEquals("¥400 以内", option.action.displayText)
    assertEquals(payload.cardId, option.action.sourceCardId)
  }

  @Test
  fun emitOptionCard_infersHotelDistanceTemplateAndHydratesMissingCallbackPrompt() = runTest {
    val registry = DemoToolFactory.create()
    val currentState =
      SessionContextState(
        selectedDestination = DestinationCandidate(name = "静安寺", address = "南京西路1686号", area = "静安区", summary = "寺庙景点"),
        hotelFilterContext = HotelFilterContext(maxPrice = 400),
      )

    val finalized =
      registry.finalize(
        registry.execute(
          registry.prepare(
            AgentToolCall(
              id = "call-infer-distance-option",
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
    val option = payload.options.single()
    assertEquals("离静安寺多近比较合适？", payload.title)
    assertEquals(2, option.action.maxDistanceKm)
    assertEquals("离目的地 2 公里内", option.action.promptText)
    assertEquals("2 公里内", option.action.displayText)
    assertEquals(payload.cardId, option.action.sourceCardId)
  }

  @Test
  fun optionCardValidator_rejectsDuplicateOptionIds() {
    val invalidCard =
      OptionCardPayload(
        cardId = "duplicate-card",
        label = "请选择",
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
        label = "请选择",
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
  fun scriptedDemoFlow_keepsHotelClarificationCardsRenderableWhenCallbackPromptIsMissing() = runTest {
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
                  AgentToolCall(
                    id = "call-5",
                    name = "emit_option_card",
                    arguments =
                      buildJsonObject {
                        put("title", JsonPrimitive("酒店预算想控制在多少？"))
                        put(
                          "options",
                          JsonArray(
                            listOf(
                              buildJsonObject {
                                put("id", JsonPrimitive("price-400"))
                                put("title", JsonPrimitive("¥400 以内"))
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
    session.sendUserMessage("查一下附近的酒店便宜点的")

    val priceCard = session.snapshot.value.timeline.last { it.cardPayload is OptionCardPayload }.cardPayload as OptionCardPayload
    assertEquals("酒店预算想控制在多少？", priceCard.title)
    assertEquals("酒店预算 400 元以内", priceCard.options.single().action.promptText)
    session.submitSelection(priceCard.options.single().action.copy(displayText = "¥400 以内"))

    val distanceCard = session.snapshot.value.timeline.last { it.cardPayload is OptionCardPayload }.cardPayload as OptionCardPayload
    assertEquals("离静安寺多近比较合适？", distanceCard.title)
    assertEquals("离目的地 2 公里内", distanceCard.options.single().action.promptText)
    session.submitSelection(distanceCard.options.single().action.copy(displayText = "2 公里内"))

    val snapshot = session.snapshot.value
    assertEquals(400, snapshot.sessionContextState.hotelFilterContext.maxPrice)
    assertEquals(2, snapshot.sessionContextState.hotelFilterContext.maxDistanceKm)
    assertTrue(snapshot.timeline.any { it.cardPayload is HotelListCardPayload })
    assertTrue(snapshot.errorMessage == null)
    assertTrue(snapshot.timeline.none { it.role == TimelineItemRole.Status && it.text.contains("option 卡片校验失败") })
  }

  @Test
  fun updateApiKey_persistsLocalOverrideAndUsesItForNextRequest() = runTest {
    val provider = FakeModelProvider(responses = listOf(ProviderResponse(text = "你好", toolCalls = emptyList(), finishReason = "stop")))
    val configStore = FakeLocalModelConfigStore()
    val session =
      AgentSession(
        modelProvider = provider,
        modelConfig = ModelConfig(providerId = "deepseek", modelId = "deepseek-chat", baseUrl = "https://api.deepseek.com", apiKey = "build-key", supportedModelIds = listOf("deepseek-v4-flash", "deepseek-chat")),
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

  @Test
  fun updateModelId_persistsOverrideAndUsesItForNextRequest() = runTest {
    val provider = FakeModelProvider(responses = listOf(ProviderResponse(text = "你好", toolCalls = emptyList(), finishReason = "stop")))
    val configStore = FakeLocalModelConfigStore()
    val session =
      AgentSession(
        modelProvider = provider,
        modelConfig =
          ModelConfig(
            providerId = "deepseek",
            modelId = "deepseek-chat",
            baseUrl = "https://api.deepseek.com",
            apiKey = "build-key",
            supportedModelIds = listOf("deepseek-v4-flash", "deepseek-chat"),
          ),
        toolRegistry = DemoToolFactory.create(),
        workflowPromptPackRepository = DefaultWorkflowPromptPackRepository(),
        initialWorkflowPackId = "navigation_hotel",
        localModelConfigStore = configStore,
      )

    session.updateModelId("deepseek-v4-flash")
    session.sendUserMessage("你好")

    assertEquals("deepseek-v4-flash", configStore.savedModelId)
    assertEquals("deepseek-v4-flash", provider.capturedModelIds.last())
    assertEquals("deepseek-v4-flash", session.snapshot.value.modelLabel)
  }

  @Test
  fun sendMessage_updatesSingleStreamingAssistantItem() = runTest {
    val provider = FakeModelProvider(
      responses = listOf(ProviderResponse(text = "流式回复完成", toolCalls = emptyList(), finishReason = "stop")),
      streamingDeltas = listOf(listOf("流式", "回复", "完成")),
    )
    val session = createSession(provider)

    session.sendUserMessage("你好")

    val assistantItems = session.snapshot.value.timeline.filter { it.role == TimelineItemRole.Assistant }
    assertEquals(1, assistantItems.size)
    assertEquals("流式回复完成", assistantItems.single().text)
    assertTrue(!assistantItems.single().isStreaming)
  }

  @Test
  fun sendMessage_surfacesToolFeedbackNearCardAndSplitsAssistantSegmentsByToolBoundary() = runTest {
    val provider = FakeModelProvider(
      responses = listOf(
        ProviderResponse(
          text = "我先帮你确认目的地。",
          reasoningContent = "先列出候选地点，再让用户选择。",
          toolCalls = listOf(
            AgentToolCall(
              id = "call-1",
              name = "search_destination",
              arguments = buildJsonObject { put("query", JsonPrimitive("静安寺")) },
            ),
            AgentToolCall(
              id = "call-2",
              name = "emit_option_card",
              arguments = buildJsonObject { put("card_kind", JsonPrimitive("destination_candidates")) },
            ),
          ),
          finishReason = "tool_calls",
        ),
      ),
    )
    val session = createSession(provider)

    session.sendUserMessage("导航到静安寺")

    val timeline = session.snapshot.value.timeline
    val firstAssistantIndex = timeline.indexOfFirst { it.role == TimelineItemRole.Assistant && it.text.contains("确认目的地") }
    val toolIndex = timeline.indexOfFirst { it.role == TimelineItemRole.Tool && it.title == "search_destination" }
    val cardAssistantIndex = timeline.indexOfLast { it.role == TimelineItemRole.Assistant && it.cardPayload is OptionCardPayload }

    assertTrue(firstAssistantIndex in 0 until toolIndex)
    assertTrue(toolIndex in 0 until cardAssistantIndex)
    assertTrue(timeline.any { it.role == TimelineItemRole.Thinking && it.text.contains("候选地点") })
    assertEquals(2, timeline.count { it.role == TimelineItemRole.Assistant })
  }

  @Test
  fun sendMessage_preservesReasoningReplayAndMapsVisibleThinkingTraceSeparately() = runTest {
    val provider = FakeModelProvider(
      responses = listOf(
        ProviderResponse(
          text = null,
          reasoningContent = "这是隐藏推理",
          toolCalls = listOf(
            AgentToolCall(
              id = "call-1",
              name = "search_destination",
              arguments = buildJsonObject { put("query", JsonPrimitive("静安寺")) },
            ),
          ),
          finishReason = "tool_calls",
        ),
        ProviderResponse(text = "我找到了静安寺候选地点。", toolCalls = emptyList(), finishReason = "stop"),
        ProviderResponse(text = "继续下一步。", toolCalls = emptyList(), finishReason = "stop"),
      ),
    )
    val session = createSession(provider)

    session.sendUserMessage("导航到静安寺")
    session.sendUserMessage("继续")

    val secondLoopMessages = provider.capturedRequestMessages[1]
    val replayedAssistant = secondLoopMessages.last { it.role == ProviderMessageRole.Assistant }
    assertEquals("这是隐藏推理", replayedAssistant.reasoningContent)
    assertEquals(1, replayedAssistant.toolCalls.size)

    val followUpMessages = provider.capturedRequestMessages[2]
    assertTrue(
      followUpMessages.any {
        it.role == ProviderMessageRole.Assistant && it.reasoningContent == "这是隐藏推理"
      },
    )
    assertTrue(
      session.snapshot.value.timeline.any {
        it.role == TimelineItemRole.Thinking && it.text.contains("隐藏推理")
      },
    )
    assertTrue(
      session.snapshot.value.timeline.none {
        it.role == TimelineItemRole.Assistant && it.text.contains("隐藏推理")
      },
    )
  }

  @Test
  fun deepSeekProvider_streamingRequestReplaysReasoningContentAndParsesHiddenChunks() = runTest {
    var capturedRequestBody = ""
    val streamingResponse =
      """
      data: {"choices":[{"delta":{"reasoning_content":"先想","tool_calls":[{"index":0,"id":"call-1","function":{"name":"search_destination","arguments":"{\"query\":\"静安寺\"}"}}]}}]}
      
      data: {"choices":[{"delta":{"reasoning_content":"一下","content":"可见回复"}}]}
      
      data: {"choices":[{"finish_reason":"tool_calls"}]}
      
      data: [DONE]
      """.trimIndent()
    val httpClient =
      OkHttpClient.Builder()
        .callTimeout(5, TimeUnit.SECONDS)
        .addInterceptor(
          Interceptor { chain ->
            val buffer = Buffer()
            chain.request().body?.writeTo(buffer)
            capturedRequestBody = buffer.readUtf8()
            Response.Builder()
              .request(chain.request())
              .protocol(Protocol.HTTP_1_1)
              .code(200)
              .message("OK")
              .body(streamingResponse.toResponseBody("text/event-stream".toMediaType()))
              .build()
          },
        ).build()
    val provider = DeepSeekModelProvider(httpClient = httpClient, json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true })

    val response =
      provider.complete(
        request =
          ProviderRequest(
            systemPrompt = "你是助手",
            modelConfig =
              ModelConfig(
                providerId = "deepseek",
                modelId = "deepseek-v4-flash",
                baseUrl = "https://api.deepseek.com",
                apiKey = "test-key",
                supportedModelIds = listOf("deepseek-v4-flash"),
                streamingEnabled = true,
              ),
            messages =
              listOf(
                ProviderConversationMessage(role = ProviderMessageRole.User, content = "导航到静安寺"),
                ProviderConversationMessage(
                  role = ProviderMessageRole.Assistant,
                  content = null,
                  reasoningContent = "历史推理",
                  toolCalls =
                    listOf(
                      AgentToolCall(
                        id = "call-previous",
                        name = "search_destination",
                        arguments = buildJsonObject { put("query", JsonPrimitive("静安寺")) },
                      ),
                    ),
                ),
              ),
            tools = emptyList(),
          ),
        onStreamEvent = {},
      )

    assertTrue(capturedRequestBody.contains("\"reasoning_content\":\"历史推理\""))
    assertTrue(capturedRequestBody.contains("\"thinking\":{\"type\":\"enabled\"}"))
    assertEquals("先想一下", response.reasoningContent)
    assertEquals("可见回复", response.text)
    assertEquals("tool_calls", response.finishReason)
    assertEquals(1, response.toolCalls.size)
    assertEquals("search_destination", response.toolCalls.single().name)
  }

  @Test
  fun deepSeekProvider_nonThinkingModelDisablesThinkingAndSkipsReasoningReplay() = runTest {
    var capturedRequestBody = ""
    val httpClient =
      OkHttpClient.Builder()
        .callTimeout(5, TimeUnit.SECONDS)
        .addInterceptor(
          Interceptor { chain ->
            val buffer = Buffer()
            chain.request().body?.writeTo(buffer)
            capturedRequestBody = buffer.readUtf8()
            Response.Builder()
              .request(chain.request())
              .protocol(Protocol.HTTP_1_1)
              .code(200)
              .message("OK")
              .body("{\"choices\":[{\"message\":{\"content\":\"普通回复\"},\"finish_reason\":\"stop\"}]}".toResponseBody("application/json".toMediaType()))
              .build()
          },
        ).build()
    val provider = DeepSeekModelProvider(httpClient = httpClient, json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true })

    provider.complete(
      request =
        ProviderRequest(
          systemPrompt = "你是助手",
          modelConfig =
            ModelConfig(
              providerId = "deepseek",
              modelId = "deepseek-chat",
              baseUrl = "https://api.deepseek.com",
              apiKey = "test-key",
              supportedModelIds = listOf("deepseek-chat"),
              streamingEnabled = false,
            ),
          messages =
            listOf(
              ProviderConversationMessage(role = ProviderMessageRole.User, content = "导航到静安寺"),
              ProviderConversationMessage(
                role = ProviderMessageRole.Assistant,
                content = null,
                reasoningContent = "不该被回放",
                toolCalls =
                  listOf(
                    AgentToolCall(
                      id = "call-previous",
                      name = "search_destination",
                      arguments = buildJsonObject { put("query", JsonPrimitive("静安寺")) },
                    ),
                  ),
              ),
            ),
          tools = emptyList(),
        ),
      onStreamEvent = {},
    )

    assertTrue(capturedRequestBody.contains("\"thinking\":{\"type\":\"disabled\"}"))
    assertTrue(!capturedRequestBody.contains("不该被回放"))
  }

  @Test
  fun deepSeekProvider_recoversToolArgumentsWhenModelAddsTrailingBrace() = runTest {
    val httpClient =
      OkHttpClient.Builder()
        .callTimeout(5, TimeUnit.SECONDS)
        .addInterceptor(
          Interceptor { chain ->
            Response.Builder()
              .request(chain.request())
              .protocol(Protocol.HTTP_1_1)
              .code(200)
              .message("OK")
              .body(
                """
                {"choices":[{"message":{"tool_calls":[{"id":"call-1","type":"function","function":{"name":"emit_option_card","arguments":"{\"title\":\"你想去哪一个？\",\"options\":[{\"id\":\"jingan_station\",\"title\":\"静安寺地铁站\",\"selection\":{\"selected_destination_name\":\"静安寺地铁站\"}}]}}"}}]},"finish_reason":"tool_calls"}]}
                """.trimIndent().toResponseBody("application/json".toMediaType()),
              ).build()
          },
        ).build()
    val provider = DeepSeekModelProvider(httpClient = httpClient, json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true })

    val response =
      provider.complete(
        request =
          ProviderRequest(
            systemPrompt = "你是助手",
            modelConfig =
              ModelConfig(
                providerId = "deepseek",
                modelId = "deepseek-chat",
                baseUrl = "https://api.deepseek.com",
                apiKey = "test-key",
                supportedModelIds = listOf("deepseek-chat"),
                streamingEnabled = false,
              ),
            messages = listOf(ProviderConversationMessage(role = ProviderMessageRole.User, content = "导航到静安寺")),
            tools = emptyList(),
          ),
        onStreamEvent = {},
      )

    assertEquals(1, response.toolCalls.size)
    assertEquals("emit_option_card", response.toolCalls.single().name)
    assertEquals("你想去哪一个？", response.toolCalls.single().arguments["title"]?.toString()?.trim('"'))
  }

  @Test
  fun sendMessage_stopsThinkingSpinnerWhenRequestFailsAfterThinkingStarted() = runTest {
    val provider =
      object : ModelProvider {
        override suspend fun complete(
          request: ProviderRequest,
          onStreamEvent: suspend (ProviderStreamEvent) -> Unit,
        ): ProviderResponse {
          onStreamEvent(ProviderStreamEvent.ThinkingDelta("先确认地点，再生成选项卡。"))
          throw IllegalStateException("模拟工具参数解析失败")
        }
      }

    val session = createSession(provider)
    session.sendUserMessage("导航到静安寺")

    val snapshot = session.snapshot.value
    val thinkingItem = snapshot.timeline.last { it.role == TimelineItemRole.Thinking }
    assertFalse(thinkingItem.isStreaming)
    assertTrue(snapshot.timeline.any { it.role == TimelineItemRole.Status && it.title == "错误" })
    assertFalse(snapshot.isSending)
  }

  private fun createSession(provider: ModelProvider): AgentSession {
    return AgentSession(
      modelProvider = provider,
      modelConfig = ModelConfig(providerId = "deepseek", modelId = "deepseek-chat", baseUrl = "https://api.deepseek.com", apiKey = "test-key", supportedModelIds = listOf("deepseek-v4-flash", "deepseek-chat")),
      toolRegistry = DemoToolFactory.create(),
      workflowPromptPackRepository = DefaultWorkflowPromptPackRepository(),
      initialWorkflowPackId = "navigation_hotel",
    )
  }
}

private class FakeModelProvider(
  private val responses: List<ProviderResponse>,
  private val streamingDeltas: List<List<String>> = emptyList(),
) : ModelProvider {
  val capturedSystemPrompts = mutableListOf<String>()
  val capturedUserMessages = mutableListOf<String>()
  val capturedApiKeys = mutableListOf<String>()
  val capturedModelIds = mutableListOf<String>()
  val capturedRequestMessages = mutableListOf<List<ProviderConversationMessage>>()
  private var index = 0

  override suspend fun complete(
    request: ProviderRequest,
    onStreamEvent: suspend (ProviderStreamEvent) -> Unit,
  ): ProviderResponse {
    capturedSystemPrompts += request.systemPrompt
    capturedUserMessages += request.messages.lastOrNull { it.role == ProviderMessageRole.User }?.content.orEmpty()
    capturedApiKeys += request.modelConfig.apiKey
    capturedModelIds += request.modelConfig.modelId
    capturedRequestMessages += request.messages.map { it.copy(toolCalls = it.toolCalls.toList()) }
    streamingDeltas.getOrNull(index)?.forEach { onStreamEvent(ProviderStreamEvent.TextDelta(it)) }
    return responses.getOrElse(index++) { ProviderResponse(text = "done", toolCalls = emptyList(), finishReason = "stop") }
  }
}

private class FakeLocalModelConfigStore : LocalModelConfigStore {
  var savedApiKey: String? = null
  var savedModelId: String? = null

  override fun getApiKeyOverride(): String? = savedApiKey

  override fun getModelIdOverride(): String? = savedModelId

  override fun saveApiKeyOverride(apiKey: String?) {
    savedApiKey = apiKey
  }

  override fun saveModelIdOverride(modelId: String?) {
    savedModelId = modelId
  }
}
