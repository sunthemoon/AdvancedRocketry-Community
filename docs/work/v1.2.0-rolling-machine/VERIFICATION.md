# v1.2.0 Rolling Machine verification

```yaml
version: v1.2.0
slice: V120-MCH-01
status: IN_PROGRESS
date: 2026-09-10
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

The GameTest command is a bounded slice check under ADR-018. It is not long-load,
remote-Linux, real-GPU, two-client or final all-machine/all-dimension acceptance.

## Remaining before this slice is verified

- Register and strictly decode `advancedrocketrycommunity:rolling` recipes.
- Persist `arce_process` and `arce_process_journal`, including resource revision,
  recipe signature and last-applied transaction UUID.
- Connect bounded process scheduling, per-tick energy consumption and journaled final
  Item/Fluid replacement without client authority.
- Add menu diagnostics, model/loot/recipe data and v1.2-specific deterministic DataGen
  evidence.
- Add save/reload and interrupted-journal recovery fixtures, then rerun the current
  version's complete short-cycle verification set.

## Gate statement

This evidence verifies `V120-ROLL-02` and only the typed-port portion of
`V120-ROLL-03`. It does not satisfy the complete Rolling Machine slice, v1.2.0 release
Gates, or the acceptance campaign deferred by ADR-018.
