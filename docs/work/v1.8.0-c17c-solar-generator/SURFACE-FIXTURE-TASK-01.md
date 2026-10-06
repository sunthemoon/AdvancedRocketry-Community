# Solar surface-environment fixture correction

Date: 2026-10-06. Task: `C17c-SOLAR-SURFACE-FIXTURE-01`.
Integrator: Root. Source basis: `a34de0ad5edb0a2b3efe6ad2bb76c17e40fafc43`.
Scope: one existing GameTest source file; no production behavior or contract change.

## Observable outcome and evidence boundary

The surface-environment test must establish its own legal open-sky and roof
cells before asserting native day, weather attenuation, blocking, night,
stored export and scalar-menu behavior. The source investigation shows that
the actual Forge launch path uses loaded world dimensions rather than the
separate flat-world factory. Clearing the one-cell template and one roof cell
does not establish a clear column above its below-ground placement. The recorded
DAY_SKY/roof_sky=0 observation is consistent with that setup gap; no unique
occluder or production defect has been proved.

This correction changes only fixture setup and restoration. A subsequent
actual unfiltered hosted run must establish its behavior. The original failed
cohorts, diagnostics, assertions and 40-tick deadline remain unchanged.

## Exact bounded source scope

The assigned implementation source is only
`src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/SolarGeneratorGameTests.java`.

1. Keep the existing native surface test, registered generator and ordinary
   producer/query routes. Only this test uses the new setup; other tests keep
   their existing `place(helper)` behavior.
2. Use the original test's X/Z and its already-loaded chunk. Select generator
   Y=`maxBuildHeight - 2` and roof Y=`maxBuildHeight - 1`, independently checking
   valid build/world bounds. Never add a ticket, force chunk loading, drain or
   seed native lighting, alter world type or substitute a heightmap predicate.
3. Require the chunk loaded before reading either cell. Reject an existing
   BlockEntity in either cell, because state-only restoration cannot restore
   arbitrary BE data. Capture both exact block states, original time, rain and
   thunder before the first mutation. Use the actual registered generator BE;
   do not inject environment truth or an alternative exposure service.
4. Restore both cells and original time/weather on synchronous setup/assertion
   failure and all existing nested asynchronous success/failure exits. Restore
   must be idempotent. Cleanup errors must not silently replace an original
   assertion: retain it and attach/report restoration errors. Call test success
   only after restoring and reading back both exact block states and original
   time/rain/thunder. A cleanup-only failure must fail the test. Do not remove
   or overwrite unrelated cells or native data.
5. Preserve every existing oracle, weather value, day/night selection, stored
   FE/export check, menu assertion, await stage/diagnostic and 40-tick timeout.
   No failed subject is removed, selected away, relaxed or retried to claim a pass.

Worker writes only that source plus its own `PROGRESS-01.md`/`HANDOFF-01.md` in
the assigned isolated worktree's `docs/work/v1.8.0-c17c-solar-surface-fixture/`.
Root owns this task, shared status/Git, integration and any generated/central
files. No production, registry, config, recipe, resource, persistence, network,
workflow, build, public API or risk-register write is allowed.

## Verification and disposition

Inspect the exact source diff and restoration/control flow with a different
agent. Execute only a separately granted small cached Java 17 compilation
locally; C is below 10 GB, so full local Gradle/GameTest/native runs stay prohibited.
All new temporary inputs/output and process temp use a fresh own D evidence leaf.
Record source/commands/exit/log/hash and any unrun checks explicitly. The heavy
verification is the unchanged hosted clean build/JUnit, two DataGen runs with
clean trees, and unfiltered Forge GameTests at the exact published commit.

Before source delivery, Root verifies independently reviewed postimages,
commits the isolated source, normally merges/non-force pushes and records the
tested identity. This task does not complete Solar native restart/client
delivery, repair historical causes or pass any version Required Gate.
