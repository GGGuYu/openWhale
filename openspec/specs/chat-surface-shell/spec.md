# chat-surface-shell Specification

## Purpose
Define the visual presentation rules for the chat timeline shell, including avatars and card depth treatment.

## Requirements
### Requirement: Chat messages SHALL display lightweight role avatars
The system SHALL display a lightweight avatar marker for user and assistant messages to improve scanability in the timeline, and the avatar marker SHALL align with the start of the corresponding message block rather than hanging from the bottom edge of the bubble.

#### Scenario: Render a user message
- **WHEN** a user message is shown in the timeline
- **THEN** the message includes a user avatar marker

#### Scenario: Render an assistant message
- **WHEN** an assistant message is shown in the timeline
- **THEN** the message includes an assistant avatar marker

#### Scenario: Align avatar with the message start edge
- **WHEN** a user or assistant message spans one or more lines of content
- **THEN** the avatar marker aligns near the top of the message block so the speaker identity is visually attached to the beginning of that message

### Requirement: Timeline cards SHALL present subtle depth cues
The system SHALL present message and card surfaces with subtle depth cues so the chat timeline feels layered and readable without becoming visually heavy.

#### Scenario: Render a standard message bubble
- **WHEN** a user or assistant message bubble is rendered in the timeline
- **THEN** the bubble includes a subtle depth treatment appropriate to its role
- **THEN** the treatment remains lighter than a decorative or highly elevated card style

#### Scenario: Render a business card in the timeline
- **WHEN** an option card, route card, or hotel list card is rendered inside the timeline
- **THEN** the card presents enough elevation or shadow to read as a distinct interactive content block
- **THEN** the depth remains visually consistent with the simplified chat surface style
