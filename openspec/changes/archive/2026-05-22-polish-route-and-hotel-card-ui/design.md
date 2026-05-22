## Context

当前 `RouteCard` 使用 `SecondaryScrollableTabRow` 承载 4 个出行方式 tab（驾车/公交/骑行/步行），在手机上 4 个 tab 无法在一屏内完整展示，用户必须横向滑动才能看到全部选项。`RouteModePanel` 内的统计信息用 `StatBadge`（"方式 · xxx" "耗时 · xxx" "距离 · xxx"）平铺展示，视觉层级单调。

`HotelListCard` 结构合理（BusinessCardSurface + HotelRow），但酒店行的平台报价排列和价格强调可以优化。

**参考设计原则：**
- Material Design 3 的卡片层次和间距指南
- 移动端聊天 UI 的紧凑性与可读性平衡
- 演示视频需要视觉吸引力，信息层级要清晰

## Goals / Non-Goals

**Goals:**
- 路线卡片 4 个出行方式 tab 一屏可见，不需横向滚动
- 路线统计信息使用更清晰的视觉层级（图标 + 字号对比 + 间距）
- 酒店列表的价格对比在一眼内可辨识
- 选项卡片保持现有效果不变
- 所有改动保持现有 BusinessCardSurface 容器风格一致性

**Non-Goals:**
- 不改动卡片的数据模型或 agent 协议
- 不添加真实地图图片渲染
- 不改变选项卡片的任何视觉

## Decisions

### 1. 路线卡片 Tab 改为等宽紧凑布局

用 `Row` + 等宽 `FilterChip` 替代 `SecondaryScrollableTabRow`，使 4 个 tab 均分卡片宽度，不再需要横向滚动。每个 chip 内显示图标 + 简短标签（如 🚗 驾车）。

### 2. 统计信息改为带图标的两行布局

废除 `StatBadge` 的 "标签 · 值" 格式，改为：
- 左侧大号时间数字（最关键的决策信息）
- 右侧图标 + 距离
- 上方出行方式名称

### 3. 酒店价格行强化最低价

在 `PlatformQuoteRow` 中，对最低价格使用 `WhaleAccent` 强调色 + SemiBold 字重，其他平台保持常规样式，让用户一眼看出哪个平台最便宜。

## Risks / Trade-offs

- [风险] 4 个 tab 压缩到一屏后文字可能被截断 → [缓解] 使用图标辅助 + 2 字短标签（驾车/公交/骑行/步行）
- [风险] 酒店价格强调逻辑变更可能影响 PlatformQuoteRow 的其他使用场景 → [缓解] 该组件只在 HotelRow 内使用，改动范围可控
