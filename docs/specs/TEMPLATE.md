# <Feature name>

<!--
Copy this file to docs/specs/<NN>-<feature>.md, one spec per queue row.
Name frames and states exactly as the design prototype does.
-->

## Goal

One or two sentences: what the user can do once this ships, and why.

## Frames

The prototype frames this spec covers, with every state the frame can be in.

| Frame | States | Design source |
| --- | --- | --- |
| `<Frame name>` | `<state>`, `<state>` | `ctx Prototype.dc.html`, lines `<from>–<to>` |

## Acceptance criteria

Each criterion is one observable behaviour with at least one test that checks it.

- [ ] <Given …, when …, then …> — `<TestClass.testName>`
- [ ] …

## Data contract

What the feature reads and writes. Leave a section out when the feature does not need it.

- **Module and package:** `:data:<name>`, `br.com.weslleycampos.chat.data.<name>`
- **Repository:**

  ```kotlin
  interface <Name>Repository {
      fun observe…(): Flow<…>
      suspend fun …(…)
  }
  ```

- **Models:** `<Model>(field: Type, …)`
- **Storage:** Room entities, DAOs, database version and auto-migration in `:core:database`
- **Koin binding:** `<Implementation>` bound as `<Interface>` in `<Module>`
- **Behaviour:** what the implementation returns, its ordering, defaults and timing

## Decided

Choices the design or this spec left open, and what was picked.

- Decided: <choice> — <why it fits the frames and the existing code>

## Out of scope

- <What this spec does not cover, and where it goes instead>
