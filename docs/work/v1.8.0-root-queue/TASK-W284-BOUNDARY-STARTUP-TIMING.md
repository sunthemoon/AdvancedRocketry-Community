# TASK W284: mod startup must not fail on the atmosphere boundary callback's wall-clock time

Root: Claude. Date: 2026-11-04. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w284`, branch `codex/v1.8.0-w284`, created by Root from the shared head |
| Decision | Root decision R316 (QUEUE row W284), from finding 2-1 of W278's scripted single-player round 2: severity high |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/REPORT.md` (round 2, finding 2-1); `evidence/r02c-torch/client-ArceTester1.log`; `evidence/r02c-torch/crash/crash-2026-11-04_13.53.20-fml.txt` |
| Record | `docs/work/v1.8.0-root-queue/records/W284.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W284/` |
| Code | `compat/atmosphere/AtmosphereBoundaryRegistry.java` (lines 25, 26 and 104 to 110), its caller `AdvancedRocketryCommunity.initializeAtmosphereBoundaries` (about line 413), the providers registered there (for example `force_field_boundary`) and their tests |

## What a real client showed

One of four client starts of the same JAR (the hosted build of `6f3bd461b`) on an otherwise busy machine stopped at
Forge's loading error screen with `java.lang.IllegalArgumentException: Atmosphere boundary compilation rejected
provider advancedrocketrycommunity:force_field_boundary for advancedrocketrycommunity:force_field`. The retry started
normally. `AtmosphereBoundaryRegistry.register` measures each `provider.classify(state)` call with `System.nanoTime`
and rejects the whole provider if one call takes more than 5 ms (`MAX_CALLBACK_NANOS`) or all calls more than 1 s
(`MAX_REGISTRATION_NANOS`). During mod loading, class loading, JIT compilation and other processes can stretch a
trivial callback past 5 ms, so whether the game starts depends on the machine's speed at that moment. The same path
runs on a dedicated server.

## What to do

1. Reproduce it deterministically with the registry's injectable `nanoTime` (a clock that reports one slow call for
   a well-behaved provider), in a unit test that fails on the shared head because the mod's own provider is rejected.
2. Fix it so that startup no longer depends on wall-clock speed, while a provider that is genuinely unbounded or
   misbehaving is still refused. Choose and justify one approach in the record; for example, do not apply the
   per-call wall-clock limit to the mod's own built-in providers, or replace the per-call wall-clock limit with a
   bound that does not depend on host speed (a call count or state bound, which the registry already has), keeping a
   generous total bound with a clear failure message. Do not just raise the number. If this is an API contract other
   mods rely on (check ADRs and docs that describe the atmosphere boundary API), keep it compatible and update the
   documentation it names.
3. Keep every existing registry test passing unchanged unless it pins the wall-clock behaviour you replace; for any
   test you change, explain in the record why the new assertion is at least as strong.

## Verification

The new test failing before and passing after; the atmosphere boundary unit tests; `./gradlew build` with all unit
tests; the full GameTest run with `scripts/check_gametest_log.py` under the heavy-JVM lock; `python -B
scripts/validate_repository.py --require-approved-identity`. If you can, start a dedicated server from the built JAR
under the lock and record that it reaches `Done`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Other round-2 findings (W285 to W289), the atmosphere simulation and sealing, force field behaviour, the ledger, the
status file and QUEUE.
