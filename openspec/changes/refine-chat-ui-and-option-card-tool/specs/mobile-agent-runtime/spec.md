## MODIFIED Requirements

### Requirement: Workflow behavior SHALL be configurable without changing runtime code
The system SHALL support configurable workflow prompt content that can guide the agent toward scenario-specific behavior, including preferred tool usage, clarification strategy, and allowed card outputs, without requiring runtime code changes.

#### Scenario: Load a scenario workflow prompt
- **WHEN** the demo enables the navigation-to-hotel workflow
- **THEN** the agent session loads the configured workflow prompt content for that scenario
- **THEN** the prompt content constrains the agent to use the expected tool-and-card behavior for the scenario

#### Scenario: Use a more general option-card tool in workflow prompts
- **WHEN** the workflow prompt instructs the assistant to ask the user for a choice
- **THEN** the prompt can guide the model to fill the generic option-card tool parameters directly instead of relying only on fixed preset card kinds

### Requirement: User-facing cards SHALL be emitted primarily through explicit card tools
The system SHALL treat user-facing cards as app-owned, explicitly emitted outputs. Data-query tools may update runtime state and return model-visible data, but the primary path for rendering option, route, and hotel-list cards SHALL be an explicit card-emission tool call instead of app-side guessing or tool-side implicit payload attachment.

#### Scenario: Query data first, then emit a card
- **WHEN** the agent has called a destination, route, or hotel data tool and decides the next step should be a user-facing card
- **THEN** the agent explicitly calls the matching card-emission tool for that card type
- **THEN** the runtime renders the emitted card using the app-owned schema contract
- **THEN** the main workflow does not depend on assistant text heuristics to infer the card

#### Scenario: Emit a generic option card with structured payload
- **WHEN** the assistant needs to ask the user for a bounded choice
- **THEN** the runtime accepts a structured option-card payload containing the main card copy and selectable options from the model
- **THEN** the runtime validates the payload before rendering it

## ADDED Requirements

### Requirement: Runtime-facing settings access SHALL remain separate from the main chat body
The system SHALL allow runtime configuration access, including API key management, without requiring that those controls remain permanently expanded in the main chat body.

#### Scenario: Update API key through settings entry
- **WHEN** the user opens the settings surface from the chat shell
- **THEN** the user can view and update the local API key there
- **THEN** the main chat body remains focused on conversation content rather than configuration forms

### Requirement: Recent conversation history SHALL be exposable from the chat shell
The system SHALL expose a recent-history entry from the main chat shell so the user can inspect recent conversation sessions through a transient UI surface.

#### Scenario: Open recent history
- **WHEN** the user taps the recent-history entry
- **THEN** the app presents a recent-history surface without replacing the main chat page
