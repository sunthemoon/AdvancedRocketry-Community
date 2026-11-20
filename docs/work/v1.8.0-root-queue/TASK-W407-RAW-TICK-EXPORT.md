# TASK W407: a gated raw tick export, so that whole-run tick percentiles can be measured

Root: Claude. Date: 2026-11-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w407`, branch `codex/v1.8.0-w407` from the shared branch |
| Decision | Root decision R544 (QUEUE row W407) |
| Sources | `satellite/command/ReleaseTestCommands.java` (the `ticks` subcommand, enabled by `-Dadvancedrocketrycommunity.releaseTestHooks=true`, permission level 2); `satellite/service/SatelliteRuntime.postTickNanos()`; [docs/17](../../17-V1PLUS-QUALITY-BUDGETS.md) section 4 (mean, p95 and p99 MSPT, spikes over 500 ms); W403's record on branch `codex/v1.8.0-w403` (`docs/work/v1.8.0-root-queue/records/W403.md`, section "Tick data source and its hard limit"), which found that no existing command exports the individual tick durations |
| Record | `docs/work/v1.8.0-root-queue/records/W407.md` |
| Write scope | `ReleaseTestCommands.java` (one new subcommand beside `ticks`), a pure helper class for its formatting next to it if useful, unit tests under `src/test/java/`, a new parser module `scripts/campaign_observe/raw_ticks.py` with `tests/test_campaign_observe_raw_ticks.py` (do not add `scripts/campaign_observe/__init__.py`: W403 adds it on its branch, which merges first; load the module by file path in the test), the record. No other product class. |
| Not allowed | A full GameTest run, `runData`, a full build or any native server: the heavy-JVM lock belongs to C4's native runs. Compiling and targeted unit tests are allowed (`JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`). |

## Why

docs/17 budgets the reference node's mean, p95 and p99 MSPT and spikes over 500 ms for the defined reference load.
The existing `ticks` subcommand prints only the statistics of its last 100 ticks, so a sampler cannot combine
windows into whole-run percentiles or find consecutive slow ticks. The next candidate (C5) is being built anyway
for W404's probe fix, so this gated diagnostic can be in it.

## What to build

1. A subcommand `arce satellite release-test perf ticks raw` under the same gate and permission as `ticks`. It
   prints one line: the current server tick count and, for each of the last 100 ticks in tick order (oldest first),
   the tick number and its total duration in nanoseconds, where total is the vanilla tick time plus the satellite
   post-tick work of that same tick, exactly as `ticks` combines them. Work out from the vanilla source (the
   `tickTimes` ring and where `tickCount` advances) and from `SatelliteRuntime` how each ring index maps to a tick
   number, and state the mapping in the code and the record, with the case of a server younger than 100 ticks
   (unfilled slots are left out, not reported as zero). Keep the line bounded (100 entries) and machine-readable,
   for example `ARCE_RELEASE_TEST ticks_raw tick=<n> entries=<k> data=<tick>:<nanos>,...`.
2. A pure formatter or mapper with unit tests: the index-to-tick mapping across the ring boundary, a young server,
   and the combination with post-tick work.
3. `raw_ticks.py`: parse that line into (tick, nanoseconds) pairs, merge successive polls by tick number (dropping
   repeats, reporting gaps where a tick number is missing), and compute from the merged run the tick count, mean,
   p95, p99 (nearest rank), maximum and each run of consecutive ticks over a threshold given as an argument. The
   docs/17 values are printed beside the figures as references, not applied as a verdict. Unit tests with hand-made
   lines, including a gap and an overlap.
4. Run the new unit tests (`./gradlew test --tests` for the new Java test class; the Python test by file), and
   `python -B scripts/validate_repository.py`, which must end with `0 failed`. Note in the record that the live
   check (the subcommand on a packaged server, polled every 100 ticks or sooner) happens on the next candidate under
   the lock.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Python tests must also pass on Linux, where governance runs them; do not depend on Windows-only modules.
- Commit on the branch in one reviewable commit plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
