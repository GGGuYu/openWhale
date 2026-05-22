# mobile-agent-runtime Specification

## Purpose
Define the runtime behavior for mobile agent sessions, provider integration, tool execution, and chat-timeline playback.

## Requirements
### Requirement: Mobile agent sessions SHALL support provider-backed basic chat
The system SHALL provide a mobile-oriented agent session that can accept user chat messages, send them to a configured large model provider, and append assistant responses to the same session history. The runtime SHALL support selecting from a constrained set of supported model IDs, including a DeepSeek v4 flash-tier model, without requiring runtime code changes for each switch.

#### Scenario: Start a basic chat session
- **WHEN** a user opens the demo chat and sends a plain text message that does not require tools
- **THEN** the system sends the message to the configured model provider
- **THEN** the assistant response is returned in the same chat session

#### Scenario: Start phase-1 runtime with one configured model provider
- **WHEN** the demo runtime is initialized for the first implementation
- **THEN** the system can run with a single configured provider/model pair while keeping provider and workflow configuration replaceable
- **THEN** later provider swaps do not require rewriting the agent loop

#### Scenario: Switch to a supported model variant
- **WHEN** the user or local runtime configuration selects another supported model ID such as `deepseek-v4-flash`
- **THEN** the next request uses that selected model ID for provider calls
- **THEN** the session runtime and workflow loop continue to work without a separate provider implementation

### Requirement: Mobile agent sessions SHALL support looped tool calling
The system SHALL support an agent loop that can inspect a model response, execute declared tool calls, append tool results back into the session, and continue the session until the assistant reaches a normal response or waits for user interaction.

#### Scenario: Continue after a tool call
- **WHEN** the assistant decides to call a registered tool during a session
- **THEN** the system executes the tool with structured arguments
- **THEN** the tool result is appended to the session as model-visible context
- **THEN** the agent loop continues so the assistant can respond based on the tool result

### Requirement: Workflow behavior SHALL be configurable without changing runtime code
The system SHALL support configurable workflow prompt content that can guide the agent toward scenario-specific behavior, including preferred tool usage, clarification strategy, and allowed card outputs, without requiring runtime code changes.

#### Scenario: Load a scenario workflow prompt
- **WHEN** the demo enables the navigation-to-hotel workflow
- **THEN** the agent session loads the configured workflow prompt content for that scenario
- **THEN** the prompt content constrains the agent to use the expected tool-and-card behavior for the scenario

### Requirement: Session state SHALL preserve selected workflow context across turns
The system SHALL preserve structured session state required for short multi-step workflows, including the currently selected destination and previously collected hotel filter values.

#### Scenario: Reuse selected destination in a follow-up turn
- **WHEN** a user first selects a destination and then asks for a nearby hotel in a later turn
- **THEN** the system resolves “nearby” against the previously selected destination stored in session state
- **THEN** the agent can continue the workflow without asking the user to restate the destination

### Requirement: User-facing cards SHALL be emitted primarily through explicit card tools
The system SHALL treat user-facing cards as app-owned, explicitly emitted outputs. Data-query tools may update runtime state and return model-visible data, but the primary path for rendering option, route, and hotel-list cards SHALL be an explicit card-emission tool call instead of app-side guessing or tool-side implicit payload attachment.

#### Scenario: Query data first, then emit a card
- **WHEN** the agent has called a destination, route, or hotel data tool and decides the next step should be a user-facing card
- **THEN** the agent explicitly calls the matching card-emission tool for that card type
- **THEN** the runtime renders the emitted card using the app-owned schema contract
- **THEN** the main workflow does not depend on assistant text heuristics to infer the card

### Requirement: Card emission SHALL complete within the same logical loop turn
The system SHALL not require an extra model round-trip just to flush a card that was already emitted by a card tool in the current turn.

#### Scenario: Show a card immediately after a card tool executes
- **WHEN** the agent emits a valid option, route, or hotel-list card during a loop turn
- **THEN** the runtime attaches that card to the current user-visible assistant output for that turn, or otherwise surfaces it immediately in the same logical turn
- **THEN** the card does not remain hidden only because another model turn has not yet happened

### Requirement: Loop state and transcript updates SHALL commit atomically per successful run
The system SHALL avoid partially committing conversation transcript updates without the corresponding session-state updates when a loop run fails partway through.

#### Scenario: A tool fails after partial progress inside a loop run
- **WHEN** the runtime encounters an error after one or more intermediate assistant or tool messages were prepared during a loop run
- **THEN** the runtime either atomically commits the full successful result of that run or rolls back the partial updates as one unit
- **THEN** the persisted conversation transcript and session state remain mutually consistent for the next turn

### Requirement: Tool batches SHALL enforce execution-order constraints
The runtime SHALL validate tool batches before execution so the card-emission contract remains stable and predictable.

#### Scenario: Validate tool-call ordering within a turn
- **WHEN** the model returns a batch of tool calls for one loop turn
- **THEN** the runtime enforces the expected constraints for that workflow, including limiting conflicting card emissions and preserving a stable order between data tools and card tools
- **THEN** invalid or conflicting tool batches are rejected, normalized, or surfaced as structured runtime errors instead of being applied silently

### Requirement: The runtime SHALL avoid duplicate user-facing cards for the same workflow step
The runtime SHALL not surface two equivalent user-facing cards for the same workflow step when one card already came from an explicit card-emission tool.

#### Scenario: A destination disambiguation card is emitted explicitly after destination search
- **WHEN** the agent first calls `search_destination` and then explicitly calls `emit_option_card` with `card_kind=destination_candidates`
- **THEN** the runtime surfaces exactly one destination disambiguation card for that workflow step
- **THEN** any fallback or compatibility card-generation path does not emit a second equivalent card for the same step

### Requirement: The runtime SHALL support streaming provider responses
The runtime SHALL support provider requests that return incremental assistant content chunks through the existing chat session flow.

#### Scenario: Execute a chat turn with streaming enabled
- **WHEN** the active model configuration enables streaming for a supported provider request
- **THEN** the runtime receives assistant content incrementally instead of waiting only for a single final text payload
- **THEN** the session state can expose those partial updates to the UI during the same assistant turn

### Requirement: Thinking-mode metadata SHALL not break subsequent chat turns
When the provider emits hidden thinking / reasoning content, the runtime SHALL either preserve the required provider-side metadata across rounds or explicitly disable that mode so a later user turn does not fail because `reasoning_content` was dropped.

#### Scenario: Continue chatting after a provider emits reasoning metadata
- **GIVEN** the provider has returned a response in thinking mode with hidden reasoning metadata
- **WHEN** the user sends the next message in the same session
- **THEN** the runtime does not trigger a provider `400 invalid_request_error` complaining that `reasoning_content` must be passed back to the API
- **THEN** the user-visible timeline still only shows reply正文，而不是内部 thinking 内容

### Requirement: The chat timeline SHALL auto-follow new messages only while pinned near the bottom
The runtime and UI SHALL cooperate so that new assistant or tool outputs remain visible when the user is still near the latest message, while preserving the user's manual scroll position once the user has intentionally moved away from the bottom.

#### Scenario: Auto-follow the latest message when user remains near bottom
- **WHEN** the user is still at or near the latest timeline item and a new assistant or tool output arrives
- **THEN** the UI scrolls as needed so the latest output remains visible

#### Scenario: Do not interrupt manual review of older messages
- **WHEN** the user has scrolled upward away from the latest timeline content and a new assistant or tool output arrives
- **THEN** the UI does not forcibly jump back to the bottom
- **THEN** the user can continue reviewing older messages until they intentionally return to the latest position
