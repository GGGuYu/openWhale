## ADDED Requirements

### Requirement: The chat demo SHALL provide a simplified chat surface shell
The system SHALL present the OpenWhale demo through a simplified chat-first shell instead of a debug-heavy dashboard layout.

#### Scenario: Show a compact top shell
- **WHEN** the user opens the main chat page
- **THEN** the top area shows the product name `OpenWhale`
- **THEN** the page keeps only compact model information and essential entry actions visible
- **THEN** large debug-oriented panels are not shown as the primary shell content

### Requirement: The chat shell SHALL expose settings and recent-history entry points
The system SHALL provide dedicated entry points for settings and recent conversation history without keeping those controls permanently expanded in the main chat surface.

#### Scenario: Open settings from the top shell
- **WHEN** the user taps the settings entry in the top shell
- **THEN** the app shows a settings surface that includes API key configuration

#### Scenario: Open recent history from the top shell
- **WHEN** the user taps the recent-history entry in the top shell
- **THEN** the app shows a recent conversation history surface as a dialog, sheet, or equivalent transient UI

### Requirement: Chat messages SHALL display lightweight role avatars
The system SHALL display a lightweight avatar marker for user and assistant messages to improve scanability in the timeline.

#### Scenario: Render a user message
- **WHEN** a user message is shown in the timeline
- **THEN** the message includes a user avatar marker

#### Scenario: Render an assistant message
- **WHEN** an assistant message is shown in the timeline
- **THEN** the message includes an assistant avatar marker

### Requirement: The input composer SHALL stay visually attached to the IME
The system SHALL avoid a large blank region between the bottom input composer and the on-screen keyboard when the IME is visible.

#### Scenario: Open the keyboard while editing a message
- **WHEN** the user focuses the input field and the IME appears
- **THEN** the composer moves with the keyboard
- **THEN** the gap between the composer and the IME remains compact and does not leave a large empty block
