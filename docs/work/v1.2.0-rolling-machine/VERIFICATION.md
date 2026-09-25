# v1.2.0 Rolling Machine verification

```yaml
version: v1.2.0
slice: V120-MCH-01
status: VERIFIED
date: 2026-09-25
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
  reinstalls the narrow runtime bridge. A packaged first-start/restart baseline verified
  both clean server cycles against the same world identity.
- The built-in batch consumes two iron ingots, 100 mB water and 2000 FE, then produces
  eight iron bars. Forge tests cover exact completion, an active-process NBT reload and
  a completed-progress/no-journal state that enters `RECOVERY_REQUIRED` without a
  second resource commit.
- A property-gated, permission-level-2 packaged-server fixture uses the real typed
  capabilities and respects their 1000 FE per-call receive limit. It pauses at progress
  10, performs `save-all flush`, kills the process without `stop`, and verifies the same
  progress, resources and revision after restart. Resume produces one eight-bar batch
  and one replay UUID; an idle wait and a final restart produce no duplicate output.
- The vanilla menu protocol exposes fixed IDs for formation, validation, the first
  structured diagnostic, process state/failure, progress/total, FE, water and inspected
  cells. Full 32-bit coordinates and the 72,000-tick upper bound are split into explicit
  low/high fields rather than truncated to the protocol's signed shorts.
- Menu slots resolve only the current loaded and formed port generation. Input/output
  shift-click uses authoritative handler operations; GameTests cover merging into an
  occupied input and a one-item-capacity partial output transfer without loss or
  duplication. Breaking the first casing reports its exact local and world position,
  removes slot access and leaves a distant menu invalid.
- The client screen is an original code-drawn static panel and only renders synchronized
  state. No custom C2S packet, upstream texture or sound was added; JEI remains in
  `V120-INT`, and real-GPU visual acceptance remains deferred by ADR-018.
- v1.2 DataGen owns 35 non-cache outputs: five blockstates/models/item models, bilingual
  language data, five crafting recipes, the controlled Rolling recipe, advancements,
  five loot tables and consolidated pickaxe/iron-tool tags. A consecutive run writes
  zero files.

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
| Rolling recipe/menu wire targeted JUnit | PASS; 6 tests, 0 failures/errors/skips |
| first v1.2 `gradlew runData --no-daemon` | PASS; 35 non-cache files written |
| consecutive v1.2 `gradlew runData --no-daemon` | PASS; written 0 |
| `gradlew clean build --no-daemon` with menu/DataGen | PASS; 550 JUnit tests, 0 failures/errors/skips |
| `gradlew runGameTestServer --no-daemon` with menu conservation regressions | PASS; all 68 Required GameTests |
| final `python scripts/validate_repository.py --require-approved-identity` | PASS; 45 checks and 850 links |
| final `python scripts/validate_v1plus_planning.py` | PASS; 11 plans and the 33-input inventory |
| common/server import, class-size and sensitive-diff scans | PASS; 0 client imports, largest production class 493 lines, no known credential pattern |
| final `git diff --check` | PASS |
| packaged `run_dedicated_server_smoke.py` baseline | PASS; first start and same-world restart both saved and stopped cleanly, with zero project errors or client-linkage failures |
| `python scripts/run_v120_rolling_restart_smoke.py ...` | PASS; durable-save forced stop preserved progress 10, 500 mB water, 3800 FE and revision 6 exactly; completion retained 8 iron bars, 400 mB water, 2000 FE and revision 7 through a final restart |
| final `gradlew clean build` after `V120-ROLL-05` | PASS; 550 JUnit tests, 0 failures/errors/skips |
| final `gradlew runData` after `V120-ROLL-05` | PASS; 35 total files, written 0 |
| final `gradlew runGameTestServer` after `V120-ROLL-05` | PASS; all 68 Required GameTests |
| final repository and planning validators | PASS; 45 checks and 856 links; 11 plans and the 33-input inventory |

The GameTest command is a bounded slice check under ADR-018. It is not long-load,
remote-Linux, real-GPU, two-client or final all-machine/all-dimension acceptance.

## Packaged restart evidence

- Tested implementation commit:
  `3c54daf797a9e8ff45d496a308d2a7eae4369c6e`.
- Packaged artifact SHA-256:
  `3fa6b6b0724507afd03470ef42bac0aeec6dd095ef1d6551c0e835e2290702e5`.
- [Baseline first-start/restart summary](packaged-restart/baseline-summary.json)
- [Forced-stop/recovery summary](packaged-restart/summary.json)
- [Filtered lifecycle log](packaged-restart/filtered-lifecycle.txt)
- [Evidence SHA-256 manifest](packaged-restart/SHA256SUMS)

## Deferred acceptance outside this slice

- Perform the real-client visual check only at the ADR-018 acceptance point. Compilation
  and server-side menu interaction prove protocol behavior, not GPU rendering quality.
- The forced stop follows an observed `save-all flush`; it proves recovery of durable
  active state, not synchronous disk flush at every transaction-journal phase.

## Gate statement

This evidence verifies `V120-ROLL-01` through `V120-ROLL-05` and the functional
`V120-MCH-01` Rolling Machine slice. It does not satisfy the v1.2.0 release Gates or the
full acceptance campaign deferred by ADR-018.
