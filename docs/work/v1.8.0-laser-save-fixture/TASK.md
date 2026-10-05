# Laser-target save-observation fixture correction

Status: SOURCE_READY_FOR_REVIEW; compilation and GameTest verification pending.

## Fixed scope

Base: `adcd46d2300b74be60f0621ce1b76598f233dce8` on the isolated
`test/v1.8.0-laser-save-fixture` worktree. Root is the sole integrator and committer.

Writable paths:

- `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/LaserTargetGameTests.java`:
  only `aTargetRegistersOnceSavedAndARemovedIdComesBackFrozen` and its private,
  bounded save-observation/cleanup support.
- This task directory's `TASK.md`, `PROGRESS.md` and `HANDOFF.md`.

The existing test assumes that 25 ticks imply no native chunk save. The retained
hosted failure at fixed `cfdcd8f546d7005b11c7bb1c8635eb3d1ee03339` fails that
assumption, before its removal/frozen-copy assertions. This correction changes
only the fixture's observation oracle, not production persistence semantics.

## Required behavior

- Strict same-server-call pre-save absence/AWAITING/inactive check before yielding.
- Read-only Save observation for exact Level, chunk, type, UUID, position, saved
  owner and non-frozen state. Keep only scalar observation flag/first tick.
- Keep the 25-tick checkpoint and 300-tick deadline; any registered record must
  have matching save evidence and exact identity fields.
- Keep all existing protection, capability, buffer/drop-count, removal,
  retirement, frozen-copy and no-reregistration assertions.
- Close owned listener/context on success, setup failure, one-test AfterBatch
  (including failure), and owned server stopping. Clean only the placed target
  and explicitly identified drops; never reset the shared endpoint root or sweep
  unrelated entities.
- Production Save events and this test's serialized Save events remain
  pre-write observations under ADR-054, not disk-ack/restart proof.

The private observer examines at most 1,024 serialized block entities per event;
the existing persisted reader can make a second pass over that admitted list.
It retains no event/tag history. Cleanup identifies newly spawned cobblestone
references around the one native target-removal call, with a 16-item fixture
bound, and never sweeps pre-existing nearby items. These are test-only admission
bounds, not changes to production chunk/entity limits.

## Verification and exclusions

Use bounded static/finite controls and exact before/after identities. Local full
build, GameTest and native execution are not authorized while C has less than
10 GB free. Compilation/runtime claims require actual separate receipts.

Other tests, production classes, public watcher, registries, generated files,
central documents, AGENTS, schema, budgets and save policy are read-only. No
source/Gate acceptance, timeout increase, save suppression, commits or pushes.
New helpers and process temporary directories stay in the owned D-parent leaf.
