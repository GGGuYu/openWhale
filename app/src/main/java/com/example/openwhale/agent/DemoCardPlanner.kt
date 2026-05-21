package com.example.openwhale.agent

import java.util.UUID

object DemoCardPlanner {
  private val hotelPlatforms = listOf("携程", "美团")

  fun destinationCard(
    candidates: List<DestinationCandidate>,
    cardId: String = newCardId("destination"),
  ): OptionCardPayload {
    return OptionCardPayload(
      cardId = cardId,
      title = "你想去哪个点？",
      description = "先确认目的地，我再给你路线。",
      options =
        candidates.map { candidate ->
          OptionCardChoice(
            id = candidate.name,
            title = candidate.name,
            supportingText = "${candidate.area} · ${candidate.summary}",
            action = SelectionAction(promptText = "我选 ${candidate.name}", selectedDestinationName = candidate.name, sourceCardId = cardId),
          )
        },
      allowCustomInput = true,
      customInputHint = "也可以直接说一个新的地点",
    )
  }

  fun priceCard(
    destinationName: String,
    cardId: String = newCardId("price"),
  ): OptionCardPayload {
    return OptionCardPayload(
      cardId = cardId,
      title = "酒店预算想控制在多少？",
      description = "我会按 ${destinationName} 附近筛便宜酒店。",
      options =
        listOf(300, 400, 500).map { price ->
          OptionCardChoice(
            id = "price-$price",
            title = "¥$price 以内",
            supportingText = when (price) {
              300 -> "更偏性价比"
              400 -> "演示默认档位"
              else -> "选择更宽松一些"
            },
            action = SelectionAction(promptText = "酒店预算 $price 元以内", maxPrice = price, sourceCardId = cardId),
          )
        },
      allowCustomInput = true,
      customInputHint = "例如：预算 350 以内",
    )
  }

  fun distanceCard(
    destinationName: String,
    cardId: String = newCardId("distance"),
  ): OptionCardPayload {
    return OptionCardPayload(
      cardId = cardId,
      title = "离 ${destinationName} 多近比较合适？",
      description = "我再补一个距离条件就能出结果。",
      options =
        listOf(1, 2, 3).map { distance ->
          OptionCardChoice(
            id = "distance-$distance",
            title = "$distance 公里内",
            supportingText = when (distance) {
              1 -> "步行更方便"
              2 -> "兼顾选择数量"
              else -> "范围更宽松"
            },
            action = SelectionAction(promptText = "离目的地 $distance 公里内", maxDistanceKm = distance, sourceCardId = cardId),
          )
        },
      allowCustomInput = true,
      customInputHint = "例如：2.5 公里内",
    )
  }

  fun routeCard(destination: DestinationCandidate, routes: List<RouteCardMode>, mapAction: ExternalLinkAction): RouteCardPayload {
    return RouteCardPayload(
      destinationName = destination.name,
      destinationAddress = destination.address,
      routes = routes,
      openMapAction = mapAction,
    )
  }

  fun hotelCard(
    state: SessionContextState,
    ranking: HotelListRanking = HotelListRanking.Balanced,
  ): HotelListCardPayload? {
    val platformStatuses =
      hotelPlatforms.map { platform ->
        val platformResults = state.hotelResultsByPlatform[platform]
        when {
          platformResults == null -> HotelPlatformStatus(platform = platform, statusText = "尚未查询", hasMatches = false)
          platformResults.isEmpty() -> HotelPlatformStatus(platform = platform, statusText = "当前筛选下无匹配", hasMatches = false)
          else -> HotelPlatformStatus(platform = platform, statusText = "${platformResults.size} 家可选", hasMatches = true)
        }
      }

    val merged =
      (state.hotelResultsByPlatform.values.flatten())
        .groupBy(HotelResultItem::name)
        .values
        .map { platformItems ->
          val reference = platformItems.minByOrNull(HotelResultItem::price) ?: platformItems.first()
          HotelCardItem(
            name = reference.name,
            distanceKm = platformItems.minOf(HotelResultItem::distanceKm),
            summary = reference.summary,
            platformQuotes =
              platformItems.sortedBy(HotelResultItem::price).map { item ->
                HotelPlatformQuote(
                  platform = item.platform,
                  price = item.price,
                  actionLabel = item.actionLabel,
                  actionUri = item.actionUri,
                )
              },
          )
        }
        .sortedWith(ranking.comparator())

    if (merged.isEmpty()) {
      return null
    }

    val destinationName = state.selectedDestination?.name ?: "附近"
    val filterSummary = buildString {
      append(state.hotelFilterContext.maxPrice?.let { "预算 ≤ ¥$it" } ?: "预算未限定")
      append(" · ")
      append(state.hotelFilterContext.maxDistanceKm?.let { "距离 ≤ ${it}km" } ?: "距离未限定")
    }

    return HotelListCardPayload(
      title = "附近便宜酒店",
      anchorDestination = destinationName,
      filterSummary = filterSummary,
      rankingLabel = ranking.label,
      platformStatuses = platformStatuses,
      hotels = merged,
    )
  }

