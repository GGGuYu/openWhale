## Context

The target is a polished mobile demo, not a production-complete general agent. The demo must show a short connected workflow: the user asks to navigate to a destination, confirms the intended place from multiple candidates, views a route card, then asks for nearby cheap hotels and receives filtered hotel results based on the chosen destination. The first implementation can use fully mocked tool data, but the runtime must behave as if the data were real so the demo feels authentic and can later evolve into real integrations.

The current repository already has OpenSpec scaffolding but no existing capability specs. The local `/Users/bytedance/guyu/project/pi` project contains reusable ideas for an agent loop, provider/model registration, and tool execution, but its coding-focused tools and terminal UI should not be copied directly into this mobile demo.

The product motivation is very specific. Existing chat-first assistants often answer navigation and hotel questions by searching or summarizing second-hand content, while the intended demo should feel more like a task agent that prefers trusted platform tools, asks clarifying questions when the destination is ambiguous, and presents choices through interactive cards instead of long text. The demo should therefore feel smarter than a plain chatbox even though the first version uses scripted mock data.

The concrete scripted flow for the demo is:
1. The user says “导航到静安寺”.
2. The agent calls a destination search tool and receives several place candidates such as 静安寺, 静安公园, and 静安寺地铁站.
3. The agent emits a generic option card so the user can choose one candidate or provide custom input.
4. After destination selection, the agent calls a route tool and emits a route card with four tabs: drive, transit, walk, and bike, plus an external map action.
5. The user then says “这个附近的酒店帮我找个便宜的”.
6. The agent treats “这个附近” as the previously selected destination, asks for missing hotel filters through option cards, and collects at least price range and distance range.
7. The agent calls mock hotel tools representing Ctrip and Meituan, merges the scripted results, and emits a hotel list card with platform-specific prices and actions.

The implementation strategy is intentionally staged. Phase 1 proves the runtime foundation: model/provider access, basic chat, loop-based tool calling, mock tools, and configurable workflow prompts. Phase 2 proves the experience layer: card templates, interactive callbacks, and the connected navigation-to-hotel walkthrough that can be recorded in a product demo video.

## Goals / Non-Goals

**Goals:**
- Establish a phase-1 runtime with basic chat, provider-backed model access, looped tool calling, and configurable workflow prompts.
- Establish a phase-2 demo interaction layer with exactly three card templates: option card, route card, and hotel list card.
- Support a connected navigation-to-hotel workflow where card selections feed back into the session state and continue the agent loop.
- Keep the architecture compatible with later replacement of mock tools by real map and hotel platform APIs.

**Non-Goals:**
- Building a general-purpose autonomous mobile OS agent.
- Supporting arbitrary AI-generated HTML or a free-form UI DSL in the first demo.
- Integrating real Amap, Ctrip, or Meituan APIs in the first delivery.
- Solving broad ranking, recommendation, or data deduplication quality for production use.

## Decisions

### 1. Implement in two phases with a stable runtime before card UX
We will split implementation into:
- **Phase 1:** agent loop, model/provider access, basic chat, tool calling, and configurable workflow prompts.
- **Phase 2:** card template rendering, interactive card actions, and the scripted navigation-to-hotel workflow.

This separation keeps the runtime debuggable before adding UI-specific complexity.

**Alternatives considered:**
- Build cards and runtime together from day one. Rejected because debugging loop/tool behavior and UI behavior simultaneously would slow down the demo.
- Start with cards only and fake the loop. Rejected because the demo would not prove the actual product direction.

### 2. Reuse pi architectural patterns, not pi product surface
We will borrow the design direction of the loop and tool abstractions from `/Users/bytedance/guyu/project/pi`, especially the message → tool call → tool result → next turn cycle seen in `packages/agent/src/agent-loop.ts:155`, while omitting coding-agent-specific tools and TUI layers.

**Alternatives considered:**
- Port the entire coding-agent package. Rejected because most of it is terminal and coding oriented.
- Build an all-new runtime with no reference. Rejected because it would duplicate already-solved orchestration problems.

### 3. Use strong card templates instead of free-form HTML
The first demo will support only three structured card schemas:
- `option`
- `route`
- `hotel_list`

The agent may choose which template to emit, but the template shape and rendering remain owned by the app.

**Alternatives considered:**
- Let the model output HTML into a WebView. Rejected for the first version because it increases instability and reduces demo repeatability.
- Build a generic UI DSL first. Rejected because it is unnecessary for the narrow scripted demo.

### 4. Treat workflow prompts as configurable scenario packs
Scenario guidance will be stored as configurable workflow prompt content rather than being hardcoded into model logic. The workflow prompt pack will instruct the agent to prefer tools over search, ask for clarification with option cards, reuse the selected destination as the anchor for “nearby”, and keep responses short.

**Alternatives considered:**
- Hardcode scenario logic directly in application code. Rejected because the user explicitly wants workflows to remain configurable.
- Depend only on tool descriptions. Rejected because the two-step scenario needs stronger orchestration guidance than tool metadata alone.

### 5. Use deterministic mock tools and scripted data for the first demo
Destination search, route results, and hotel results will come from fixed mock responses. The mock results may vary slightly by tool, but they will be deterministic for the scripted demo flow so recorded videos remain stable.

