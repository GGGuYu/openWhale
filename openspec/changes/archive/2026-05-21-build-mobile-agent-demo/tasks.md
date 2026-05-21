## 1. Phase 1 Runtime Foundation

- [x] 1.1 Inspect the existing app structure and identify where to place the mobile agent session runtime, provider configuration, and tool registry.
- [x] 1.2 Implement a basic chat session that sends user messages to a configured model provider and appends assistant responses to session history.
- [x] 1.3 Implement the core agent loop for structured tool calling, tool result injection, and continued turns, reusing the useful abstractions from the local `pi` project without importing coding-specific tools.
- [x] 1.4 Add provider/model configuration for the initial large model connection and verify the runtime can switch workflow prompt packs without code changes.
- [x] 1.5 Add session state storage for selected destination and hotel filter context so later turns can resolve “nearby” correctly.

## 2. Phase 1 Mock Tools and Workflow Prompt Packs

- [x] 2.1 Implement the mock destination search tool with deterministic candidate results for the scripted demo.
- [x] 2.2 Implement the mock route tool that returns four route modes for the selected destination.
- [x] 2.3 Implement mock hotel tools for both Ctrip and Meituan with deterministic but slightly different hotel result payloads.
- [x] 2.4 Create a configurable workflow prompt pack for the navigation-to-hotel scenario that instructs the agent to prefer tools, ask clarifying questions with option cards, and keep responses concise.
- [x] 2.5 Verify the agent can complete the scripted conversation in text/tool form before any custom card rendering is added.

## 3. Phase 2 Card Templates and Interaction Plumbing

- [x] 3.1 Implement the generic option card template and render destination, price, and distance choices through the same component.
- [x] 3.2 Implement the route card template with four route tabs and an external map action.
- [x] 3.3 Implement the hotel list card template with platform-specific pricing and per-platform open actions.
- [x] 3.4 Implement a structured callback path that converts card selections into session inputs and resumes the agent loop.
- [x] 3.5 Validate card payload shapes before rendering so the app only accepts the allowed demo card types.

## 4. Scripted Demo Integration and Validation

- [x] 4.1 Connect the workflow prompt pack, mock tools, session state, and card templates into the full navigation-to-hotel demo path.
- [x] 4.2 Tune the scripted demo data and assistant wording so the recorded walkthrough remains visually consistent and believable.
- [x] 4.3 Test the end-to-end flow: destination disambiguation → route card → hotel filters → hotel list card → platform action.
- [x] 4.4 Add lightweight debug logging or inspection hooks so runtime, tool, and card issues can be diagnosed quickly during demo preparation.
- [x] 4.5 Lock the demo entry to portrait orientation so real-device demos remain stable and do not require rotation recovery in the first delivery.
- [x] 4.6 Add runtime debug logs for provider requests, tool calls, tool results, and loop turn transitions so on-device troubleshooting is straightforward during demos.
- [x] 4.7 Add an in-app entry that lets the user configure and update the DeepSeek API key locally so the demo can be tested on-device without rebuilding the app.
- [x] 4.8 Restyle the tool-call hint UI into a dedicated card so tool execution feedback looks more polished during the demo without changing the underlying runtime behavior.

## 5. Demo UX Follow-up Tasks

- [x] 5.1 Make the hotel result card render even when only one platform returns usable hotels, and clearly show when another platform returned no matching results for the current filters.
- [x] 5.2 Add a markdown rendering task so assistant messages with markdown emphasis and list structure display correctly in the chat timeline.
- [x] 5.3 Replace option-card custom input fields with a non-interactive hint row that reminds the user they can continue in natural language without showing an embedded text box.
- [x] 5.4 Disable option cards that have already triggered a session callback so historical choice cards cannot be tapped again, while keeping non-callback cards such as route cards reusable from history.
- [x] 5.5 Refactor the runtime architecture so card rendering is no longer produced mainly by app-side guessing or tool-side implicit payload attachment, and document the migration target as an explicit “AI calls data tools, then AI calls card tools” loop. This task is expected to touch the current loop, tool registry, and card planning path.
- [x] 5.6 Expose structured card-emission tools to the model as first-class tools, at minimum covering `option`, `route`, and `hotel_list`, with strict schema validation and stable payload contracts owned by the app.
- [x] 5.7 Update the agent loop so the model can first call official data-query tools (for example map / Ctrip / Meituan API wrappers) and then, in the same loop, explicitly call a card tool to emit the next user-facing card, instead of relying on the app to infer the card from response text.
- [x] 5.8 Migrate existing demo flows to the new two-step tool pattern and remove the current app-side card guessing path as the primary mechanism, while keeping a minimal guarded fallback only if needed for backward compatibility during transition.

## 6. Runtime Architecture Hardening Follow-up

- [x] 6.1 Remove fallback card inference from the main runtime path and keep any remaining implicit planner behavior behind a clearly guarded compatibility/debug switch so prompt-tool contract regressions cannot hide inside normal demos.
- [x] 6.2 Refactor card-emission handling so a card tool can surface its user-facing card within the same logical loop turn, instead of relying on a later assistant turn to flush `pendingCardPayload`.
- [x] 6.3 Make loop execution commit transcript updates, session-state updates, and UI-visible timeline outputs atomically on success, so mid-loop failures cannot leave `conversationHistory` ahead of `sessionContextState`.
- [x] 6.4 Add tool-batch validation rules for the core workflow, including data-tools-first ordering, limits on conflicting card emissions within one turn, and clearer handling of duplicate or invalid tool calls.
- [x] 6.5 Reshape tool execution around clearer prepare / execute / finalize phases, borrowing the useful runtime hygiene from `/Users/bytedance/guyu/project/pi/packages/agent/src/agent-loop.ts`, while keeping the mobile demo scope and avoiding coding-agent complexity.
- [x] 6.6 Tighten runtime event semantics so tool-execution feedback, tool-result transcript entries, and user-facing card rendering have distinct responsibilities and do not depend on ambiguous cross-turn side effects.
- [x] 6.7 Fix the observed duplicate destination-card bug from real-device testing, where `search_destination` plus `emit_option_card` can still yield two equivalent user-facing cards because fallback card synthesis and delayed `pendingCardPayload` flush overlap in the current loop.