  fun buildOptionCardByKind(cardKind: String, state: SessionContextState): OptionCardPayload {
    return when (cardKind) {
      "destination_candidates" -> {
        check(state.destinationCandidates.isNotEmpty()) { "当前没有可展示的目的地候选项。" }
        destinationCard(state.destinationCandidates)
      }

      "hotel_price" -> {
        val destinationName = state.selectedDestination?.name ?: error("当前没有已选目的地，无法展示预算选项卡。")
        priceCard(destinationName)
      }

      "hotel_distance" -> {
        val destinationName = state.selectedDestination?.name ?: error("当前没有已选目的地，无法展示距离选项卡。")
        distanceCard(destinationName)
      }

      else -> error("不支持的 option 卡片类型: $cardKind")
    }
  }

  fun maybeBuildAssistantCardFallback(
    workflowPackId: String,
    responseText: String?,
    state: SessionContextState,
    lastExecutedToolNames: List<String>,
  ): AgentCardPayload? {
    if (workflowPackId != "navigation_hotel") {
      return null
    }

    if ("search_destination" in lastExecutedToolNames && state.destinationCandidates.isNotEmpty()) {
      return destinationCard(state.destinationCandidates)
    }
    if ("get_route_options" in lastExecutedToolNames && state.latestRouteCard != null) {
      return state.latestRouteCard
    }
    if (lastExecutedToolNames.any { it == "search_ctrip_hotels" || it == "search_meituan_hotels" }) {
      hotelCard(state)?.let { return it }
    }

    if (responseText.isNullOrBlank()) {
      return null
    }

    val text = responseText.lowercase()
    if (state.selectedDestination != null && state.hotelFilterContext.maxPrice == null && asksForPrice(text)) {
      return priceCard(state.selectedDestination.name)
    }
    if (state.selectedDestination != null && state.hotelFilterContext.maxPrice != null && state.hotelFilterContext.maxDistanceKm == null && asksForDistance(text)) {
      return distanceCard(state.selectedDestination.name)
    }
    return null
  }

  fun fallbackText(card: AgentCardPayload): String {
    return when (card) {
      is OptionCardPayload -> card.description ?: "请从下方选择。"
      is RouteCardPayload -> "路线已经准备好了。"
      is HotelListCardPayload -> "我先把酒店结果整理给你。"
    }
  }

  private fun asksForPrice(text: String): Boolean =
    listOf("预算", "价位", "价格", "房价", "多少钱").any(text::contains)

  private fun asksForDistance(text: String): Boolean =
    listOf("距离", "多远", "范围", "公里", "附近").any(text::contains)

  private fun HotelListRanking.comparator(): Comparator<HotelCardItem> {
    return when (this) {
      HotelListRanking.Cheapest -> compareBy<HotelCardItem>({ it.platformQuotes.minOf(HotelPlatformQuote::price) }, { it.distanceKm })
      HotelListRanking.Nearest -> compareBy<HotelCardItem>({ it.distanceKm }, { it.platformQuotes.minOf(HotelPlatformQuote::price) })
      HotelListRanking.Balanced -> compareBy<HotelCardItem>({ it.distanceKm + (it.platformQuotes.minOf(HotelPlatformQuote::price) / 300.0) }, { it.platformQuotes.minOf(HotelPlatformQuote::price) })
    }
  }

  private fun newCardId(prefix: String): String = "$prefix-${UUID.randomUUID()}"
}

object AgentCardValidator {
  fun validate(card: AgentCardPayload?): AgentCardPayload? {
    card ?: return null
    return when (card) {
      is OptionCardPayload -> card.takeIf { it.cardId.isNotBlank() && it.title.isNotBlank() && it.options.isNotEmpty() && it.options.all { option -> option.title.isNotBlank() && option.action.promptText.isNotBlank() } }
      is RouteCardPayload -> card.takeIf { it.destinationName.isNotBlank() && it.routes.size == 4 && it.routes.all { route -> route.title.isNotBlank() } && it.openMapAction.uri.isNotBlank() }
      is HotelListCardPayload -> card.takeIf { it.hotels.isNotEmpty() && it.platformStatuses.isNotEmpty() && it.hotels.all { hotel -> hotel.platformQuotes.isNotEmpty() && hotel.name.isNotBlank() } }
    }
  }
}
