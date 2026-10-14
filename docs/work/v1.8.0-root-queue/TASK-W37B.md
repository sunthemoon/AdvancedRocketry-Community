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

## Amendment (Root, 2026-10-14, after the boundary report in `records/W37B.md`)

Your report shows there is no post-write Forge callback inside `setRespawnPosition`, and that both
END-only and logout-only restoration are too late. The boundary is therefore built from Forge events
that run **before every save** that can follow the bed operation. No Mixin.

**Facts to verify with `javap` first.**
- Packet handlers run as server tasks between ticks, through `PacketUtils.ensureRunningOnSameThread`
  and the task queue that `waitUntilNextTick` drains. So the bed use happens between ticks.
- In the next tick, `ServerTickEvent` START is posted before `tickChildren` and before the autosave
  at 96.

If either is false, stop and report.

1. **Snapshot.** Take it at the admitted off-world bed operation (`PlayerSleepInBedEvent`, after
   W37's admission): the exact native tuple, keyed by the player, in transient lifecycle state.
2. **Attribution.** A `PlayerSetSpawnEvent` belongs to the operation only when it matches vanilla's
   bed setter exactly: the player's current Level, the bed position, not forced, and arriving while
   that operation's snapshot is pending.
3. **Primary restore.** At Forge's `SleepingTimeCheckEvent` at `EventPriority.HIGHEST`, posted after
   the setter (127) and before both late refusals and a successful sleep, write the snapshot back by
   direct field writes. Only do this if the native tuple still equals exactly the bed tuple that
   operation wrote. Then clear the pending entry.
4. **Fallback restores.** Apply the same exact-match restore for any entry still pending, before
   each save that can follow within the same inter-tick window:
   - `ServerTickEvent` START, before the autosave;
   - `PlayerLoggedOutEvent`, which `PlayerList.remove` posts before its save;
   - `ServerStoppingEvent`; verify that `stopServer` posts it before `saveAll` at 68;
   - Forge's `CommandEvent`, before any command, including `/save-all`, executes.

   These cover a throwing listener earlier in the time-check dispatch.
5. **The residual, stated in the record and the proposed risk text.** A third-party task that saves
   players in the same inter-tick window, between the bed packet and the next fallback, could
   persist the bed tuple. A crash cannot persist it, because no save happens before a restore.
6. **R-036.** A pending planet-respawn backup is never touched. A snapshot restore never writes over
   a tuple other than the exact bed tuple.

Then carry out the rest of the task: the six natural types, the jar check, the `natural()` effect
list, and the tests, including:
- a throwing `SleepingTimeCheckEvent` listener before ours, followed by the next tick's
  autosave-equivalent save;
- `/save-all` run right after the bed use.

## Amendment 2 (Root, 2026-10-14, after counterexamples A and B in `records/W37B.md`)

Root accepts both remaining native save paths as documented residuals. No Mixin.
- **A, the integrated-server pause save.** `IntegratedServer.tickServer` 53 can save before any
  START.
- **B, crash cleanup.** `runServer` 417, then `stopServer` 525 and 647, saves without
  `ServerStoppingEvent`.

**Why.** Each needs a compound failure: another mod's listener must throw in the time-check dispatch
before ours (or an `Error` must escape the bed task), and then a single-player pause or a crash must
follow in the same inter-tick window. The consequence is bounded. The off-world bed tuple becomes the
saved point, and W37's respawn validation (R-036) still judges it at respawn: with the default
options, the Overworld fallback applies and the point is kept. That is a narrow violation of D1
outcome 3, not a loss of the player's data.

**What to do.**
- Implement amendment 1 as written.
- `ServerStoppingEvent` stays as the orderly-shutdown fallback.
- Additionally, register the primary restore so it runs before any foreign `SleepingTimeCheckEvent`
  listener can throw, as far as Forge allows. Say how far that is.
- State residuals A and B exactly, with their offsets and their bounded consequence, in the record
  and in the proposed risk text. Root adds them to R-036 and the ADR-066 note.
- Then finish the task: the six natural types, the jar check, the `natural()` effect list, the tests
  and the commands.
