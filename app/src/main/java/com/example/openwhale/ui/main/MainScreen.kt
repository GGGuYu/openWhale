package com.example.openwhale.ui.main

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.runtime.withFrameNanos

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
    onModelSelected = resolvedViewModel::updateModelId,
  )
}

@Composable
internal fun MainScreen(
  uiState: AgentSessionSnapshot,
  modifier: Modifier = Modifier,
  contentPadding: Dp = 0.dp,
  inputText: String = "",
  listState: LazyListState = rememberLazyListState(),
  onInputChange: (String) -> Unit = {},
  onSendClick: () -> Unit = {},
  onWorkflowPackSelected: (String) -> Unit = {},
  onSelectionSubmit: (SelectionAction) -> Unit = {},
  onApiKeySave: (String) -> Unit = {},
  onModelSelected: (String) -> Unit = {},
) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val bottomPinThresholdPx = with(density) { 96.dp.roundToPx() }
  var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
  var showHistorySheet by rememberSaveable { mutableStateOf(false) }
  var debugExpanded by rememberSaveable { mutableStateOf(false) }
  var apiKeyInput by rememberSaveable { mutableStateOf("") }
  var composerHeightPx by remember { mutableIntStateOf(0) }
  val composerHeightDp = with(density) { composerHeightPx.toDp() }
  val recentConversations = remember(uiState.timeline, uiState.selectedWorkflowPackId, uiState.sessionContextState) { buildRecentConversationItems(uiState) }

  // Tail-content change key: recomputes whenever the latest item's identity or visible
  // content changes (new item added, text grows, card payload changes, streaming flag toggles).
  val tailChangeKey = remember(uiState.timeline.lastOrNull(), uiState.timeline.size) {
    val last = uiState.timeline.lastOrNull()
    buildString {
      append(uiState.timeline.size)
      append(':')
      append(last?.id.orEmpty())
      append(':')
      append(last?.cardPayload?.type.orEmpty())
      append(':')
      append(last?.isStreaming.toString())
      append(':')
      append(last?.text?.length ?: 0)
    }
  }

  // Auto-follow: whenever tail content changes, check if the user is within the
  // bottom threshold. If yes, animate to the latest item. One-frame delay ensures
  // LazyColumn has laid out the new content before we measure distance.
  LaunchedEffect(tailChangeKey) {
    if (uiState.timeline.isNotEmpty()) {
      withFrameNanos { }
      if (listState.isNearBottom(bottomPinThresholdPx)) {
        listState.animateScrollToItem(uiState.timeline.size)
      }
    }
  }

  // When the user actively sends a message, always scroll to it regardless of pin state.
  LaunchedEffect(uiState.timeline.size) {
    val last = uiState.timeline.lastOrNull()
    if (last?.role == TimelineItemRole.User && uiState.timeline.isNotEmpty()) {
      listState.animateScrollToItem(uiState.timeline.size)
    }
  }

  if (showSettingsSheet) {
    SettingsSheet(
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
      debugExpanded = debugExpanded,
      onDebugExpandedChange = { debugExpanded = it },
      onModelSelected = onModelSelected,
      onDismissRequest = { showSettingsSheet = false },
    )
  }

  if (showHistorySheet) {
    HistorySheet(items = recentConversations, onDismissRequest = { showHistorySheet = false })
  }

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
  ) {
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize(),
      contentPadding =
        PaddingValues(
          start = 12.dp,
          end = 12.dp,
          top = contentPadding + 12.dp,
          bottom = composerHeightDp + 12.dp,
        ),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        ChatTopShell(
          uiState = uiState,
          onWorkflowPackSelected = onWorkflowPackSelected,
          onShowSettings = { showSettingsSheet = true },
          onShowHistory = { showHistorySheet = true },
        )
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

    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
      ComposerBar(
        inputText = inputText,
        uiState = uiState,
        onInputChange = onInputChange,
        onSendClick = onSendClick,
        onMeasured = { composerHeightPx = it },
      )
    }
  }
}