**Alternatives considered:**
- Use live APIs immediately. Rejected because unstable network/data quality would make the demo harder to control.
- Fake results only in UI without tool execution. Rejected because the agent must still believe it is calling tools so the loop is realistic.

### 6. Harden the runtime around an explicit tool-message contract inspired by pi
After the first demo validation, we should tighten the runtime architecture so it behaves more like the cleaner loop layering seen in `/Users/bytedance/guyu/project/pi/packages/agent/src/agent-loop.ts:155`. The main follow-up is not to add more demo surfaces, but to reduce ambiguity in how cards, tool results, and session state flow through the loop.

The key hardening goals are:
- **Make explicit card tools the primary rendering path.** The current fallback planner remains useful as a guarded compatibility path, but the intended contract is “AI calls data tools, then AI calls card tools,” not “app guesses which card to show.”
- **Avoid delayed card flush behavior.** A card emitted by a card tool should surface in the same logical loop turn, instead of depending on a later assistant response to attach it.
- **Commit transcript and state together.** The loop should avoid mutating conversation history in-place while deferring state commit until the end, because a mid-loop failure can otherwise leave transcript and state out of sync.
- **Validate tool batches before applying them.** The runtime should explicitly guard constraints such as data-tools-first ordering, limiting conflicting card emissions in one turn, and preserving predictable card/tool semantics.
- **Prepare for cleaner event boundaries.** Over time, the runtime may benefit from separating loop-core message/tool flow from higher-level session policy concerns, similar to how `pi` distinguishes the low-level loop from session-level retry/continue policy.

### 7. Observed duplicate-card failure proves the current mixed-mode runtime is still leaky
During real-device validation, the destination flow exposed a concrete failure: after the user said “导航到静安寺”, the app showed two equivalent destination-disambiguation cards. Runtime logs showed one `search_destination` call followed by one explicit `emit_option_card`, which means the duplicate card was not caused by the model calling the card tool twice.

The most likely cause is the current overlap between:
- the compatibility fallback path in `AgentLoopRunner` that still calls `DemoCardPlanner.maybeBuildAssistantCardFallback(...)` before the loop sees whether the next model response will emit a real card tool, and
- the delayed `pendingCardPayload` flush model, where a card emitted by `emit_option_card` is only attached on the next assistant turn.

In practice, this means the runtime can first synthesize a destination option card from the prior `search_destination` data step, then also render the explicit `emit_option_card` result on the next turn, producing two user-facing cards for the same workflow step. This bug confirms that the current architecture is still mixed-mode rather than truly explicit-card-first.

**Design implication:**
- The fallback planner must not remain active on the main path once the model has adopted explicit card tools for that workflow.
- Card emission should become same-turn and single-source, so the runtime cannot accidentally materialize both a fallback card and an explicit card for the same step.

**Alternatives considered:**
- Keep the current mixed mode of explicit card tools plus silent fallback as the long-term architecture. Rejected because it hides prompt/tool contract regressions and makes runtime behavior harder to reason about.
- Force a full rewrite before the next demo iteration. Rejected because the current runtime already proves the interaction model; the next step should be a targeted hardening pass, not a ground-up rebuild.

## Risks / Trade-offs

- **[Risk]** Mock data may make the architecture look complete while hiding integration gaps. → **Mitigation:** keep tool interfaces realistic and preserve provider/tool boundaries for later real APIs.
- **[Risk]** A configurable workflow prompt can drift and cause inconsistent card output. → **Mitigation:** constrain the prompt to only three allowed card types and validate output shape before rendering.
- **[Risk]** Reusing pi concepts without careful trimming may pull in unnecessary coding-agent complexity. → **Mitigation:** copy only loop/tool/provider patterns and explicitly exclude coding tools and terminal UI.
- **[Risk]** The hotel scenario depends on state handoff from the route scenario. → **Mitigation:** store selected destination ID/name in session state and make “nearby” resolution an explicit workflow rule.
- **[Risk]** The runtime can still appear correct while relying on fallback card inference instead of explicit card tools. → **Mitigation:** add follow-up tasks that remove implicit card inference from the main path and treat it as a guarded compatibility-only fallback.
- **[Risk]** Partial loop failures can leave conversation transcript and session state inconsistent. → **Mitigation:** add a transactional commit boundary for loop results so history, state, and UI-visible outputs land together.
- **[Risk]** A workflow step can render duplicate cards when fallback synthesis and explicit card emission overlap. → **Mitigation:** explicitly add a bug-fix task to deduplicate same-step cards and remove fallback rendering from the primary explicit-card path.

## Migration Plan

This is a new capability, so rollout can happen behind a demo-only entry point. Phase 1 can ship as an internal runtime milestone without cards. Phase 2 can enable the full demo flow. Rollback is simple: disable the demo workflow entry and card rendering path while keeping the underlying runtime code isolated.

## Open Questions

- Should workflow prompt packs be selected by local config file, remote config, or an in-app settings panel in the first implementation?
- Should route tab switching be handled entirely in the card renderer, or should it be able to request fresh tool data later when real APIs are added?
- Do we want one generic “option” action callback format for all cards, or separate callback payload shapes per card type?
