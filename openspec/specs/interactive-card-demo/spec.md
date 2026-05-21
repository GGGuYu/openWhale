# interactive-card-demo Specification

## Purpose
TBD - created by archiving change build-mobile-agent-demo. Update Purpose after archive.
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