@Composable
private fun ChatTopShell(
  uiState: AgentSessionSnapshot,
  onWorkflowPackSelected: (String) -> Unit,
  onShowSettings: () -> Unit,
  onShowHistory: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = RoundedCornerShape(30.dp),
    tonalElevation = 1.dp,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("OpenWhale", style = MaterialTheme.typography.headlineSmall, color = WhaleInk)
          Text(
            "${uiState.providerLabel} · ${uiState.modelLabel}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          AssistChip(
            onClick = onShowHistory,
            label = { Text("历史") },
            modifier = Modifier.semantics { contentDescription = "打开最近对话" },
          )
          AssistChip(
            onClick = onShowSettings,
            label = { Text("设置") },
            modifier = Modifier.semantics { contentDescription = "打开设置" },
          )
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusPill(text = "当前模型", containerColor = WhaleSurface, contentColor = WhaleInk)
        StatusPill(
          text = if (uiState.hasReadyApiKey()) "Key 已就绪" else "Key 待配置",
          containerColor = if (uiState.hasReadyApiKey()) MaterialTheme.colorScheme.secondaryContainer else WhaleSurface,
          contentColor = if (uiState.hasReadyApiKey()) MaterialTheme.colorScheme.onSecondaryContainer else WhaleInk,
        )
      }

      CompactWorkflowSelector(
        availableWorkflowPacks = uiState.availableWorkflowPacks,
        selectedWorkflowPackId = uiState.selectedWorkflowPackId,
        onWorkflowPackSelected = onWorkflowPackSelected,
      )

      SessionContextSummary(uiState = uiState)
    }
  }
}

@Composable
private fun CompactWorkflowSelector(
  availableWorkflowPacks: List<WorkflowPromptPack>,
  selectedWorkflowPackId: String,
  onWorkflowPackSelected: (String) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("工作流", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(
      modifier = Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      availableWorkflowPacks.forEach { pack ->
        AssistChip(
          onClick = { onWorkflowPackSelected(pack.id) },
          label = { Text(pack.title) },
          leadingIcon = {
            Box(
              modifier =
                Modifier
                  .size(10.dp)
                  .background(if (pack.id == selectedWorkflowPackId) WhaleAccent else WhaleAccentSoft, CircleShape),
            )
          },
        )
      }
    }
  }
}

