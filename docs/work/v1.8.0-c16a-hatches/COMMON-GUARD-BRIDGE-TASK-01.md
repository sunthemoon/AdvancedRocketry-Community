# C16a common save-guard bridge

Date: 2026-10-07. Owner/integrator: Root. Status: in-progress.
Task ID: C16a-03b-COMMON-GUARD-01. Base source:
`1dbe46271d6e92434bb16f8137a293f6eaa1d6d3` on
`codex/v1.8.0-classic-content`. Independent reviewer is assigned separately.

## Scope and authority

Implement only the three common-helper signatures in the already adopted
[ordinary-cut inventory](GUARD-INVENTORY-ACCEPTANCE-01.md), proposal
`77cb3f909cd645828f08c4ba60d4db89939b8cabd533d98278f31c30cda0ef9e`,
its JAVA-SIGNATURES section 7.2: `recordObservedDenial(ServerLevel, ChunkPos,
String)`, `defer(ChunkDataEvent.Save, String)`, and `serverStopped(ServerStoppedEvent)`.
This does not adopt pending GuardTicket/frame/physical-hatch or power-mode code.

Root's exclusive write scope is the existing
`persistence/GuardedChunkSaves.java`, a new `gametest/ClassicSaveAdmissionGameTests.java`
and this task's records. Central status/log changes remain Root-owned and separate.
No other agent source, archive, AGENTS, build, registry, schema, provider ID,
packet, item, resource or machine consumer is modified. New test code is NEW/MIT,
not copied from upstream assets or implementations.

## Observable behavior and tests

- Record an already observed denial using only the Level capability and chunk
  identity, before any outgoing save. No chunk acquisition, lookup or load.
  First reason, 256 distinct coordinates and 256-character reason bounds remain.
- Defer only this save attempt: check existing permanent denial first, validate
  a bounded nonblank temporary reason, mark unsaved and throw. Do not insert,
  clear or reset permanent state. Future callers may use it only for an actually
  held coherent operation; a loaded pending transaction is not such authority.
- On ServerStoppedEvent, after the writer/Level-close attempt, close remaining
  Level-owned denial states. Attempt each Level even if an earlier state is
  unavailable; retain only the first RuntimeException and rethrow it after
  attempting all remaining Levels. Do not silently classify unavailable state
  as successful disposal or imply that native writers succeeded.
  Do not close at ordinary chunk/Level unload or before final native writers.
  Missing/invalid capabilities must not reopen or normalize a guard.
- New event-bridge GameTests cover observation before an empty save, first-wins
  retry, nonsticky temporary deferral, sticky-priority deferral and bounded
  reason rejection. Retain all old tests. Never close the shared live GameTest
  Level with a fabricated stop event. Existing pure tests check closed state.

## Risk, verification and boundaries

[R-021](../../11-RISK-REGISTER.md) remains open. Refusal is an explicit whole
terrain-chunk exception mechanism: it can revert unrelated changes, 257 distinct
denials saturate the Level, and retry/error volume has no total quota. The
[ADR disclosure proposal](../v1.8.0-c16a-save-guard/ADR-064-SAVE-REFUSAL-EXPLANATION-PROPOSAL-01.md)
is not risk acceptance. Temporary deferral has the same per-attempt save/log
impact but must not make that coordinate permanently denied by itself; repeated
real held operations can cause repeated deferrals. No automatic repair/reset,
cross-store durability, writer admission or cleanup guarantee is introduced.

Verification: scoped whitespace, independently reviewed actual source/test
delta, bounded cached Java17 compilation and existing pure-domain JUnit if
available, then unchanged exact-commit hosted clean build, DataGen/repeat/clean
worktree and complete GameTest. Record real results and failed attempts.
Native final-save ordering, dynamic-dimension teardown, late callback/provider
failure and packaged restart remain unverified until separately executed.
C below 10 GB excludes a local full build/GameTest/native server. Temporary
outputs belong under D:/GitHub/ARCE-Task-Evidence; no refused cleanup retry.

This helper alone is not playable shared hatches, a controller, a lathe,
GuardTicket consumption, a permanent-denial producer or ledger/Gate delivery.
