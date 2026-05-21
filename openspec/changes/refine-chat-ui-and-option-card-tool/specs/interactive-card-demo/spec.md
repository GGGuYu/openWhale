## MODIFIED Requirements

### Requirement: The demo SHALL support a reusable option card template
The system SHALL provide a generic option card template that can display a title, an optional description, a list of selectable options, and an optional custom-input hint, while allowing the agent to fill the main card copy and option content through explicit tool parameters.

#### Scenario: Ask the user to disambiguate a destination with agent-authored card copy
- **WHEN** the destination search tool returns multiple place candidates for a navigation request
- **THEN** the assistant emits an option card with agent-provided title, description, and option labels
- **THEN** the option card allows the user to choose one candidate or choose custom input

#### Scenario: Emit a generic option card outside fixed preset kinds
- **WHEN** the workflow needs to ask the user for a constrained choice
- **THEN** the assistant can emit an option card by filling card title, description, options, and callback prompt text directly through the option-card tool
- **THEN** the app validates the payload against the allowed option-card schema before rendering

### Requirement: Card actions SHALL continue the workflow
The system SHALL translate card interactions into structured inputs that feed back into the agent session so the workflow can continue without forcing the user to restate the same information in text.

#### Scenario: Continue after an option card selection
- **WHEN** the user taps an option on an interactive card
- **THEN** the app sends the selected value back into the agent session as structured input
- **THEN** the agent continues the current workflow from that point

#### Scenario: Continue after selecting an agent-authored generic option
- **WHEN** the assistant emits a generic option card with explicit callback prompt text per option
- **THEN** the app uses the selected option's callback data to resume the workflow
- **THEN** the user does not need to manually restate the same choice in natural language

## ADDED Requirements

### Requirement: Tool execution feedback SHALL be visually de-emphasized
The system SHALL render tool execution feedback with lower visual weight than standard assistant content and user-facing cards.

#### Scenario: Show tool execution feedback in the timeline
- **WHEN** a tool execution feedback item is rendered in the chat timeline
- **THEN** its typography and spacing are smaller or lighter than normal content cards
- **THEN** it reads as auxiliary feedback rather than primary conversational content
