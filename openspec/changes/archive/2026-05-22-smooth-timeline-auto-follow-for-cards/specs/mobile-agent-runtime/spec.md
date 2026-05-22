## MODIFIED Requirements

### Requirement: The chat timeline SHALL auto-follow new messages only while pinned near the bottom
The runtime and UI SHALL cooperate so that new assistant, tool, and card outputs remain visible through smooth follow behavior while the user is still near the latest message, while preserving the user's manual scroll position once the user has intentionally moved away from the bottom.

#### Scenario: Auto-follow the latest non-text output when user remains near bottom
- **WHEN** the user is still at or near the latest timeline item and a new card, tool feedback item, or other non-text output arrives
- **THEN** the UI scrolls as needed so the latest output remains visible
- **THEN** the follow motion is presented smoothly enough to read as a natural continuation instead of an abrupt jump

#### Scenario: Auto-follow continuing latest output smoothly while pinned near bottom
- **WHEN** the user is still at or near the latest timeline item and the latest assistant or tool turn continues to append visible content
- **THEN** the UI keeps the newest content in view as needed
- **THEN** it does so with smooth follow behavior rather than a forced instant snap on each update

#### Scenario: Do not interrupt manual review of older messages
- **WHEN** the user has scrolled upward away from the latest timeline content and a new assistant, card, or tool output arrives
- **THEN** the UI does not forcibly jump back to the bottom
- **THEN** the user can continue reviewing older messages until they intentionally return to the latest position
