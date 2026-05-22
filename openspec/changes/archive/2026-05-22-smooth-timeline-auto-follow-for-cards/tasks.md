## 1. Scroll trigger coverage

- [x] 1.1 Audit the current timeline follow trigger so card insertion, tool feedback, and other non-text tail updates participate in pinned-bottom auto-follow.
- [x] 1.2 Normalize latest-content change detection around the tail of the timeline instead of relying only on plain text growth.

## 2. Smooth follow behavior

- [x] 2.1 Replace abrupt pinned-bottom jumps with smooth animated follow behavior for latest timeline updates.
- [x] 2.2 Add lightweight coalescing or equivalent control so rapid successive updates remain readable and do not jitter while auto-follow is active.

## 3. Validation

- [x] 3.1 Add or update UI tests covering pinned-bottom follow for text, tool feedback, and output-card insertion.
- [ ] 3.2 Manually validate that leaving the bottom suppresses auto-follow and that returning to the bottom restores smooth following behavior.
