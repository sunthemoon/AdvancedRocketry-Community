# TASK W37b: natural off-world types and D1 outcome 3 (an off-world bed keeps the saved point)

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w37b-natural-beds` (from the shared branch, plus Root's `build.gradle` commit and this task) |
| Branch | `codex/v1.8.0-w37b-natural-beds` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W37B.md` (new) |
| Specification | `docs/work/v1.8.0-c18a-sleep/D1-OUTCOME-FREEZE-33.md` (outcomes 1 to 6 and its conditions); ADR-066 section 3.3 (the sleep bullet) and its W37 note; `OWNER-DECISION-03.md` and `-05.md` in the same folder; R-036 |

W37, merged as `4f9e6597`, refuses sleep off-world on unbreathable, PENDING or incomplete cells and
without station VISIT. While the six off-world dimension types keep `natural=false`, vanilla refuses
those beds first, so W37's admission is not reachable yet. This task makes it reachable. The
`sleepEvent` ledger row is delivered with it.

This slice changes the player's saved respawn point behaviour and dimension data. Write a design
section at the top of your record **before** the code. Root will have the result reviewed
independently. If the operation boundary in step 2 cannot be proven without a Mixin, **stop and
report**; a new Mixin needs its own ADR and the owner's confirmation (ADR-073).

## 1. The types (D1 outcome 1)

- **The one change.** Set `natural=true` on exactly the six types: `moon`, `space`, `mars`, `venus`,
  `tau_ceti_f`, `tau_ceti_g`. Every other field stays the same, including the 18000 `fixed_time` of
  Moon and Space.
- **One writer.** The v1.8 DataGen becomes the one current writer of all six. Root's commit on this
  branch excludes the four v1.4 copies (`mars`, `moon`, `space`, `venus`) from `processResources` and
  `sourcesJar`; the v0.3 copies were already excluded. Do not edit `build.gradle`; if the exclusion
  needs a change, stop and report.
- **Prove it.** Check that the packaged jar holds exactly one entry for each type, and that each
  type loads with `natural=true` at runtime.
- **List the effects.** List every read of `DimensionType.natural()` in the mapped Forge jar
  (`javap`), and its effect on these Levels: beds, compasses, clocks, Nether portal piglin spawns,
  and anything else you find. Compare each with D1 outcome 6. Report any effect that the freeze does
  not cover.

## 2. D1 outcome 3: an off-world bed keeps the existing saved point

- **The rule.** An ordinary off-world bed interaction keeps the player's existing native spawn
  tuple, or its absence. This includes a late refusal (daytime, monsters) that comes after vanilla's
  setter call in `ServerPlayer.startSleepInBed`.
- **Scope.** It must be scoped to the operation, not a blanket veto. Unrelated, nested, admin,
  restore and other-mod setters keep working. This is review R4 of the first W37 review; read it.
- **A suggested shape.** Verify it with `javap`, or replace it with something better:
  - snapshot the tuple when the admitted bed operation starts (`PlayerSleepInBedEvent`, after the
    other listeners);
  - identify vanilla's own bed setter by its exact arguments: the bed position, the Level, not
    forced, and the same call;
  - put the snapshot back by direct field writes, as W37F's restore does, only when the native
    tuple still equals exactly the bed tuple that operation wrote.
- **Find the latest safe point.** Find the latest point where the restore can run before any save
  can persist the bed tuple: autosave, logout, stop. Cite the offsets, and state the window that
  remains.
- **Interaction with W37's backup (R-036).** A pending planet-respawn backup must never be broken.
- **Owner decisions.** Owner decision 03 says off-world sleep keeps the existing point. The
  separate question of whether sleeping should set a planet point when `allowPlanetRespawn` is on
  is **open with the owner**: do not implement it.

## 3. Tests

- **Native GameTests with a real connected player on each of the six Levels:**
  - a breathable, VISIT-permitted bed sleeps, and the saved point is unchanged;
  - an unbreathable bed is refused;
  - a daytime refusal and a monster refusal after the setter still keep the point;
  - an admin `/spawnpoint`, another mod's setter, and a W37 restore still work;
  - the Overworld bed behaviour is unchanged.
- **The automatable D1-V rows** from the freeze's evidence list: packaging, holder and readback,
  bed operations, compass and clock. List the rows that need real clients or a packaged restart as
  left for the final campaign.

## Commands

- The whole unit suite once.
- `runData` under the lock, then `git diff --exit-code -- src/generated` after committing.
- `build` once under the lock, for the jar check.
- `runGameTestServer` under the lock, then the log checker.
- Delete your generated `build/gametest/world` at the end with a plain `rm -rf build/gametest/world`
  from the worktree root. If the tool refuses, record it and Root deletes it.

## Not in this task

- The owner-open sleep planet point.
- Status files, the ledger, ADRs and the risk register (Root).
