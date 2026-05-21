## Why

We want a phone-first agent demo that feels more useful than a generic chatbox by using trusted platform tools, asking for clarification with interactive UI, and carrying context across a short multi-step task. We should build this now as a tightly scoped demo with mock data so we can validate the product interaction, record a convincing walkthrough, and establish the runtime foundation for later real integrations.

## What Changes

- Add a mobile agent runtime that supports basic chat, multi-provider model access, agent loop continuation, and tool calling inspired by the reusable loop and tool abstractions in `/Users/bytedance/guyu/project/pi`.
- Add configurable workflow prompts so the app can switch or tune scenario-specific agent behavior without rebuilding the runtime.
- Add a demo interaction layer based on three card templates: a generic option card, a route display card, and a hotel list card.
- Add mock tools and scripted demo data for a connected two-step flow: destination selection and route display, followed by nearby budget hotel discovery using the selected destination as context.
- Add interactive card actions so user selections feed back into the agent loop and allow the agent to continue the workflow.

## Capabilities

### New Capabilities
- `mobile-agent-runtime`: Provide a mobile-friendly agent runtime with basic chat, configurable model/provider access, loop-based tool calling, and configurable workflow prompts.
- `interactive-card-demo`: Provide template-based interactive cards and mock tool workflows for the navigation-to-hotel demo scenario.

### Modified Capabilities
- None.

## Impact

- Affected systems: mobile chat UI, agent runtime/session state, tool execution layer, card rendering layer, workflow prompt configuration, and demo data fixtures.
- External integrations for the first phase remain mocked, but the architecture must leave room for later DeepSeek/provider setup and future real map/hotel APIs.
- The implementation will likely reuse patterns from the local `pi` project for agent loop, tool abstraction, and provider/model configuration while adapting them for a mobile demo context.
