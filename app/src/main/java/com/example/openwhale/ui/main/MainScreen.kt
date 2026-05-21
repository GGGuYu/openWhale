package com.example.openwhale.ui.main

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.openwhale.agent.AgentCardPayload
import com.example.openwhale.agent.AgentSessionSnapshot
import com.example.openwhale.agent.DebugEvent
import com.example.openwhale.agent.DebugEventLevel
import com.example.openwhale.agent.ExternalLinkAction
import com.example.openwhale.agent.HotelCardItem
import com.example.openwhale.agent.HotelFilterContext
import com.example.openwhale.agent.HotelListCardPayload
import com.example.openwhale.agent.HotelPlatformQuote
import com.example.openwhale.agent.HotelPlatformStatus
import com.example.openwhale.agent.OptionCardPayload
import com.example.openwhale.agent.RouteCardMode
import com.example.openwhale.agent.RouteCardPayload
import com.example.openwhale.agent.SelectionAction
import com.example.openwhale.agent.SessionContextState
import com.example.openwhale.agent.TimelineItem
import com.example.openwhale.agent.TimelineItemRole
import com.example.openwhale.agent.WorkflowPromptPack
import com.example.openwhale.theme.OpenWhaleTheme
import com.example.openwhale.theme.WhaleAccent
import com.example.openwhale.theme.WhaleAccentSoft
import com.example.openwhale.theme.WhaleInk
import com.example.openwhale.theme.WhaleSurface

@Composable
fun MainScreen(
  modifier: Modifier = Modifier,
  contentPadding: Dp = 0.dp,
  viewModel: MainScreenViewModel? = null,
) {
  val appContext = LocalContext.current.applicationContext
  val resolvedViewModel = if (viewModel != null) viewModel else remember(appContext) { MainScreenViewModel.create(appContext) }
  val uiState by resolvedViewModel.uiState.collectAsStateWithLifecycle()
  var inputText by remember { mutableStateOf("") }

  MainScreen(
    uiState = uiState,
    modifier = modifier,
    contentPadding = contentPadding,
    inputText = inputText,
    onInputChange = { inputText = it },
    onSendClick = {
      resolvedViewModel.sendMessage(inputText)
      inputText = ""
    },
    onWorkflowPackSelected = resolvedViewModel::selectWorkflowPack,
    onSelectionSubmit = resolvedViewModel::submitSelection,
    onApiKeySave = resolvedViewModel::updateApiKey,
  )
}

@Composable
internal fun MainScreen(
  uiState: AgentSessionSnapshot,
  modifier: Modifier = Modifier,
  contentPadding: Dp = 0.dp,
  inputText: String = "",
  onInputChange: (String) -> Unit = {},
  onSendClick: () -> Unit = {},
  onWorkflowPackSelected: (String) -> Unit = {},
  onSelectionSubmit: (SelectionAction) -> Unit = {},
  onApiKeySave: (String) -> Unit = {},
) {
  val context = LocalContext.current

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    bottomBar = {
      Surface(shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surface) {
        Column(
          modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedTextField(
            value = inputText,
            onValueChange = onInputChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            shape = RoundedCornerShape(20.dp),
            label = { Text("输入消息") },
            placeholder = { Text("例如：导航到静安寺") },
          )
          Button(
            onClick = onSendClick,
            enabled = inputText.isNotBlank() && !uiState.isSending,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(18.dp),
          ) {
            if (uiState.isSending) {
              CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
              Spacer(modifier = Modifier.width(10.dp))
              Text("处理中")
            } else {
              Text("发送")
            }
          }
        }
      }
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding =
        PaddingValues(
          start = 16.dp,
          end = 16.dp,
          top = contentPadding + innerPadding.calculateTopPadding() + 12.dp,
          bottom = innerPadding.calculateBottomPadding() + 20.dp,
        ),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        HeaderCard(uiState = uiState, onWorkflowPackSelected = onWorkflowPackSelected, onApiKeySave = onApiKeySave)
      }
      items(uiState.timeline, key = TimelineItem::id) { item ->
        TimelineBubble(
          item = item,
          consumedCallbackCardIds = uiState.sessionContextState.consumedCallbackCardIds,
          onSelectionSubmit = onSelectionSubmit,
          onOpenLink = { uri -> openExternalLink(context = context, uri = uri) },
        )
      }
    }
  }
}

