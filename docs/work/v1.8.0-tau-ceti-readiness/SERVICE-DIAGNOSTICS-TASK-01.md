# Bounded transfer service failure diagnostics

## Assignment and scope

Development base: `60f1564528de782fa15269884fd1355d3c0b9ff1`; isolated branch
`codex/v1.8.0-transfer-diagnostics-20261007`. This implements diagnostic observation,
not a transfer/readiness fix. Root integrates, commits and publishes; the worker does
not stage, commit, push or modify main. Effective live user AGENTS governs this work.

Exclusive write scope under `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/`:

- `rocket/server/RocketRuntime.java`, `RocketManager.java`, `RocketFlightService.java`,
  `RocketTransferService.java` and new `TransferFailureDiagnostics.java`.
- `gametest/TauCetiPathGameTests.java` and `PlanetaryWorldGameTests.java`.
- New `src/test/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/TransferFailureDiagnosticsTest.java`.
- This task record only; no other source, central, generated, governance or status edits.

The new helper/tests are original project-authored MIT code, not imported or transformed
official/upstream source. Named native declarations are read-only compatibility facts.

## Behavior and exclusions

One service-owned latest-successfully-prepared UUID/scalar slot and service tick-entry/
completion scalars record existing dispatch branches. A newer successful preparation
replaces the slot; mismatched request observations are explicitly unobserved. No collection,
durable schema, permission, gameplay API, provider callback, native reflection or clock
interpolation is added. The reader captures RocketRuntime's installed pointer once and
only queries an actual final RocketManager; other implementations are unsupported.

Observation reads only lifecycle/diagnostic fields and existing live/settled membership.
It never obtains SavedData, loads/tickets/ticks chunks, scans entities, waits on native
futures, invokes recovery or changes authority. Native getTickCount is a scalar timestamp,
including signed wrap; no arithmetic on it decides flight behavior. Completion is recorded
only after the original service loop completes, not in an exceptional finally.

Each existing failing Tau/Planetary assertion may emit one additional ASCII payload of
at most 512 bytes, excluding logger/framework prefix. Original Tau PRE/POST lines and all
old diagnostic payloads remain untouched. Successful assertions perform no new observation.
Formatting/query failures choose fixed fallback text before one logging invocation;
logging failures never retry or replace the original assertion/cleanup.

Unchanged: native readiness/ticket/recovery calls; state-set mutations; native flight timings;
original assertions, oracle strings, scheduling, restoration and cleanup; all persistence,
network, registry, admission, world behavior and Gate/ledger status.

## Verification and status

Mandatory governance/version/parallel documents were previously read; their fixed objects
at this base are unchanged from the previously inspected source basis
`86c40c56b8ac3815522cc6e248e16307809bc4de`. Live AGENTS
SHA remains `1be0391c3f69f566ae3f627e37107adfbd0bbfa6f581b105ff6bd2a2c2630dc0`.

Planned checks: exact source inversion outside marked diagnostic additions; scoped path/
whitespace/call invariants; pure helper reset/classification/slot/branch/format tests; then
different-agent actual-source review before Root integration. Actual development checks:
23 finite/static controls passed, including exact inversion of all six pre-existing Java
files. Root separately granted one serial Java17/Jupiter cohort: only the pure helper,
its test and the unchanged external A0Harness with six cached JUnit dependencies. Javac
and JVM exited 0; 19 discovered subjects all passed, with zero failures, aborts, skips or
container failures. Inputs/configuration/base had no drift, and both streams were fully
captured. This is an uncommitted development observation, not a committed delivery result.

Evidence: `D:/GitHub/ARCE-Task-Evidence/v1.8.0/transfer-diagnostics-author-20261007-529e1a`
and the separate `transfer-diagnostics-a0-20261007-529e1a` result/raw leaf. No production
bridge/GameTest compilation, Gradle, native or hosted run was executed. Installed-service
classification, framework-prefix length and real failure-path logging remain runtime
verification requirements. Current Tau/Venus required failures remain open and unchanged.

Existing TransferService/FlightService exceed 500 lines before this diagnostic slice;
bookkeeping/formatting is isolated in the new small pure helper, rather than adding a new
service responsibility or framework. Final counts and the 800-line threshold are reported
in external evidence before freezing: FlightService 573 and TransferService 761 lines,
both below 800. Their flight/transfer responsibilities are unchanged; new scalar bookkeeping
and formatting resides in the 93-line helper. No new class exceeds 500 lines.
