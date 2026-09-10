# v1.2.0 Rolling Machine verification

```yaml
version: v1.2.0
slice: V120-MCH-01
status: IN_PROGRESS
date: 2026-09-11
branch: codex/v1.2.0-rolling-machine
```

## Verified implementation

- The original community-authored 2×3×5 pattern is loaded through the bounded,
  all-or-nothing datapack catalog. File IDs must match embedded IDs and a rejected
  reload retains the last valid catalog.
- The registered controller and four typed port BlockEntities form, unbind and rebuild
  through block, neighbor and chunk lifecycle events. Controller generations advance
  monotonically and no structure lookup forces a chunk load.
- Item, water and FE are retained by their owning port BlockEntities under the
  `arce_rolling_port` schema 1 root. Current roots are limited to 64 KiB, Item payloads
  to 16 KiB, and unrelated resource kinds are rejected rather than discarded.
- Capabilities are exposed only while the binding identifies the loaded, formed
  controller generation and the controller's persisted part set contains that port.
  Unbinding invalidates all cached views; a caller retaining an old handler cannot use
  it after a later rebuild.
- Future, malformed, mismatched-type and oversized port resource roots are preserved as
  opaque NBT and fail closed. A blocked required port cannot participate in formation.
- Breaking an Item port drops its stored stack; ordinary structure invalidation leaves
  every port block and resource untouched.
- `advancedrocketrycommunity:rolling` recipes use exact schema-1 fields, stable semantic
  signatures and a deliberately limited vanilla Item/Tag ingredient subset. Individual
  definitions are limited to 64 KiB, ingredient JSON to 4096 characters, resolved
  variants to 32 and a server lookup to 1024 Rolling recipes; ambiguity and excess fail
  closed.
- The controller owns independent `arce_process` and `arce_process_journal` schema-1
  roots with bounded progress, global resource revision, recipe signature and replay
  UUID. Future or malformed roots are preserved and block affected resource access.
- A bounded ready queue advances only formed, loaded machines. Item/Fluid simulation is
  side-effect free, FE is consumed per tick, and final Item/Fluid replacement advances
  the shared revision exactly once through the recoverable process executor.
- The stop path clears all manager indexes and the next `ServerAboutToStartEvent`
  reinstalls the narrow runtime bridge. A packaged two-start lifecycle fixture remains
  part of `V120-ROLL-05` rather than being inferred from the initial GameTest startup.
- The built-in batch consumes two iron ingots, 100 mB water and 2000 FE, then produces
  eight iron bars. Forge tests cover exact completion, an active-process NBT reload and
  a completed-progress/no-journal state that enters `RECOVERY_REQUIRED` without a
  second resource commit.

## Verification commands

| Command | Result |
|---|---|
| `gradlew test --tests "...ProcessForgePortAdapterTest" --tests "...RollingMachinePortPersistenceTest" --rerun-tasks --no-daemon` | PASS; 8 tests |
| `gradlew runGameTestServer --no-daemon` | PASS; all 63 Required GameTests |
| `gradlew clean build --no-daemon` | PASS; 539 JUnit tests, 0 failures/errors/skips |
| `gradlew runData --no-daemon` with the installed JDK 17 | PASS; existing v0.8 providers wrote no changed files |
| `python scripts/validate_repository.py --require-approved-identity` | PASS; 45 checks, 0 failures |
| `git diff --check` | PASS |
| common/server client-import scan | PASS; no `net.minecraft.client` import in machine process/port/rolling packages |
| Rolling recipe/process/port adapter targeted JUnit | PASS; 19 tests, 0 failures/errors/skips |
| `gradlew runGameTestServer --no-daemon` after process integration | PASS; all 67 Required GameTests |
| `gradlew clean build --no-daemon` after process integration | PASS; 548 JUnit tests, 0 failures/errors/skips |
| `gradlew runData --no-daemon` after process integration | PASS; 22 cache entries, written 0 |
| `python scripts/validate_repository.py --require-approved-identity` after process integration | PASS; 45 checks and 848 links |
| `python scripts/validate_v1plus_planning.py` | PASS; 11 plans and the 33-input inventory |

The GameTest command is a bounded slice check under ADR-018. It is not long-load,
remote-Linux, real-GPU, two-client or final all-machine/all-dimension acceptance.

## Remaining before this slice is verified

- Add menu diagnostics and v1.2-specific deterministic DataGen evidence for the already
  registered blocks and controlled recipe data.
- Add the packaged-server forced-interruption/restart fixture and rerun the final Rolling
  short-cycle verification set. The current journal phases mark the BlockEntity dirty;
  this evidence does not claim a synchronous disk flush for every phase.

## Gate statement

This evidence verifies `V120-ROLL-02`, `V120-ROLL-03` and the recipe/process portion of
`V120-ROLL-01/04`. It does not satisfy the complete Rolling Machine slice, v1.2.0
release Gates, or the acceptance campaign deferred by ADR-018.
