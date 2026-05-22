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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
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
    onResetSession = resolvedViewModel::resetSession,
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
  onResetSession: () -> Unit = {},
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
        val last = uiState.timeline.lastOrNull()
        val isCardOrTool = last?.cardPayload != null || last?.role == TimelineItemRole.Tool
        if (isCardOrTool) {
          // Constant-speed smooth scroll for large cards / tool output.
          // We can't pre-measure the distance because LazyColumn composes items
          // lazily — items below the viewport aren't composed until we scroll near
          // them. Instead, scroll at ~2000 px/s until the bottom is reached, which
          // naturally produces a ~500ms scroll for a typical 1000px card.
          listState.scroll {
            val startNanos = withFrameNanos { it }
            val speedPxPerSec = 2000f
            var lastFrameNanos = startNanos
            var stalledFrames = 0
            while (stalledFrames < 3) {
              val now = withFrameNanos { it }
              val deltaSec = ((now - lastFrameNanos).toFloat() / 1_000_000_000f).coerceIn(0f, 0.1f)
              val scrollAmount = speedPxPerSec * deltaSec
              val consumed = scrollBy(scrollAmount)
              lastFrameNanos = now
              if (consumed < 1f) {
                stalledFrames++
              } else {
                stalledFrames = 0
              }
            }
          }
        } else {
          listState.animateScrollToItem(uiState.timeline.size)
        }
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
    HistorySheet(
      uiState = uiState,
      onWorkflowPackSelected = onWorkflowPackSelected,
      onShowSettings = { showSettingsSheet = true },
      onDismissRequest = { showHistorySheet = false },
    )
  }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
  ) {
    ChatTopBar(
      uiState = uiState,
      onMenuClick = { showHistorySheet = true },
      onNewChatClick = onResetSession,
    )

    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding =
          PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 12.dp,
            bottom = composerHeightDp + 12.dp,
          ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        items(uiState.timeline, key = TimelineItem::id) { item ->
          TimelineBubble(
            item = item,
            consumedCallbackCardIds = uiState.sessionContextState.consumedCallbackCardIds,
            onSelectionSubmit = onSelectionSubmit,
            onOpenLink = { uri -> openExternalLink(context = context, uri = uri) },
          )
        }
      }

      if (uiState.timeline.isEmpty()) {
        Column(
          modifier = Modifier
            .align(Alignment.Center)
            .padding(horizontal = 32.dp)
            .offset(y = (-48).dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              "🐋",
              style = MaterialTheme.typography.headlineMedium,
            )
            Text(
              "你好，我是 OpenWhale",
              style = MaterialTheme.typography.titleLarge,
              color = WhaleInk,
              fontWeight = FontWeight.SemiBold,
            )
          }
          Text(
            "一个致力于生活服务的手机端 Agent",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
}

@Composable
private fun ChatTopBar(
  uiState: AgentSessionSnapshot,
  onMenuClick: () -> Unit,
  onNewChatClick: () -> Unit,
) {
  val hasApiKey = uiState.hasReadyApiKey()
  Surface(
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = 2.dp,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(horizontal = 4.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onMenuClick) {
        Icon(
          imageVector = Icons.Filled.Menu,
          contentDescription = "打开历史面板",
          tint = WhaleInk,
        )
      }

      Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          "OpenWhale",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = WhaleInk,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .background(
                if (hasApiKey) Color(0xFF4CAF50) else Color(0xFFE53935),
                CircleShape,
              ),
          )
          Text(
            if (hasApiKey) "${uiState.modelLabel} with Max" else "模型 API Key 未填写",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      IconButton(onClick = onNewChatClick) {
        Icon(
          imageVector = Icons.Filled.Edit,
          contentDescription = "新对话",
          tint = WhaleInk,
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
        placeholder = { Text("") },
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
private fun HistorySheet(
  uiState: AgentSessionSnapshot,
  onWorkflowPackSelected: (String) -> Unit,
  onShowSettings: () -> Unit,
  onDismissRequest: () -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismissRequest) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text("菜单", style = MaterialTheme.typography.headlineSmall, color = WhaleInk)

      // Workflow selector
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("工作流", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          uiState.availableWorkflowPacks.forEach { pack ->
            AssistChip(
              onClick = { onWorkflowPackSelected(pack.id) },
              label = { Text(pack.title) },
              leadingIcon = {
                Box(
                  modifier =
                    Modifier
                      .size(10.dp)
                      .background(
                        if (pack.id == uiState.selectedWorkflowPackId) WhaleAccent else WhaleAccentSoft,
                        CircleShape,
                      ),
                )
              },
            )
          }
        }
      }

      // Settings entry button
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable { onShowSettings() },
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Settings,
              contentDescription = null,
              tint = WhaleInk,
              modifier = Modifier.size(22.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text("设置", style = MaterialTheme.typography.titleMedium, color = WhaleInk)
              Text(
                "API Key 配置 · 模型选择 · 调试信息",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
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
  val avatarLabel = if (isUser) "你的头像" else "Deepseek 头像"

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
  containerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
  contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
  Surface(color = containerColor, shape = RoundedCornerShape(8.dp), modifier = modifier) {
    Text(
      text,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
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
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
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
  BusinessCardSurface(label = payload.label, trailingChip = if (isConsumed) "已处理" else null) { palette ->
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
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      payload.routes.forEachIndexed { index, route ->
        FilterChip(
          selected = selectedTabIndex == index,
          onClick = { selectedTabIndex = index },
          label = {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center,
            ) {
              Box(contentAlignment = Alignment.Center) {
                Text(
                  text = modeEmoji(route.mode),
                  style = MaterialTheme.typography.titleMedium,
                )
              }
            }
          },
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(12.dp),
        )
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
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = route.title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = "约 ${route.durationMinutes} 分钟",
            style = MaterialTheme.typography.headlineSmall,
            color = WhaleInk,
            fontWeight = FontWeight.Bold,
          )
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = modeEmoji(route.mode),
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text = "${route.distanceKm} km",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Text(
        route.summary,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
      )
    }
  }
}

private fun modeEmoji(mode: String): String = when (mode) {
  "drive" -> "🚗"
  "transit" -> "🚌"
  "walk" -> "🚶"
  "bike" -> "🚲"
  else -> "📍"
}

@Composable
private fun HotelListCard(payload: HotelListCardPayload, onOpenLink: (String) -> Unit) {
  BusinessCardSurface(label = "酒店列表") { palette ->
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        "锚点 ${payload.anchorDestination}",
        style = MaterialTheme.typography.labelMedium,
        color = palette.contentColor,
      )
      Text(
        payload.filterSummary,
        style = MaterialTheme.typography.labelMedium,
        color = palette.supportingColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
  val minPrice = hotel.platformQuotes.minOfOrNull { it.price }
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = RoundedCornerShape(18.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
    shadowElevation = 0.dp,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(hotel.name, style = MaterialTheme.typography.titleSmall, color = WhaleInk)
      Text(
        "约 ${hotel.distanceKm} km · ${hotel.summary}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
      hotel.platformQuotes.forEach { quote ->
        PlatformQuoteRow(
          quote = quote,
          isLowestPrice = quote.price == minPrice,
          onOpenLink = onOpenLink,
        )
      }
    }
  }
}

@Composable
private fun PlatformQuoteRow(quote: HotelPlatformQuote, isLowestPrice: Boolean, onOpenLink: (String) -> Unit) {
  val priceColor = if (isLowestPrice) WhaleAccent else MaterialTheme.colorScheme.onSurfaceVariant
  val priceWeight = if (isLowestPrice) FontWeight.Bold else FontWeight.Normal
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(quote.platform, color = WhaleInk, style = MaterialTheme.typography.labelLarge)
      Text("¥${quote.price}", color = priceColor, style = MaterialTheme.typography.titleMedium, fontWeight = priceWeight)
    }
    OutlinedButton(
      onClick = { onOpenLink(quote.actionUri) },
      shape = RoundedCornerShape(14.dp),
      border = BorderStroke(1.dp, WhaleAccent.copy(alpha = 0.6f)),
      colors = ButtonDefaults.outlinedButtonColors(
        containerColor = WhaleAccent.copy(alpha = 0.08f),
        contentColor = WhaleAccent,
      ),
    ) {
      Text(quote.actionLabel, fontWeight = FontWeight.Medium)
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
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(marker, style = MaterialTheme.typography.labelSmall)
        Text("工具反馈", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        if (item.isStreaming) {
          Spacer(modifier = Modifier.width(6.dp))
          CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            strokeWidth = 1.6.dp,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
      Text(item.title, color = WhaleInk, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
      if (item.isStreaming) {
        Text(
          "执行中…",
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
          style = MaterialTheme.typography.labelSmall,
        )
      } else {
        Text(item.text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
      }
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
  return (lastItemBottom - info.viewportEndOffset).coerceAtLeast(0)
}

@Composable
private fun HotelPlatformStatusChip(status: HotelPlatformStatus) {
  Surface(
    color = if (status.hasMatches) WhaleSurface else MaterialTheme.colorScheme.surfaceContainerHighest,
    shape = RoundedCornerShape(8.dp),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        status.platform,
        color = if (status.hasMatches) WhaleInk else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
      )
      Text(
        status.statusText,
        color = if (status.hasMatches) WhaleAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        style = MaterialTheme.typography.labelSmall,
      )
    }
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
    TimelineItemRole.User -> "你"
    TimelineItemRole.Assistant -> "Deepseek"
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
                    label = "候选地点",
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