@Composable
private fun HeaderCard(uiState: AgentSessionSnapshot, onWorkflowPackSelected: (String) -> Unit, onApiKeySave: (String) -> Unit) {
  var debugExpanded by remember { mutableStateOf(true) }
  var apiKeyInput by remember { mutableStateOf("") }
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(24.dp)) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Text("OpenWhale Mobile Agent Demo", style = MaterialTheme.typography.headlineMedium, color = WhaleInk)
      Text(
        "导航、歧义确认、预算筛选、酒店列表和平台跳转已经接通。当前面板也会显示最近的 runtime 调试事件。",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      StatusPills(uiState = uiState)
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("切换工作流", style = MaterialTheme.typography.titleMedium, color = WhaleInk)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          uiState.availableWorkflowPacks.forEach { pack ->
            AssistChip(
              onClick = { onWorkflowPackSelected(pack.id) },
              label = { Text(pack.title) },
              leadingIcon = {
                Box(modifier = Modifier.size(10.dp).background(if (pack.id == uiState.selectedWorkflowPackId) WhaleAccent else WhaleAccentSoft, CircleShape))
              },
            )
          }
        }
      }
      ContextSummary(uiState = uiState)
      ApiKeyPanel(
        uiState = uiState,
        apiKeyInput = apiKeyInput,
        onApiKeyInputChange = { apiKeyInput = it },
        onSaveClick = {
          onApiKeySave(apiKeyInput)
          apiKeyInput = ""
        },
        onResetClick = {
          onApiKeySave("")
          apiKeyInput = ""
        },
      )
      AssistChip(
        onClick = { debugExpanded = !debugExpanded },
        label = { Text(if (debugExpanded) "收起调试面板" else "展开调试面板") },
      )
      if (debugExpanded) {
        DebugPanel(events = uiState.debugEvents)
      }
    }
  }
}

@Composable
private fun ApiKeyPanel(
  uiState: AgentSessionSnapshot,
  apiKeyInput: String,
  onApiKeyInputChange: (String) -> Unit,
  onSaveClick: () -> Unit,
  onResetClick: () -> Unit,
) {
  Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("DeepSeek Key", style = MaterialTheme.typography.titleMedium, color = WhaleInk)
          Text(
            "直接在应用里保存本地密钥，真机调试时不需要重新打包。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Surface(
          color = if (uiState.apiKeyConfigured) WhaleAccentSoft else WhaleSurface,
          shape = RoundedCornerShape(999.dp),
        ) {
          Text(
            if (uiState.apiKeyConfigured) "已就绪" else "待配置",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = if (uiState.apiKeyConfigured) WhaleAccent else WhaleInk,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
          )
        }
      }
      Text(uiState.apiKeyStatusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      OutlinedTextField(
        value = apiKeyInput,
        onValueChange = onApiKeyInputChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        label = { Text("更新 DeepSeek API Key") },
        placeholder = { Text("粘贴 sk-...，仅保存在本机") },
        visualTransformation = PasswordVisualTransformation(),
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onResetClick, enabled = uiState.hasLocalApiKeyOverride) {
          Text("恢复构建配置")
        }
        Button(
          onClick = onSaveClick,
          enabled = apiKeyInput.isNotBlank(),
          shape = RoundedCornerShape(16.dp),
        ) {
          Text("保存本地 Key")
        }
      }
    }
  }
}

@Composable
private fun StatusPills(uiState: AgentSessionSnapshot) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Surface(color = WhaleAccentSoft, shape = RoundedCornerShape(999.dp)) {
      Text(
        "${uiState.providerLabel} / ${uiState.modelLabel}",
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        color = WhaleAccent,
        fontWeight = FontWeight.Medium,
      )
    }
    Surface(color = WhaleSurface, shape = RoundedCornerShape(999.dp)) {
      Text("卡片回调已启用", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = WhaleInk, fontWeight = FontWeight.Medium)
    }
    uiState.errorMessage?.let {
      Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(999.dp)) {
        Text("最近有错误", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
      }
    }
  }
}

