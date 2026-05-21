package com.example.openwhale.agent

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

object DemoToolFactory {
  private val json = Json { prettyPrint = true; encodeDefaults = true }

  fun create(debugLogger: AgentDebugLogger = NoopAgentDebugLogger): AgentToolRegistry {
    val tools =
      listOf(
        searchDestinationTool(),
        routeTool(),
        ctripHotelTool(),
        meituanHotelTool(),
        emitOptionCardTool(),
        emitRouteCardTool(),
        emitHotelListCardTool(),
      )
    return AgentToolRegistry(tools.associateBy { it.definition.name }, debugLogger = debugLogger)
  }

  private fun searchDestinationTool(): RegisteredAgentTool {
    return RegisteredAgentTool(
      definition =
        AgentToolDefinition(
          name = "search_destination",
          description = "查询目的地候选数据。只返回候选地点，不直接展示卡片。若需要让用户选择，请随后调用 emit_option_card 并传 card_kind=destination_candidates。",
          parametersSchema =
            buildJsonObject {
              put("type", JsonPrimitive("object"))
              put(
                "properties",
                buildJsonObject {
                  put("query", buildJsonObject { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("用户要去的地名或地点关键词")) })
                },
              )
              put("required", JsonArray(listOf(JsonPrimitive("query"))))
            },
        ),
      executor = AgentToolExecutor { arguments, currentState ->
        val query = arguments["query"]?.jsonPrimitive?.content?.trim().orEmpty()
        val candidates = destinationCandidatesFor(query)
        val payload =
          buildJsonObject {
            put("query", JsonPrimitive(query))
            put(
              "candidates",
              buildJsonArray {
                candidates.forEach { candidate ->
                  add(
                    buildJsonObject {
                      put("name", JsonPrimitive(candidate.name))
                      put("address", JsonPrimitive(candidate.address))
                      put("area", JsonPrimitive(candidate.area))
                      put("summary", JsonPrimitive(candidate.summary))
                    },
                  )
                }
              },
            )
          }
        ToolExecutionResult(
          displayText = buildString {
            append("search_destination 已返回 ")
            append(candidates.size)
            append(" 个候选地点：")
            append(candidates.joinToString("、") { it.name })
          },
          modelPayload = json.encodeToString(payload),
          nextState =
            currentState.copy(
              destinationCandidates = candidates,
              selectedDestination = null,
              latestRouteCard = null,
              hotelFilterContext = HotelFilterContext(),
              hotelResultsByPlatform = emptyMap(),
            ),
        )
      },
    )
  }

  private fun routeTool(): RegisteredAgentTool {
    return RegisteredAgentTool(
      definition =
        AgentToolDefinition(
          name = "get_route_options",
          description = "查询已确认目的地的路线数据。只返回路线数据并写入会话状态，不直接展示卡片。若要给用户展示路线，请随后调用 emit_route_card。",
          parametersSchema =
            buildJsonObject {
              put("type", JsonPrimitive("object"))
              put(
                "properties",
                buildJsonObject {
                  put("destination_name", buildJsonObject { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("最终确认的目的地名称")) })
                },
              )
              put("required", JsonArray(listOf(JsonPrimitive("destination_name"))))
            },
        ),
      executor = AgentToolExecutor { arguments, currentState ->
        val destinationName = arguments["destination_name"]?.jsonPrimitive?.content?.trim().orEmpty()
        val destination = resolveDestination(destinationName, currentState)
        val routeModes = routeVariantsFor(destination).map { route -> route.toCardMode() }
        val routeCard =
          DemoCardPlanner.routeCard(
            destination = destination,
            routes = routeModes,
            mapAction = ExternalLinkAction(label = "打开地图", uri = "amapuri://route/plan/?dname=${destination.name}"),
          )
        val payload =
          buildJsonObject {
            put(
              "selected_destination",
              buildJsonObject {
                put("name", JsonPrimitive(destination.name))
                put("address", JsonPrimitive(destination.address))
                put("area", JsonPrimitive(destination.area))
              },
            )
            put(
              "routes",
              buildJsonArray {
                routeModes.forEach { route ->
                  add(
                    buildJsonObject {
                      put("mode", JsonPrimitive(route.mode))
                      put("duration_minutes", JsonPrimitive(route.durationMinutes))
                      put("distance_km", JsonPrimitive(route.distanceKm))
                      put("summary", JsonPrimitive(route.summary))
                    },
                  )
                }
              },
            )
            put(
              "open_map_action",
              buildJsonObject {
                put("label", JsonPrimitive(routeCard.openMapAction.label))
                put("uri", JsonPrimitive(routeCard.openMapAction.uri))
              },
            )
          }
        ToolExecutionResult(
          displayText = "get_route_options 已生成 ${destination.name} 的 4 种路线摘要。",
          modelPayload = json.encodeToString(payload),
          nextState =
            currentState.copy(
              selectedDestination = destination,
              latestRouteCard = routeCard,
              hotelResultsByPlatform = emptyMap(),
            ),
        )
      },
    )
  }

  private fun ctripHotelTool(): RegisteredAgentTool {
    return hotelTool(
      toolName = "search_ctrip_hotels",
      platformName = "携程",
      actionLabelPrefix = "打开携程",
      basePrice = 328,
      distanceOffset = 0.1,
    )
  }

  private fun meituanHotelTool(): RegisteredAgentTool {
    return hotelTool(
      toolName = "search_meituan_hotels",
      platformName = "美团",
      actionLabelPrefix = "打开美团",
      basePrice = 299,
      distanceOffset = 0.0,
    )
  }

  private fun hotelTool(
    toolName: String,
    platformName: String,
    actionLabelPrefix: String,
    basePrice: Int,
    distanceOffset: Double,
  ): RegisteredAgentTool {
    return RegisteredAgentTool(
      definition =
        AgentToolDefinition(
          name = toolName,
          description = "查询${platformName}附近酒店数据，支持预算和距离筛选。只回传平台结果，不直接展示酒店卡片。整合多平台结果后，如需展示，请调用 emit_hotel_list_card。",
          parametersSchema =
            buildJsonObject {
              put("type", JsonPrimitive("object"))
              put(
                "properties",
                buildJsonObject {
                  put("destination_name", buildJsonObject { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("目的地名称，可省略，省略时默认使用会话里已选地点")) })
                  put("max_price", buildJsonObject { put("type", JsonPrimitive("integer")); put("description", JsonPrimitive("最高预算，单位元")) })
                  put("max_distance_km", buildJsonObject { put("type", JsonPrimitive("integer")); put("description", JsonPrimitive("离目的地最远距离，单位公里")) })
                },
              )
            },
        ),
      executor = AgentToolExecutor { arguments, currentState ->
        val destinationName = arguments["destination_name"]?.jsonPrimitive?.content?.trim().orEmpty()
        val destination = resolveDestination(destinationName, currentState)
        val maxPrice = arguments["max_price"]?.jsonPrimitive?.intOrNull ?: currentState.hotelFilterContext.maxPrice ?: 400
        val maxDistanceKm = arguments["max_distance_km"]?.jsonPrimitive?.intOrNull ?: currentState.hotelFilterContext.maxDistanceKm ?: 2
        val hotels =
          scriptedHotels(destination.name, basePrice, distanceOffset, actionLabelPrefix)
            .filter { it.price <= maxPrice }
            .filter { it.distanceKm <= maxDistanceKm }
        val payload =
          buildJsonObject {
            put("platform", JsonPrimitive(platformName))
            put("anchor_destination", JsonPrimitive(destination.name))
            put(
              "filters",
              buildJsonObject {
                put("max_price", JsonPrimitive(maxPrice))
                put("max_distance_km", JsonPrimitive(maxDistanceKm))
              },
            )
            put(
              "hotels",
              buildJsonArray {
                hotels.forEach { hotel ->
                  add(
                    buildJsonObject {
                      put("name", JsonPrimitive(hotel.name))
                      put("price", JsonPrimitive(hotel.price))
                      put("distance_km", JsonPrimitive(hotel.distanceKm))
                      put("summary", JsonPrimitive(hotel.summary))
                      put("action_label", JsonPrimitive(hotel.actionLabel))
                      put("action_uri", JsonPrimitive(hotel.actionUri))
                    },
                  )
                }
              },
            )
          }
        val nextState =
          currentState.copy(
            selectedDestination = destination,
            hotelFilterContext = HotelFilterContext(maxPrice = maxPrice, maxDistanceKm = maxDistanceKm),
            hotelResultsByPlatform = currentState.hotelResultsByPlatform + (platformName to hotels),
          )
        ToolExecutionResult(
          displayText = "$toolName 已返回 ${hotels.size} 家${platformName}候选酒店，预算≤¥$maxPrice，距离≤${maxDistanceKm}km。",
          modelPayload = json.encodeToString(payload),
          nextState = nextState,
        )
      },
    )
  }

  private fun emitOptionCardTool(): RegisteredAgentTool {
    return RegisteredAgentTool(
      definition =
        AgentToolDefinition(
          name = "emit_option_card",
          description = "发出用户可点击的 option 卡片。用途：destination_candidates 用于地点歧义确认，hotel_price 用于收集预算，hotel_distance 用于收集距离。只有确实需要用户明确选择时才调用。",
          parametersSchema =
            buildJsonObject {
              put("type", JsonPrimitive("object"))
              put(
                "properties",
                buildJsonObject {
                  put(
                    "card_kind",
                    buildJsonObject {
                      put("type", JsonPrimitive("string"))
                      put("description", JsonPrimitive("option 卡片类型"))
                      put("enum", JsonArray(listOf(JsonPrimitive("destination_candidates"), JsonPrimitive("hotel_price"), JsonPrimitive("hotel_distance"))))
                    },
                  )
                },
              )
              put("required", JsonArray(listOf(JsonPrimitive("card_kind"))))
            },
        ),
      executor = AgentToolExecutor { arguments, currentState ->
        val cardKind = arguments["card_kind"]?.jsonPrimitive?.content
        val card = AgentCardValidator.validate(DemoCardPlanner.buildOptionCardByKind(cardKind.orEmpty(), currentState))
        ToolExecutionResult(
          displayText = "emit_option_card 已发出 ${cardKind.orEmpty()} 选项卡。",
          modelPayload = json.encodeToString(buildJsonObject { put("card_type", JsonPrimitive("option")); put("card_kind", JsonPrimitive(cardKind.orEmpty())) }),
          nextState = currentState,
          cardPayload = requireNotNull(card) { "option 卡片校验失败。" },
        )
      },
      kind = AgentToolKind.Card,
    )
  }

  private fun emitRouteCardTool(): RegisteredAgentTool {
    return RegisteredAgentTool(
      definition =
        AgentToolDefinition(
          name = "emit_route_card",
          description = "发出路线卡片。适用于路线数据已经齐备时直接展示给用户，这张卡更偏展示与跳转 hook，不需要结构化回调。",
          parametersSchema =
            buildJsonObject {
              put("type", JsonPrimitive("object"))
              put("properties", buildJsonObject {})
            },
        ),
      executor = AgentToolExecutor { _, currentState ->
        val card = AgentCardValidator.validate(currentState.latestRouteCard)
        ToolExecutionResult(
          displayText = "emit_route_card 已发出路线卡片。",
          modelPayload = json.encodeToString(buildJsonObject { put("card_type", JsonPrimitive("route")) }),
          nextState = currentState,
          cardPayload = requireNotNull(card) { "当前没有可展示的路线卡片，请先调用 get_route_options。" },
        )
      },
      kind = AgentToolKind.Card,
    )
  }

  private fun emitHotelListCardTool(): RegisteredAgentTool {
    return RegisteredAgentTool(
      definition =
        AgentToolDefinition(
          name = "emit_hotel_list_card",
          description = "发出酒店列表卡片。适用于已经拿到一个或多个平台的酒店结果后，按 cheapest、nearest 或 balanced 的排序策略向用户展示。若某个平台无匹配，卡片会明确标出。",
          parametersSchema =
            buildJsonObject {
              put("type", JsonPrimitive("object"))
              put(
                "properties",
                buildJsonObject {
                  put(
                    "ranking",
                    buildJsonObject {
                      put("type", JsonPrimitive("string"))
                      put("description", JsonPrimitive("酒店排序策略"))
                      put("enum", JsonArray(listOf(JsonPrimitive("cheapest"), JsonPrimitive("nearest"), JsonPrimitive("balanced"))))
                    },
                  )
                },
              )
            },
        ),
      executor = AgentToolExecutor { arguments, currentState ->
        val ranking = HotelListRanking.fromWireValue(arguments["ranking"]?.jsonPrimitive?.content)
        val card = AgentCardValidator.validate(DemoCardPlanner.hotelCard(currentState, ranking = ranking))
        ToolExecutionResult(
          displayText = "emit_hotel_list_card 已发出酒店列表卡片，排序=${ranking.wireValue}。",
          modelPayload = json.encodeToString(buildJsonObject { put("card_type", JsonPrimitive("hotel_list")); put("ranking", JsonPrimitive(ranking.wireValue)) }),
          nextState = currentState,
          cardPayload = requireNotNull(card) { "当前没有可展示的酒店结果，请先调用酒店查询工具。" },
        )
      },
      kind = AgentToolKind.Card,
    )
  }

  private fun destinationCandidatesFor(query: String): List<DestinationCandidate> {
    if (query.contains("静安寺")) {
      return listOf(
        DestinationCandidate(name = "静安寺", address = "南京西路1686号", area = "静安区", summary = "寺庙景点，适合直接导航"),
        DestinationCandidate(name = "静安公园", address = "南京西路1649号", area = "静安区", summary = "街角公园，离商圈很近"),
        DestinationCandidate(name = "静安寺地铁站", address = "2/7/14号线换乘站", area = "静安区", summary = "地铁换乘点，适合作为会合点"),
      )
    }

    return listOf(DestinationCandidate(name = query.ifBlank { "默认目的地" }, address = "演示地址 1 号", area = "演示区域", summary = "默认演示结果"))
  }

  private fun resolveDestination(destinationName: String, currentState: SessionContextState): DestinationCandidate {
    val matched = currentState.destinationCandidates.firstOrNull { it.name == destinationName }
    if (matched != null) {
      return matched
    }
    if (destinationName.isBlank()) {
      return currentState.selectedDestination ?: error("当前没有可用目的地，请先调用 search_destination 或明确地点名称。")
    }
    return DestinationCandidate(
      name = destinationName,
      address = "演示地址 1 号",
      area = "演示区域",
      summary = "用户直接指定的目的地",
    )
  }

  private fun routeVariantsFor(destination: DestinationCandidate): List<RouteVariant> {
    return listOf(
      RouteVariant(mode = "drive", durationMinutes = 18, distanceKm = 7.4, summary = "高架优先，预计 18 分钟，可直接停到商圈周边"),
      RouteVariant(mode = "transit", durationMinutes = 24, distanceKm = 8.1, summary = "2 号线换乘后步行 5 分钟，整体最稳"),
      RouteVariant(mode = "walk", durationMinutes = 63, distanceKm = 4.6, summary = "沿南京西路一路步行，适合边走边逛"),
      RouteVariant(mode = "bike", durationMinutes = 27, distanceKm = 5.1, summary = "共享单车友好，末段进入商圈更顺手"),
    ).map { route ->
      if (destination.name == "静安寺地铁站" && route.mode == "transit") {
        route.copy(durationMinutes = 20, distanceKm = 7.6, summary = "直达换乘点，适合地铁会合")
      } else {
        route
      }
    }
  }

  private fun scriptedHotels(destinationName: String, basePrice: Int, distanceOffset: Double, actionLabelPrefix: String): List<HotelResultItem> {
    return listOf(
      HotelResultItem(name = "静安精选酒店", platform = actionLabelPrefix.removePrefix("打开"), price = basePrice, distanceKm = 0.6 + distanceOffset, summary = "离${destinationName}步行约 9 分钟，评分 4.7，适合当天落脚", actionLabel = "${actionLabelPrefix} · 精选", actionUri = demoHotelUri(actionLabelPrefix, "精选")),
      HotelResultItem(name = "南京西路轻居", platform = actionLabelPrefix.removePrefix("打开"), price = basePrice + 39, distanceKm = 1.2 + distanceOffset, summary = "商圈内位置稳定，夜间回酒店更方便", actionLabel = "${actionLabelPrefix} · 详情", actionUri = demoHotelUri(actionLabelPrefix, "详情")),
      HotelResultItem(name = "静安夜景酒店", platform = actionLabelPrefix.removePrefix("打开"), price = basePrice + 65, distanceKm = 1.8 + distanceOffset, summary = "高层景观更好，适合想住得更舒服一点", actionLabel = "${actionLabelPrefix} · 比价", actionUri = demoHotelUri(actionLabelPrefix, "比价")),
    )
  }

  private fun demoHotelUri(actionLabelPrefix: String, suffix: String): String {
    return when {
      actionLabelPrefix.contains("携程") -> "https://m.ctrip.com/webapp/hotel/?entry=$suffix"
      actionLabelPrefix.contains("美团") -> "https://i.meituan.com/hotel/?entry=$suffix"
      else -> "https://example.com/hotel?entry=$suffix"
    }
  }

  private data class RouteVariant(
    val mode: String,
    val durationMinutes: Int,
    val distanceKm: Double,
    val summary: String,
  ) {
    fun toCardMode(): RouteCardMode {
      return RouteCardMode(
        mode = mode,
        title =
          when (mode) {
            "drive" -> "驾车"
            "transit" -> "公交"
            "walk" -> "步行"
            "bike" -> "骑行"
            else -> mode
          },
        durationMinutes = durationMinutes,
        distanceKm = distanceKm,
        summary = summary,
      )
    }
  }
}
