# Airlock native regression triage 18: analysis

Date: 2026-10-08. Author: delegated Claude source-analysis worker (not Root,
reviewer or approver). Read-only source analysis; no build, JVM, GameTest,
replay or source change was executed or proposed as adopted.

## 1. Scope and inputs

- Worktree `D:/GitHub/arce-v180-claude-airlock-triage18-20261008`, fixed base
  `e91ddc16eccd714a936f93db838975f6e6e7ccdb` (identity taken from the task; no
  Git command was run to re-check HEAD).
- Root packet `reserve-forward-integration17-root-20261008-01` (below, "packet
  17"). `RESULTS17.json` and `COMMANDS17.json` were read in full. Both name the
  same source SHA. The `runGameTestServer` record (below, "NATIVE17") has exit 1.
  `RESULTS17.json` also records 2,179 unit tests with 0 failures, 63 native error
  headers and `acceptance: false`. These counts were not re-derived here.
- `NATIVE17-latest.log` and `NATIVE17.log` were searched and read only in
  bounded ranges (about lines 2760-2790 and 5725-5764 of the latest log, plus
  matching lines in the Gradle log). Their full contents were not read. The other
  62 error headers were not triaged.

## 2. Observed facts (packet 17 logs)

- `NATIVE17-latest.log:2769`: batch `airlock:1` (7 tests) starts at 15:13:10.995.
- `:2770`: batch `moon_arrival_highlands:1` is logged at 11.396, before the
  failure line. Listener ordering was not established and is not used as evidence.
- `:2771`: `bothhalfcallbacksrevokeinstalledcachedinflightandcompletedairbeforetick
  failed! Installed producer did not establish supplied chamber upperOnly=true
  phase=0 status=OPEN oxygen=1000 energy=40000 activeTaskOutcome=NONE
  indexed=false tracked=1 active=0 pending=0 dirty=0 inspections=436
  seed=(328,180,88) retainedOutcome=OPEN needsScan=false lastScanBounds=<seed
  only> exposedSkyIsOpen=true climateControlRequired=false`. The seed cell is
  OPEN and all six neighbours are SEALED. The halves are lower (329,179,88) and
  upper (329,180,88), both north/left, unpowered and closed. The seed block is
  `minecraft:air`, `seedCanSeeSky=true`, `seedMotionBlockingNoLeavesHeight=182`
  and `seedYAtOrAboveHeight=false`.
- `:5740-5742`: 542 game tests complete and 1 required test failed, with this name.
- `NATIVE17.log:2800,2802,5772-5773` mirror these lines. `:5802` and `:5820`
  report the Gradle failure (`BUILD FAILED in 3m 41s`).

## 3. Source-derived conclusions (fixed worktree)

Paths are under `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/`.

1. **Which case failed.** In `gametest/AirlockDoorGameTests.java:450`,
   `upperOnly = index >= 3` and `phase = index % 3`. The reported
   `upperOnly=true phase=0` is therefore case index 3. Line 449 asserts
   `index == nextCase`, and `nextCase` is incremented only after `revokeCase`
   returns (`:450-452`). This means that cases 0-2 completed without throwing in
   this run. No per-case log line confirms this.
2. **What failed.** The failing assertion is the supply prerequisite at
   `:289-297`, which runs after the vent install and the 32-iteration loop
   (`:275-286`). The callback, revocation and recovery checks (`:298-329`) were
   not reached for case 3. This failure therefore provides no observation about
   opposite-half revocation behaviour.
3. **Geometry.** `prepare` (`:437-445`) builds a 3x3x3 iron shell with an air
   centre at the seed and places the pair at `cell.east().offset(0,-1,0)` for
   index 3 (`pair`, `:486-490`). `revokeCase` puts the vent at `cell.below()`
   (`:274-275`). The iron at (328,181,88) above the seed matches the logged
   heightmap value 182.
4. **The only OPEN paths.** In `atmosphere/server/ServerLevelVolumeWorldView.java`,
   `observe` returns OPEN only outside the build height (`:43-45`) or for air
   when `isExposedToVacuum` is true (`:75-79`). `isExposedToVacuum` is true
   when `exposedSkyIsOpen && (level.canSeeSky(pos) || y >= MOTION_BLOCKING_NO_LEAVES
   height)` (`:88-102`, `canSeeSky` at `:92`). At diagnostic time, 180 < 182, so
   the diagnostic's seed=OPEN can only come from the `canSeeSky` branch. This
   agrees with the logged `seedCanSeeSky=true`.
5. **Timing.** The supply loop (`:284-286`) and the diagnostic (`:333-409`) run
   synchronously inside one `runAfterDelay` callback, which is one server tick.
   World inputs that change only between ticks cannot change during the 32
   iterations. `prepare(index+1)` runs in the same callback directly after
   `closeActive()` restores the previous fixture (`:451-454`). The next case runs
   one tick later (`:428`, delay `index + 1`).
6. **Diagnostic limits.** The diagnostic is a later sample, as the source itself
   states (`:332`, `:379`). The scan-time operands that produced the retained
   OPEN were not logged.

## 4. Hypotheses (not proven)

- **H1 (sky-light lag).** If vanilla 1.20.1 `canSeeSky` is sky-light based
  (sky brightness >= maximum), then `canSeeSky=true` under a solid iron block
  means the seed still held stale sky light. Possible sources are the previous
  case's restore to open air, or an asynchronous light engine that has not yet
  applied the shell re-placement one tick later. That `canSeeSky` implementation
  is outside this repository and was **not re-read** here, because JAR access is
  prohibited.
- **H2 (non-determinism).** Prior records report this assertion failing on
  different cases: `upperOnly=false` phase 0 (`docs/work/v1.8.0-ci/RESULT-28.md:30`,
  `RESULT-30.md:71`), lower phase 1 (`RESULT-36.md:47`, `RESULT-39.md:45-50`),
  and lower phase 0/1 with the same `sky true`/`height 182` pattern
  (`RESULT-42.md:44-47`, `RESULT-43.md:24-25`, `RESULT-44.md:46-49`). One cohort
  recorded all six cases passing
  (`docs/work/v1.8.0-c18a-airlock/FIXTURE-TIMING-INTEGRATION-01.md:43-46`). This
  run fails case 3 after cases 0-2. These observations are consistent with a
  timing-dependent input. However, the runs used different source revisions and
  hosts, so they do not prove H1 or any unique cause.
- **H3 (production relevance).** If H1 holds, the same production view can
  classify a newly enclosed air cell as OPEN until light catches up. With
  `needsScan=false`, it is unknown whether that OPEN is ever re-scanned without
  another dirty event. `AtmosphereLevelService`'s re-scan and dirty policy was
  not read in this task.
- **Not excluded.** Another moon-level actor touching the same chunk, scanner
  semantics behind the seed-only `lastScanBounds` (`VolumeScanTask` not read),
  and reflection-read field staleness.

## 5. Missing proof

- No scan-time `canSeeSky`, sky brightness or heightmap sample for the OPEN event.
- No sky-light value or light-engine pending state at any time.
- No independent confirmation of the vanilla `canSeeSky` semantics.
- No per-case positive evidence for cases 0-2, and no replay in this task.
- The semantic purpose of the `canSeeSky` branch, beyond the heightmap branch,
  is not documented in the source read. For example, the branch may affect
  rooms roofed with light-transparent motion-blocking blocks. No GameTest in
  `src` referencing glass blocks was found to exist for atmosphere sealing, and
  this was not checked further.

## 6. Proposed minimal checks (written here, not executed or adopted)

- **C1 (diagnostic only, no behaviour change).** Add
  `level.getBrightness(LightLayer.SKY, cell)` and the same value for
  `cell.above()` to the failure-only diagnostic. If the 1.20.1 mappings expose
  it, also add the light engine's pending-work flag. This would show whether
  `canSeeSky=true` coincides with sky light 15 at failure time (H1's mechanism).
  It remains a later sample, not a scan-time observation.
- **C2 (scan-time witness).** Sample `canSeeSky(cell)` and sky brightness once at
  the start of each `runCase`, before the vent install at `:275`, and include
  them in the existing message only on failure. This separates "stale at case
  start" from "changed during the case" without altering the predicates or
  deadlines.
- **C3 (fixture sequencing experiment).** Wait a bounded number of ticks for the
  shell's light state before the vent install. This changes test sequencing.
  It must not be adopted as a repair: it could mask a production stale-light
  OPEN (H3), and AGENTS section 5 forbids passing by widening deadlines. It
  needs an explicit Root/owner decision.
- **C4 (production semantics question, no change proposed).** Decide whether
  `isExposedToVacuum` should depend on light state. Any change must first
  enumerate which player-visible cases rely on the `canSeeSky` branch, add
  happy and failure GameTests (open column, sealed roof, transparent roof,
  freshly closed room), and keep restart and save behaviour unchanged.

Every check requires an actual `runGameTestServer` cohort on a committed SHA.
One passing or failing batch cannot establish causation. All earlier failures
remain open.