@Composable
private fun SessionContextSummary(uiState: AgentSessionSnapshot) {
  val hasContext =
    uiState.sessionContextState.selectedDestination != null ||
      uiState.sessionContextState.hotelFilterContext.maxPrice != null ||
      uiState.sessionContextState.hotelFilterContext.maxDistanceKm != null ||
      uiState.errorMessage != null
  if (!hasContext) {
    return
  }

  val destination = uiState.sessionContextState.selectedDestination?.name ?: "未选择"
  val price = uiState.sessionContextState.hotelFilterContext.maxPrice?.let { "≤¥$it" } ?: "未设置"
  val distance = uiState.sessionContextState.hotelFilterContext.maxDistanceKm?.let { "≤${it}km" } ?: "未设置"

  Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(22.dp)) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text("当前会话", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        StatusPill(text = destination, containerColor = WhaleSurface, contentColor = WhaleInk)
        StatusPill(text = price, containerColor = WhaleSurface, contentColor = WhaleInk)
        StatusPill(text = distance, containerColor = WhaleSurface, contentColor = WhaleInk)
      }
      uiState.errorMessage?.let { errorMessage ->
        Text(
          errorMessage,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

@Composable
private fun ComposerBar(
  inputText: String,
  uiState: AgentSessionSnapshot,
  onInputChange: (String) -> Unit,
  onSendClick: () -> Unit,
  onMeasured: (Int) -> Unit = {},
) {
  Surface(
    modifier =
      Modifier
        .fillMaxWidth()
        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
        .onSizeChanged { onMeasured(it.height) },
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = 10.dp,
    tonalElevation = 1.dp,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.Bottom,
    ) {
      OutlinedTextField(
        value = inputText,
        onValueChange = onInputChange,
        modifier = Modifier.weight(1f),
        minLines = 1,
        maxLines = 4,
        shape = RoundedCornerShape(22.dp),
        label = { Text("消息") },
        placeholder = { Text("例如：导航到静安寺") },
      )
      Button(
        onClick = onSendClick,
        enabled = inputText.isNotBlank() && !uiState.isSending,
        modifier = Modifier.height(52.dp).widthIn(min = 88.dp),
        shape = RoundedCornerShape(18.dp),
      ) {
        if (uiState.isSending) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text("处理中", maxLines = 1)
        } else {
          Text("发送")
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
  uiState: AgentSessionSnapshot,
  apiKeyInput: String,
  onApiKeyInputChange: (String) -> Unit,
  onSaveClick: () -> Unit,
  onResetClick: () -> Unit,
  debugExpanded: Boolean,
  onDebugExpandedChange: (Boolean) -> Unit,
  onModelSelected: (String) -> Unit,
  onDismissRequest: () -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismissRequest) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text("设置", style = MaterialTheme.typography.headlineSmall, color = WhaleInk)
      Text(
        "把运行时配置和调试能力下沉到次级入口，聊天主界面只保留必要的产品壳层。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      ApiKeyPanel(
        uiState = uiState,
        apiKeyInput = apiKeyInput,
        onApiKeyInputChange = onApiKeyInputChange,
        onSaveClick = onSaveClick,
        onResetClick = onResetClick,
      )
      ModelSelectionPanel(uiState = uiState, onModelSelected = onModelSelected)
      Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text("调试信息", style = MaterialTheme.typography.titleMedium, color = WhaleInk)
              Text("默认折叠，只在需要排查时查看。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AssistChip(
              onClick = { onDebugExpandedChange(!debugExpanded) },
              label = { Text(if (debugExpanded) "收起" else "展开") },
            )
          }
          if (debugExpanded) {
            DebugPanel(events = uiState.debugEvents)
          }
        }
      }
      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

@Composable
private fun ModelSelectionPanel(
  uiState: AgentSessionSnapshot,
  onModelSelected: (String) -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = RoundedCornerShape(20.dp),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("DeepSeek 模型", style = MaterialTheme.typography.titleMedium, color = WhaleInk)
        Text(
          "只开放已验证的模型，切换后下一轮对话立即生效。",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        uiState.supportedModelIds.forEach { modelId ->
          FilterChip(
            selected = modelId == uiState.modelLabel,
            onClick = { onModelSelected(modelId) },
            label = { Text(modelId) },
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(items: List<RecentConversationPreview>, onDismissRequest: () -> Unit) {
  ModalBottomSheet(onDismissRequest = onDismissRequest) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text("最近对话", style = MaterialTheme.typography.headlineSmall, color = WhaleInk)
      Text(
        "当前先展示本地最近会话摘要与占位数据，为后续真正的历史持久化预留入口。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (items.isEmpty()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
          Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("还没有可展示的最近对话", style = MaterialTheme.typography.titleMedium, color = WhaleInk)
            Text("先发一条消息，后续这里会聚合最近会话摘要。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      } else {
        items.forEachIndexed { index, item ->
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(item.title, style = MaterialTheme.typography.titleMedium, color = WhaleInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
              Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
          if (index != items.lastIndex) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
          }
        }
      }
      Spacer(modifier = Modifier.height(8.dp))
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
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("DeepSeek Key", style = MaterialTheme.typography.titleMedium, color = WhaleInk)
          Text(
            "直接在应用内保存本地覆写，真机调试时不需要重新打包。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Surface(
          color = if (uiState.hasReadyApiKey()) WhaleAccentSoft else WhaleSurface,
          shape = RoundedCornerShape(999.dp),
        ) {
          Text(
            if (uiState.hasReadyApiKey()) "已就绪" else "待配置",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = if (uiState.hasReadyApiKey()) WhaleAccent else WhaleInk,
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
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(onClick = onResetClick, enabled = uiState.hasLocalApiKeyOverride) {
          Text("恢复构建配置")
        }
        Button(onClick = onSaveClick, enabled = apiKeyInput.isNotBlank(), shape = RoundedCornerShape(16.dp)) {
          Text("保存本地 Key")
        }
      }
    }
  }
}

@Composable
private fun DebugPanel(events: List<DebugEvent>) {
  Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(18.dp)) {
    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
  when (item.role) {
    TimelineItemRole.Thinking -> {
      ThinkingTraceCard(item = item)
      return
    }

    TimelineItemRole.Tool -> {
      ToolExecutionCard(item = item)
      return
    }
    TimelineItemRole.Status -> {
      StatusEventCard(item = item)
      return
    }
    else -> Unit
  }

  val isUser = item.role == TimelineItemRole.User
  val roleLabel = item.role.displayLabel(item.title)
  val backgroundColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
  val contentColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else WhaleInk
  val avatarEmoji = if (isUser) "🙂" else "🐋"
  val avatarLabel = if (isUser) "用户头像" else "助手头像"

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    verticalAlignment = Alignment.Top,
  ) {
    Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = backgroundColor),
      elevation = CardDefaults.cardElevation(defaultElevation = if (isUser) 1.5.dp else 2.5.dp),
      modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(0.94f),
    ) {
      Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MessageHeader(
          roleLabel = roleLabel,
          avatarEmoji = avatarEmoji,
          avatarLabel = avatarLabel,
          contentColor = contentColor,
          isStreaming = item.isStreaming,
          isUser = isUser,
        )
        if (item.text.isNotBlank()) {
          if (!isUser) {
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
private fun MessageHeader(
  roleLabel: String,
  avatarEmoji: String,
  avatarLabel: String,
  contentColor: Color,
  isStreaming: Boolean,
  isUser: Boolean,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      AvatarMarker(emoji = avatarEmoji, label = avatarLabel, size = 28.dp)
      Text(
        roleLabel,
        color = contentColor.copy(alpha = 0.74f),
        style = MaterialTheme.typography.labelMedium,
      )
    }
    if (isStreaming && !isUser) {
      Text(
        "正在生成",
        color = contentColor.copy(alpha = 0.62f),
        style = MaterialTheme.typography.labelSmall,
      )
    }
  }
}

@Composable
private fun ThinkingTraceCard(item: TimelineItem) {
  var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
  val hasThinkingBody = item.text.isNotBlank()
  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
    modifier = Modifier.widthIn(max = 320.dp),
  ) {
    if (!expanded && hasThinkingBody) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { expanded = true }
          .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          modifier = Modifier.weight(1f),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            "🧠",
            style = MaterialTheme.typography.bodySmall,
          )
          Text(
            "已折叠思考轨迹",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (item.isStreaming) {
            CircularProgressIndicator(
              modifier = Modifier.size(14.dp),
              strokeWidth = 1.8.dp,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
        Text(
          "展开",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    } else {
      Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
              "🧠",
              style = MaterialTheme.typography.bodySmall,
            )
            Text(
              if (item.isStreaming) "思考轨迹" else item.title,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.isStreaming) {
              CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.6.dp,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
          if (hasThinkingBody) {
            TextButton(onClick = { expanded = false }) {
              Text(
                "收起",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
        Text(
          item.text,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

private data class BusinessCardPalette(
  val containerColor: Color,
  val outlineColor: Color,
  val contentColor: Color,
  val supportingColor: Color,
  val nestedSurfaceColor: Color,
  val actionContainerColor: Color,
  val actionContentColor: Color,
)

@Composable
private fun rememberBusinessCardPalette(): BusinessCardPalette {
  val colorScheme = MaterialTheme.colorScheme
  return remember(colorScheme) {
    BusinessCardPalette(
      containerColor = colorScheme.surface,
      outlineColor = colorScheme.outlineVariant.copy(alpha = 0.78f),
      contentColor = colorScheme.onSurface,
      supportingColor = colorScheme.onSurfaceVariant,
      nestedSurfaceColor = colorScheme.surfaceContainerLowest,
      actionContainerColor = colorScheme.surfaceContainerLow,
      actionContentColor = colorScheme.onSurface,
    )
  }
}

@Composable
private fun BusinessCardChip(
  text: String,
  modifier: Modifier = Modifier,
  containerColor: Color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
  contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
  Surface(color = containerColor, shape = RoundedCornerShape(999.dp), modifier = modifier) {
    Text(
      text,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      style = MaterialTheme.typography.labelMedium,
      color = contentColor,
    )
  }
}

@Composable
private fun BusinessCardSurface(
  label: String,
  modifier: Modifier = Modifier,
  trailingChip: String? = null,
  content: @Composable ColumnScope.(BusinessCardPalette) -> Unit,
) {
  val palette = rememberBusinessCardPalette()
  Surface(
    color = palette.containerColor,
    shape = RoundedCornerShape(20.dp),
    border = BorderStroke(1.dp, palette.outlineColor.copy(alpha = 0.52f)),
    shadowElevation = 0.dp,
    modifier = modifier.semantics { contentDescription = "$label 卡片" },
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        BusinessCardChip(text = label)
        trailingChip?.let {
          BusinessCardChip(
            text = it,
            containerColor = palette.actionContainerColor,
            contentColor = palette.supportingColor,
          )
        }
      }
      content(palette)
    }
  }
}

@Composable
private fun AvatarMarker(emoji: String, label: String, size: Dp = 36.dp) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = CircleShape,
    shadowElevation = 1.dp,
    modifier = Modifier.size(size).semantics { contentDescription = label },
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(emoji, style = MaterialTheme.typography.titleMedium)
    }
  }
}

@Composable
private fun StatusEventCard(item: TimelineItem) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = RoundedCornerShape(18.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(item.title, style = MaterialTheme.typography.labelLarge, color = WhaleInk)
      Text(item.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
  BusinessCardSurface(label = "候选地点", trailingChip = if (isConsumed) "已处理" else null) { palette ->
    Text(payload.title, style = MaterialTheme.typography.titleMedium, color = palette.contentColor)
    payload.description?.let {
      Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.supportingColor)
    }
    payload.options.forEach { option ->
      OutlinedButton(
        onClick = { onSelectionSubmit(option.action) },
        enabled = !isConsumed,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, palette.outlineColor.copy(alpha = 0.42f)),
        colors = ButtonDefaults.outlinedButtonColors(
          containerColor = palette.actionContainerColor,
          contentColor = palette.actionContentColor,
          disabledContainerColor = palette.actionContainerColor.copy(alpha = 0.8f),
          disabledContentColor = palette.supportingColor,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
      ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(option.title, color = palette.contentColor, style = MaterialTheme.typography.titleSmall)
          option.supportingText?.let {
            Text(it, color = palette.supportingColor, style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }
    if (payload.allowCustomInput) {
      Surface(
        color = palette.nestedSurfaceColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, palette.outlineColor.copy(alpha = 0.32f)),
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(if (isConsumed) "这张卡片已处理" else "也可以继续直接输入", style = MaterialTheme.typography.labelLarge, color = palette.contentColor)
          Text(
            payload.customInputHint ?: "你也可以继续用自然语言补充条件。",
            style = MaterialTheme.typography.bodySmall,
            color = palette.supportingColor,
          )
        }
      }
    }
  }
}

@Composable
private fun RouteCard(payload: RouteCardPayload, onOpenLink: (String) -> Unit) {
  var selectedTabIndex by remember(payload.destinationName) { mutableIntStateOf(0) }
  val selectedRoute = payload.routes.getOrElse(selectedTabIndex) { payload.routes.first() }

  BusinessCardSurface(label = "路线方案") { palette ->
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(payload.destinationName, style = MaterialTheme.typography.titleMedium, color = palette.contentColor)
      Text(payload.destinationAddress, style = MaterialTheme.typography.bodyMedium, color = palette.supportingColor)
    }
    SecondaryScrollableTabRow(selectedTabIndex = selectedTabIndex, edgePadding = 0.dp, containerColor = Color.Transparent) {
      payload.routes.forEachIndexed { index, route ->
        Tab(selected = selectedTabIndex == index, onClick = { selectedTabIndex = index }, text = { Text(route.title) })
      }
    }
    RouteModePanel(route = selectedRoute)
    OutlinedButton(
      onClick = { onOpenLink(payload.openMapAction.uri) },
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, palette.outlineColor.copy(alpha = 0.72f)),
      colors = ButtonDefaults.outlinedButtonColors(
        containerColor = palette.actionContainerColor,
        contentColor = palette.actionContentColor,
      ),
    ) {
      Text(payload.openMapAction.label)
    }
  }
}

@Composable
private fun RouteModePanel(route: RouteCardMode, modifier: Modifier = Modifier) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = RoundedCornerShape(18.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
    modifier = modifier,
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
  Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.62f), shape = RoundedCornerShape(999.dp)) {
    Text(
      "$label · $value",
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      color = MaterialTheme.colorScheme.onSecondaryContainer,
      style = MaterialTheme.typography.labelLarge,
    )
  }
}

@Composable
private fun HotelListCard(payload: HotelListCardPayload, onOpenLink: (String) -> Unit) {
  BusinessCardSurface(label = "酒店列表") { palette ->
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(payload.title, style = MaterialTheme.typography.titleMedium, color = palette.contentColor)
      Text("锚点：${payload.anchorDestination}", style = MaterialTheme.typography.bodyMedium, color = palette.supportingColor)
      Text("${payload.filterSummary} · ${payload.rankingLabel}", style = MaterialTheme.typography.bodySmall, color = palette.supportingColor)
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

@Composable
private fun HotelRow(hotel: HotelCardItem, onOpenLink: (String) -> Unit) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = RoundedCornerShape(18.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
    shadowElevation = 0.dp,
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
  val marker = toolFeedbackMarker(item.title)
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f),
    shape = RoundedCornerShape(16.dp),
    shadowElevation = 1.dp,
    modifier = Modifier.widthIn(max = 288.dp),
  ) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(marker, style = MaterialTheme.typography.labelSmall)
        Text("工具反馈", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
      }
      Text(item.title, color = WhaleInk, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
      Text(item.text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
  }
}

private fun toolFeedbackMarker(title: String): String {
  val normalized = title.lowercase()
  return when {
    "search" in normalized -> "🔎"
    "route" in normalized || "map" in normalized -> "🧭"
    "hotel" in normalized -> "🏨"
    "emit" in normalized -> "🪄"
    else -> "🛠️"
  }
}

private fun AgentSessionSnapshot.hasReadyApiKey(): Boolean {
  return apiKeyConfigured || hasLocalApiKeyOverride || !apiKeyStatusText.contains("未配置")
}

/**
 * Returns true when the user is near the content bottom.
 * Tolerates a few items below the last visible one so newly-inserted
 * items (cards, tool feedback) that haven't scrolled into the viewport yet
 * don't fool us into thinking the user was reading history.
 */
private fun LazyListState.isNearBottom(thresholdPx: Int): Boolean {
  val info = layoutInfo
  if (info.totalItemsCount == 0) return true
  if (!canScrollForward) return true
  val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return true
  // If 3+ items are below the viewport, the user is browsing history.
  // 0–2 items below = user was at the bottom; the new items just haven't scrolled in yet.
  val itemsBelow = info.totalItemsCount - 1 - lastVisible.index
  if (itemsBelow > 2) return false
  val lastItemBottom = lastVisible.offset + lastVisible.size
  return (info.viewportEndOffset - lastItemBottom) <= thresholdPx
}

private fun LazyListState.distanceFromContentBottom(): Int {
  val info = layoutInfo
  if (info.totalItemsCount == 0) return 0
  if (!canScrollForward) return 0
  val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return 0
  if (lastVisible.index < info.totalItemsCount - 1) return Int.MAX_VALUE
  val lastItemBottom = lastVisible.offset + lastVisible.size
  return (info.viewportEndOffset - lastItemBottom).coerceAtLeast(0)
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

@Composable
private fun StatusPill(text: String, containerColor: Color, contentColor: Color) {
  Surface(color = containerColor, shape = RoundedCornerShape(999.dp)) {
    Text(
      text,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
      color = contentColor,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Medium,
    )
  }
}

private fun buildRecentConversationItems(uiState: AgentSessionSnapshot): List<RecentConversationPreview> {
  val workflowTitle = uiState.availableWorkflowPacks.firstOrNull { it.id == uiState.selectedWorkflowPackId }?.title ?: "当前工作流"
  return uiState.timeline
    .filter { it.role == TimelineItemRole.User }
    .takeLast(6)
    .asReversed()
    .mapIndexed { index, item ->
      RecentConversationPreview(
        title = item.text,
        subtitle = buildString {
          append(workflowTitle)
          append(" · 最近片段 #")
          append(index + 1)
        },
      )
    }
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

private fun DebugEventLevel.label(): String =
  when (this) {
    DebugEventLevel.Info -> "INFO"
    DebugEventLevel.Warning -> "WARN"
    DebugEventLevel.Error -> "ERROR"
  }

private fun DebugEventLevel.dotColor(): Color =
  when (this) {
    DebugEventLevel.Info -> WhaleAccent
    DebugEventLevel.Warning -> Color(0xFFD68C16)
    DebugEventLevel.Error -> Color(0xFFC84A4A)
  }

private fun TimelineItemRole.displayLabel(fallbackTitle: String): String =
  when (this) {
    TimelineItemRole.User -> "用户"
    TimelineItemRole.Assistant -> "助手"
    else -> fallbackTitle
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

private data class RecentConversationPreview(
  val title: String,
  val subtitle: String,
)

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
                    allowCustomInput = true,
                    customInputHint = "也可以继续说一个新的地点",
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
