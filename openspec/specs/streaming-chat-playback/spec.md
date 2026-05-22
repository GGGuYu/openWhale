# streaming-chat-playback Specification

## Purpose
Define how streamed assistant responses are rendered and kept visible in the chat timeline.

## Requirements
### Requirement: Assistant responses SHALL support incremental streaming playback
The system SHALL support rendering an assistant response incrementally as content arrives from the provider instead of waiting for the full response to complete before showing any text.

#### Scenario: Render a streaming assistant response
- **WHEN** the active model request is executed with streaming enabled
- **THEN** the chat timeline shows one in-progress assistant message that grows as new content chunks arrive
- **THEN** the user can read the partial response before the final chunk is received

### Requirement: Streaming playback SHALL produce a typewriter-like reading experience
The system SHALL present streamed assistant content as a progressively revealed message rather than a burst of separate mini-messages.

#### Scenario: Reveal streamed content in one logical message
- **WHEN** the provider emits multiple content chunks for one assistant turn
- **THEN** the runtime appends those chunks into one logical assistant timeline item
- **THEN** the UI reads as a single assistant reply being typed out rather than multiple fragmented replies

### Requirement: Streaming playback SHALL cooperate with latest-message visibility
The system SHALL keep streamed content visible while the user remains near the latest message, without overriding a deliberate user scroll away from the bottom.

#### Scenario: Stay pinned to the latest streamed content
- **WHEN** the user is already near the latest timeline item and a streamed assistant response continues to grow
- **THEN** the timeline remains visually pinned near the bottom so new content stays visible

#### Scenario: Respect a user who scrolled away from the bottom
- **WHEN** the user has intentionally scrolled upward away from the latest messages while a streamed assistant response continues
- **THEN** the timeline does not force-scroll back to the bottom on every streamed update