@Composable
private fun ContextSummary(uiState: AgentSessionSnapshot) {
  val destination = uiState.sessionContextState.selectedDestination?.name ?: "未选择"
  val price = uiState.sessionContextState.hotelFilterContext.maxPrice?.let { "≤¥$it" } ?: "未设置"
  val distance = uiState.sessionContextState.hotelFilterContext.maxDistanceKm?.let { "≤${it}km" } ?: "未设置"
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text("会话状态", style = MaterialTheme.typography.titleMedium, color = WhaleInk)
    Text("目的地：$destination", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("酒店预算：$price，距离：$distance", color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun DebugPanel(events: List<DebugEvent>) {
  Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("最近调试事件", style = MaterialTheme.typography.titleMedium, color = WhaleInk)
      val recentEvents = events.takeLast(8).asReversed()
      if (recentEvents.isEmpty()) {
        Text("当前还没有调试事件。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      } else {
        recentEvents.forEach { event ->
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.padding(top = 6.dp).size(8.dp).background(event.level.dotColor(), CircleShape))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text("${event.category.uppercase()} · ${event.level.label()}", style = MaterialTheme.typography.labelLarge, color = WhaleInk)
              Text(event.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TimelineBubble(
  item: TimelineItem,
  consumedCallbackCardIds: Set<String>,
  onSelectionSubmit: (SelectionAction) -> Unit,
  onOpenLink: (String) -> Unit,
) {
  if (item.role == TimelineItemRole.Tool) {
    ToolExecutionCard(item = item)
    return
  }

  val alignment = if (item.role == TimelineItemRole.User) Alignment.CenterEnd else Alignment.CenterStart
  val backgroundColor =
    when (item.role) {
      TimelineItemRole.User -> WhaleAccent
      TimelineItemRole.Assistant -> MaterialTheme.colorScheme.surface
      TimelineItemRole.Tool -> WhaleAccentSoft
      TimelineItemRole.Status -> WhaleSurface
    }
  val contentColor = if (item.role == TimelineItemRole.User) Color.White else WhaleInk

  Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
    Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = backgroundColor),
      modifier = Modifier.fillMaxWidth(if (item.role == TimelineItemRole.Status) 1f else 0.94f),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(item.title, color = contentColor.copy(alpha = 0.75f), style = MaterialTheme.typography.titleMedium)
        if (item.text.isNotBlank()) {
          if (item.role == TimelineItemRole.Assistant || item.role == TimelineItemRole.Status) {
            MarkdownText(markdown = item.text, color = contentColor)
          } else {
            Text(item.text, color = contentColor, style = MaterialTheme.typography.bodyLarge)
          }
        }
        item.cardPayload?.let { payload ->
          CardContent(
            payload = payload,
            consumedCallbackCardIds = consumedCallbackCardIds,
            onSelectionSubmit = onSelectionSubmit,
            onOpenLink = onOpenLink,
          )
        }
      }
    }
  }
}

@Composable
private fun CardContent(
  payload: AgentCardPayload,
  consumedCallbackCardIds: Set<String>,
  onSelectionSubmit: (SelectionAction) -> Unit,
  onOpenLink: (String) -> Unit,
) {
  when (payload) {
    is OptionCardPayload -> OptionCard(payload = payload, consumedCallbackCardIds = consumedCallbackCardIds, onSelectionSubmit = onSelectionSubmit)
    is RouteCardPayload -> RouteCard(payload = payload, onOpenLink = onOpenLink)
    is HotelListCardPayload -> HotelListCard(payload = payload, onOpenLink = onOpenLink)
  }
}

@Composable
private fun OptionCard(payload: OptionCardPayload, consumedCallbackCardIds: Set<String>, onSelectionSubmit: (SelectionAction) -> Unit) {
  val isConsumed = payload.cardId in consumedCallbackCardIds
  Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(payload.title, style = MaterialTheme.typography.titleMedium, color = WhaleInk)
      payload.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
      payload.options.forEach { option ->
        OutlinedButton(
          onClick = { onSelectionSubmit(option.action) },
          enabled = !isConsumed,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        ) {
          Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(option.title, color = WhaleInk, style = MaterialTheme.typography.titleSmall)
            option.supportingText?.let {
              Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
          }
        }
      }
      if (payload.allowCustomInput) {
        Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(16.dp)) {
          Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (isConsumed) "这张卡片已处理" else "也可以继续直接输入", style = MaterialTheme.typography.labelLarge, color = WhaleInk)
            Text(
              payload.customInputHint ?: "你也可以继续用自然语言补充条件。",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun RouteCard(payload: RouteCardPayload, onOpenLink: (String) -> Unit) {
  var selectedTabIndex by remember(payload.destinationName) { mutableIntStateOf(0) }
  val selectedRoute = payload.routes.getOrElse(selectedTabIndex) { payload.routes.first() }

  Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Column(modifier = Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(payload.destinationName, style = MaterialTheme.typography.titleMedium, color = WhaleInk)
        Text(payload.destinationAddress, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      SecondaryScrollableTabRow(selectedTabIndex = selectedTabIndex, edgePadding = 14.dp) {
        payload.routes.forEachIndexed { index, route ->
          Tab(selected = selectedTabIndex == index, onClick = { selectedTabIndex = index }, text = { Text(route.title) })
        }
      }
      RouteModePanel(route = selectedRoute, modifier = Modifier.padding(horizontal = 14.dp))
      OutlinedButton(
        onClick = { onOpenLink(payload.openMapAction.uri) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        shape = RoundedCornerShape(16.dp),
      ) {
        Text(payload.openMapAction.label)
      }
    }
  }
}

@Composable
private fun RouteModePanel(route: RouteCardMode, modifier: Modifier = Modifier) {
  Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(18.dp), modifier = modifier) {
    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatBadge(label = "方式", value = route.title)
        StatBadge(label = "耗时", value = "${route.durationMinutes} 分钟")
        StatBadge(label = "距离", value = "${route.distanceKm} km")
      }
      Text(route.summary, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
  }
}

@Composable
private fun StatBadge(label: String, value: String) {
  Surface(color = WhaleSurface, shape = RoundedCornerShape(999.dp)) {
    Text("$label · $value", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = WhaleInk, style = MaterialTheme.typography.labelLarge)
  }
}

@Composable
private fun HotelListCard(payload: HotelListCardPayload, onOpenLink: (String) -> Unit) {
  Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(payload.title, style = MaterialTheme.typography.titleMedium, color = WhaleInk)
        Text("锚点：${payload.anchorDestination}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${payload.filterSummary} · ${payload.rankingLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        payload.platformStatuses.forEach { platformStatus ->
          HotelPlatformStatusChip(status = platformStatus)
        }
      }
      payload.hotels.forEach { hotel ->
        HotelRow(hotel = hotel, onOpenLink = onOpenLink)
      }
    }
  }
}

@Composable
private fun HotelRow(hotel: HotelCardItem, onOpenLink: (String) -> Unit) {
  Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(18.dp)) {
    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(hotel.name, style = MaterialTheme.typography.titleSmall, color = WhaleInk)
      Text(
        "约 ${hotel.distanceKm} km · ${hotel.summary}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      hotel.platformQuotes.forEach { quote ->
        PlatformQuoteRow(quote = quote, onOpenLink = onOpenLink)
      }
    }
  }
}

