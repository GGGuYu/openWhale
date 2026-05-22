# interactive-card-demo Specification

## Purpose
Define the interactive card types and interaction contracts used by the mobile agent demo chat workflow.

## Requirements
### Requirement: The demo SHALL support a reusable option card template
The system SHALL provide a generic option card template that can display a title, a list of selectable options, and an optional custom-input path for cases where the predefined options are insufficient.

#### Scenario: Ask the user to disambiguate a destination
- **WHEN** the destination search tool returns multiple place candidates for a navigation request
- **THEN** the assistant emits an option card with the candidate destinations
- **THEN** the option card allows the user to choose one candidate or choose custom input

### Requirement: The demo SHALL support a route display card template
The system SHALL provide a route card template that displays the selected destination, route summaries for driving, transit, walking, and cycling, and an action to open the external map app.

#### Scenario: Show route choices after destination confirmation
- **WHEN** the user confirms a destination candidate
- **THEN** the assistant emits a route card based on the route tool result
- **THEN** the route card shows four route modes and a map-open action

### Requirement: The demo SHALL support a hotel list card template
The system SHALL provide a hotel list card template that displays nearby hotel results, includes platform-specific pricing, and exposes actions to open the hotel on each source platform.

#### Scenario: Show hotel results after filters are collected
- **WHEN** the user has provided enough hotel search parameters for the demo workflow
- **THEN** the assistant emits a hotel list card with the returned hotel items
- **THEN** each hotel item includes pricing or label differences from multiple platforms and platform-specific open actions

### Requirement: Card actions SHALL continue the workflow
The system SHALL translate card interactions into structured inputs that feed back into the agent session so the workflow can continue without forcing the user to restate the same information in text.

#### Scenario: Continue after an option card selection
- **WHEN** the user taps an option on an interactive card
- **THEN** the app sends the selected value back into the agent session as structured input
- **THEN** the agent continues the current workflow from that point

### Requirement: The demo SHALL support the scripted navigation-to-hotel flow with mock tools
The system SHALL support a deterministic demo flow in which the user asks to navigate to a destination, confirms a specific place, then requests a cheap nearby hotel and receives consistent scripted hotel results driven by mock tools.

#### Scenario: Complete the scripted demo flow
- **WHEN** the user follows the scripted navigation-to-hotel conversation for the demo
- **THEN** the system uses mock destination, route, and hotel tools instead of web search
- **THEN** the user can complete the flow through cards and end on a hotel platform action

#### Scenario: Follow the concrete Jing'an Temple demo path
- **WHEN** the user asks to navigate to Jing'an Temple and later asks for a cheap nearby hotel
- **THEN** the system first presents destination disambiguation choices
- **THEN** the system presents a route card after destination confirmation
- **THEN** the system collects hotel price and distance filters through option cards before presenting hotel results

### Requirement: Tool execution feedback SHALL include a lightweight tool marker
The system SHALL render tool execution feedback with a compact visual marker, such as an emoji or equivalent lightweight glyph, that helps users immediately recognize it as auxiliary tool activity.

#### Scenario: Show a tool marker in the timeline
- **WHEN** a tool execution feedback item is rendered in the chat timeline
- **THEN** the item includes a lightweight tool marker near the tool label
- **THEN** the marker remains visually subordinate to the tool text and does not overpower the surrounding content

### Requirement: User-facing cards SHALL expose depth cues distinct from tool feedback
The system SHALL use visual depth cues such as subtle shadow or elevation differences so interactive cards and user-facing business cards read as more important than tool feedback items.

#### Scenario: Compare a business card and a tool feedback item
- **WHEN** a route card, hotel list card, or option card is shown in the same timeline as tool feedback
- **THEN** the business card presents stronger visual depth than the tool feedback item
- **THEN** the tool feedback still remains readable without competing for primary attention

### Requirement: Hotel clarification option cards SHALL render with valid callback payloads
The system SHALL render hotel-filter clarification cards with a validator-compatible payload so the nearby-hotel flow can continue through structured budget or distance choices instead of failing at runtime.

#### Scenario: Render a budget clarification card after the user asks for a cheaper nearby hotel
- **GIVEN** the session already has a selected destination anchor
- **AND** the user asks for a nearby cheap hotel without supplying a hotel budget
- **WHEN** the assistant emits an option card to clarify the missing budget filter
- **THEN** the timeline renders that budget option card successfully
- **THEN** the app does not surface a generic `option` card validation failure in place of the card

#### Scenario: Render a distance clarification card after budget has been collected
- **GIVEN** the session already has a selected destination anchor and a confirmed hotel budget
- **AND** the user still has not supplied a maximum hotel distance
- **WHEN** the assistant emits an option card to clarify the missing distance filter
- **THEN** the timeline renders that distance option card successfully
- **THEN** the follow-up hotel search can continue through the structured option-card callback path
