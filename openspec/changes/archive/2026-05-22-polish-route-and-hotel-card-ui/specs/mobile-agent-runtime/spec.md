## MODIFIED Requirements

### Requirement: Route card SHALL display all travel modes without requiring horizontal scroll
路线卡片 SHALL 在单一视口内完整展示 4 个出行方式（驾车/公交/骑行/步行），用户无需横向滑动即可对比所有选项。

#### Scenario: All four travel modes visible at once
- **WHEN** a route card with 4 travel modes is displayed
- **THEN** all 4 mode tabs are visible within the card width without horizontal scrolling
- **THEN** the active tab is visually distinguished

### Requirement: Route card stat display SHALL use visual hierarchy
路线卡片的统计信息（方式、耗时、距离）SHALL 使用清晰的视觉层级和图标辅助，而非纯文字 badge 堆叠。

#### Scenario: Route stats are scannable
- **WHEN** viewing a route card
- **THEN** travel mode, duration, and distance are displayed with visual distinction (icons, size hierarchy)
- **THEN** the most important metric (duration) is visually prominent

### Requirement: Hotel list card SHALL emphasize price comparison
酒店列表卡片 SHALL 强化价格信息的视觉权重，使平台比价关系一目了然。

#### Scenario: Price comparison is visually clear
- **WHEN** a hotel list card is displayed
- **THEN** the lowest price among platforms is visually emphasized
- **THEN** platform names and prices are clearly associated