@Composable
private fun PlatformQuoteRow(quote: HotelPlatformQuote, onOpenLink: (String) -> Unit) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(quote.platform, color = WhaleInk, style = MaterialTheme.typography.labelLarge)
      Text("¥${quote.price}", color = WhaleAccent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
    OutlinedButton(onClick = { onOpenLink(quote.actionUri) }, shape = RoundedCornerShape(14.dp)) {
      Text(quote.actionLabel)
    }
  }
}

@Composable
private fun ToolExecutionCard(item: TimelineItem) {
  Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
    Surface(
      color = WhaleAccentSoft,
      shape = RoundedCornerShape(20.dp),
      modifier = Modifier.fillMaxWidth(0.88f),
    ) {
      Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(color = WhaleSurface, shape = RoundedCornerShape(999.dp)) {
          Text("工具执行反馈", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = WhaleInk, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
        Text(item.title, color = WhaleInk, style = MaterialTheme.typography.titleMedium)
        Text(item.text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
      }
    }
  }
}

@Composable
private fun HotelPlatformStatusChip(status: HotelPlatformStatus) {
  Surface(
    color = if (status.hasMatches) WhaleSurface else MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(999.dp),
  ) {
    Text(
      "${status.platform} · ${status.statusText}",
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      color = if (status.hasMatches) WhaleInk else MaterialTheme.colorScheme.onSurfaceVariant,
      style = MaterialTheme.typography.labelMedium,
    )
  }
}

@Composable
private fun MarkdownText(markdown: String, color: Color) {
  Text(markdown.toAnnotatedString(), color = color, style = MaterialTheme.typography.bodyLarge)
}

private fun openExternalLink(context: android.content.Context, uri: String) {
  runCatching {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  }.recoverCatching {
    if (it is ActivityNotFoundException && uri.startsWith("amapuri://")) {
      context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://uri.amap.com/navigation")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } else {
      throw it
    }
  }
}

@Composable
private fun DebugEventLevel.label(): String =
  when (this) {
    DebugEventLevel.Info -> "INFO"
    DebugEventLevel.Warning -> "WARN"
    DebugEventLevel.Error -> "ERROR"
  }

@Composable
private fun DebugEventLevel.dotColor(): Color =
  when (this) {
    DebugEventLevel.Info -> WhaleAccent
    DebugEventLevel.Warning -> Color(0xFFD68C16)
    DebugEventLevel.Error -> MaterialTheme.colorScheme.error
  }

private fun String.toAnnotatedString(): AnnotatedString {
  val bulletRegex = Regex("^[-*]\\s+")
  val boldRegex = Regex("\\*\\*(.+?)\\*\\*")
  return buildAnnotatedString {
    lineSequence().forEachIndexed { lineIndex, rawLine ->
      if (lineIndex > 0) {
        append("\n")
      }
      val line = rawLine.trimEnd()
      val normalizedLine = if (bulletRegex.containsMatchIn(line)) "• ${line.replaceFirst(bulletRegex, "")}" else line
      var cursor = 0
      boldRegex.findAll(normalizedLine).forEach { match ->
        if (match.range.first > cursor) {
          append(normalizedLine.substring(cursor, match.range.first))
        }
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
          append(match.groupValues[1])
        }
        cursor = match.range.last + 1
      }
      if (cursor < normalizedLine.length) {
        append(normalizedLine.substring(cursor))
      }
    }
  }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun MainScreenPreview() {
  OpenWhaleTheme {
    MainScreen(
      uiState =
        AgentSessionSnapshot(
          timeline =
            listOf(
              TimelineItem(id = "1", role = TimelineItemRole.Status, title = "工作流", text = "当前工作流：导航找酒店 Demo。试试输入：导航到静安寺"),
              TimelineItem(id = "2", role = TimelineItemRole.User, title = "你", text = "导航到静安寺"),
              TimelineItem(id = "3", role = TimelineItemRole.Tool, title = "search_destination", text = "search_destination 已返回 3 个候选地点：静安寺、静安公园、静安寺地铁站"),
              TimelineItem(
                id = "4",
                role = TimelineItemRole.Assistant,
                title = "助手",
                text = "你想去哪一个？",
                cardPayload =
                  OptionCardPayload(
                    cardId = "preview-card",
                    title = "你想去哪个点？",
                    description = "先确认目的地，我再给你路线。",
                    options =
                      listOf(
                        com.example.openwhale.agent.OptionCardChoice(
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
              WorkflowPromptPack(id = "general_chat", title = "通用聊天", systemPrompt = "", starterPrompt = ""),
              WorkflowPromptPack(id = "navigation_hotel", title = "导航找酒店 Demo", systemPrompt = "", starterPrompt = ""),
            ),
          selectedWorkflowPackId = "navigation_hotel",
          providerLabel = "deepseek",
          modelLabel = "deepseek-chat",
          sessionContextState = SessionContextState(hotelFilterContext = HotelFilterContext(), selectedDestination = null),
          apiKeyConfigured = false,
          apiKeyStatusText = "当前未配置 API Key，请先在应用内填写",
          isSending = false,
        ),
      contentPadding = 16.dp,
    )
  }
}
