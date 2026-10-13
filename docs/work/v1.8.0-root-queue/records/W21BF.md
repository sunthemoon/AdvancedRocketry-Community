# W21BF record: W21b review fixes

Task: [TASK-W21BF-REVIEW-FIXES](../TASK-W21BF-REVIEW-FIXES.md). Implementer: a headless Claude
session (Opus 5.5), 2026-10-13, in `D:/GitHub/arce-v180-w21b-registry-schema4` on the local branch
`codex/v1.8.0-w21b-registry-schema4`, starting from `b76776e3`. The tree was clean at the start.
Review: `D:/GitHub/ARCE-Task-Evidence/root-queue/W21b-review/REVIEW.md`. Its SHA-256 matched the
task's `d2a7401c…0eb9d4a`, and I read it in full, including the probe appendix. Nothing was pushed
or merged, and no other agent was started.

## Commits

Each finding has its own commit with its tests. F2 comes first because F1's O(touched) cost
depends on it.

| Commit | Finding | Change |
|---|---|---|
| `dfa4fa41` | F2 | Ground changes check the touched job and its instance, before and after the change, and the job's device, processor and instance peers through the derived indexes. They no longer run `RegistryInvariants.plan` over the whole registry. An `INVARIANT_FAILED` result names its cause, for example `GROUND_PHASE_MISMATCH`. The instance ledger indexes ground-origin instances by job, so the backlink and retention lookups no longer scan every instance. |
| `0cd9fc1b` | F1 | Only START and account creation run the whole-root encode and bounded read. Every other ground change is bounded by the reservations, an O(1) check. The whole-root preflight itself is about three times faster. Details follow. |
| `8825087b` | F3 | `instance release` returns IDEMPOTENT for an instance that is not QUARANTINED, before the ground check. |
| `31c3a620` | F6 | Satellite root 4 maps to the literal `"v1.8.0-ground-surveys"`. |
| `fb9e470a` | F8 | The native harness records `git HEAD`, refuses an unclean tree, and with `--build-candidate` builds the jar and records its SHA-256 when the build ends. |
| `754405bc` | F4 | The V180 language provider translates `OFFLINE_REPAIR_REQUIRED` in English and Chinese. The commit includes the `runData` output. |

## F1 in detail

- **Which changes skip the whole-root preflight.** The skip covers every ground change that adds
  no record:
  - non-final and final survey advances;
  - claims, both rebinds and non-final analysis advances;
  - the credit, unless it creates the owner's account;
  - cancel;
  - job release and ground `instance release`.

  Root's decision lists advances, claims, rebinds and credits. I applied its rule ("only for
  operations that add records") to the final survey advance, cancel and both releases as well.
- **The O(1) bound.** Each reservation is the largest encoding its record can reach. An account
  encodes in at most 128 bytes. The root's fixed fields and list headers fit in
  `RecordSizer.ROOT_FRAMING_BYTES`, which is 4 KiB. By my count they need about 300 bytes, and
  a test checks that an empty root fits in 4 KiB. So this sum bounds the encoded root:
  - the satellite, mission, instance and ground reservations;
  - plus the account count × 128;
  - plus 4 KiB.

  When the sum is at most 16 MiB, the change needs no encode. A test checks both constants and
  the bound on a mixed registry.
- **Fallback, so no bound is relaxed.** Admission keeps the sum near 14 MiB, so only a restored
  root above its section budgets can exceed the bound. On such a root, every change still takes
  the whole-root preflight. A test covers this path.
- **Ground `instance release`** uses the same rule through `GroundSurveys.fitsWithoutNewRecord`.
- **A faster whole-root preflight.** This was needed to meet the START budget. No quota or check
  changed.
  - `BoundedSavedDataIo.readCompressed` puts an 8 KiB buffer between the gzip stream and the
    quota stream. The quota still counts exactly the bytes the NBT reader takes. The read-ahead
    can only lower the compressed estimate, so it can only refuse more. The existing 64 KiB
    inflated slack covers the buffered bytes. This reader serves every managed SavedData type,
    so their disk reads also get faster.
  - `validatePayload` no longer deep-copies the payload. Writing and reading never change it.
  - The new overload `validatePayload(payload, type, maxPayloadBytes)` measures the payload's raw
    size during its own write. The preflight therefore no longer serializes the 16 MiB root a
    second time. A test pins the bound at exactly 16 MiB and 16 MiB + 1.
  - The validation write is a throwaway copy, so it deflates at `BEST_SPEED`. The expanded and
    heap quotas do not depend on the deflate level. Deflate's stored blocks add only a few bytes
    per 64 KiB, so a payload within 16 MiB compresses below the 16 MiB + 64 KiB limit at any
    level. The real disk writer (`BoundedSavedDataIo.write`, `CheckedSavedDataFile`) is
    unchanged.
  - `SatelliteRegistryPayload.encodeUnchecked` lets the preflight skip `encodeCurrent`'s own size
    pass. The edited root is measured once. `encodeCurrent` still checks its bound for saves.
- **Measurements.** `GroundSurveyScaleTest` uses the review's probe registry: 2,000 AVAILABLE
  survey-origin instances and 700 READY jobs. A second START adds an analysis job, so the
  registry has 2,002 instances and 702 jobs, about 1.37 MB. The commit message of `0cd9fc1b`
  says "2,001 instance and 701 job"; the exact counts are these.

  | Measurement (median) | Budget | Before | After (full suite at `754405bc`) |
  |---|---|---|---|
  | Survey advance on the probe registry | 1 ms | 88 ms (review) | 64.0 µs |
  | Non-final analysis advance on the same registry | 1 ms | not measured | 44.6 µs |
  | START on a 16,743,201-byte root (5,242 large missions) | 500 ms (docs/17 spike) | 891 ms (with only the F1 restriction) | 337.2 ms (max 349.8 ms) |

  The isolated runs gave START medians of 307 and 328 ms. The test also asserts that no advance
  calls the whole-root preflight. A counting sizer proves which operations call it: START and
  account creation do; the advances, rebinds, claim, credit, cancel and both releases do not.

## Changed files by area

- Satellite mission: `GroundSurveys`, `GroundSurveyLedger`, `InstanceLedger`, `RecordSizer`,
  `ResourceMissions`. `SatelliteMissionRegistry` is unchanged at 798 lines.
- Satellite persistence: `CodecRecordSizer`, `SatelliteRegistryPayload`.
- Persistence migration: `BoundedSavedDataIo`, `ManagedSavedDataType`.
- Data generation: `V180LanguageProvider` and its generated `en_us.json` and `zh_cn.json` under
  `src/generated/v1.8`.
- Tests:
  - new: `GroundSurveyScaleTest`, `SatelliteOperationCodeLanguageTest` and
    `tests/test_run_w21b_registry_upgrade.py`;
  - extended: `GroundSurveyInvariantsTest`, `GroundSurveysTest`, `GroundSurveyBoundsTest`,
    `BoundedSavedDataIoCapacityTest` and `SavedDataSchemaMigratorTest`.
- Script: `scripts/run_w21b_registry_upgrade.py`.

## Changed assertions

No test, assertion, timeout or budget was removed or loosened. Two existing tests changed
because Root's decisions changed the behaviour they pinned.

- **`GroundSurveyInvariantsTest.duplicateRunningClaimsKeepAvailabilityButBothReleasePathsRefuse`**
  (F3).
  - Its first loop called `instance release` on the AVAILABLE instances of two jobs with
    duplicate RUNNING claims, and expected `OFFLINE_REPAIR_REQUIRED`. It now expects IDEMPOTENT
    with `changed() == false`, and checks that the saved root is unchanged.
  - The job-release refusal and the `instanceReleaseProblem` query assertions are kept.
  - The second half of the test is unchanged: the instances are quarantined there, and both
    release paths still refuse.
- **`GroundSurveyBoundsTest.groundInstanceReleaseChecksRootAndEpochBeforePublishingOrAdvancingTime`**
  (F1).
  - Its refusing sizer now also reserves 16 MiB for the job. The reservations then cannot bound
    the root, so the release still reaches the whole-root preflight that the sizer refuses.
  - The asserted results are unchanged: STORAGE_BUDGET or CAPACITY_REACHED, an unchanged
    instance, and an unchanged clock.

## Commands and results

Logs and copies of the JUnit XML files are in
`D:/GitHub/ARCE-Task-Evidence/root-queue/W21bF/verification-20261013`. All Gradle commands used
`gradlew.bat --offline --console=plain`. Before the heavy runs, C: had 23 GB free and D: had
393 GB.

| Command | Result |
|---|---|
| `test --tests '*GroundSurvey*'` after F2 | 89 tests, 0 failures. |
| First `test --tests '*GroundSurveyScaleTest' --tests '*GroundSurveyBoundsTest'` | 12 tests, 1 failure: START took 891 ms at the near-limit root. This led to the preflight speed-ups; the budget was not changed. |
| A scratch profiling test (deleted, never committed) | It showed that the bounded read, the payload copy and the second size pass dominated. |
| `test --tests '*satellite*' --tests '*persistence.migration*' --tests '*BetaOperationalReportTest'` after F1 | 312 tests, 0 failures. |
| `test --tests '*GroundSurvey*' --tests '*ResourceMissions*' --tests '*OperatorRecovery*'` after F3 | 103 tests, 0 failures. |
| `test --tests '*SavedDataSchemaMigratorTest' --tests '*GroundSurveyMigrationTest' --tests '*SatelliteRootThreeTest'` after F6 | 19 tests, 0 failures. |
| `python -B -m unittest tests.test_run_w21b_registry_upgrade -v` | 5 tests, OK. |
| `python -B scripts/run_w21b_registry_upgrade.py --help` | Passed. |
| `runData` under the lock (11:34–11:35) | BUILD SUCCESSFUL. The only drift was the new key in the two v1.8 language files, committed in `754405bc` (`rundata-01.log`). |
| `test --tests '*SatelliteOperationCodeLanguageTest' --tests '*V180StationControlDataTest'` | 5 tests, 0 failures. |
| `build_candidate` called on this worktree with the real `gradlew.bat ... jar` | It recorded HEAD `754405bc`, a clean tree, and jar SHA-256 `a066ab94…5b7ef12` (`harness-build-check.txt`). I did not repeat the native server run. |
| Whole unit suite, `test`, at `754405bc` | BUILD SUCCESSFUL: 2,763 tests, 0 failures, 0 errors, 0 skipped. W21b had 2,753; this task adds 10. The station warp commit median at 4,096 stations was 213.2 ms (`full-unit-01*`). |
| `runGameTestServer` under the lock (11:51–12:03) at `754405bc` | BUILD SUCCESSFUL: all 848 required tests passed, including both ground-survey registry GameTests (`gametest-console-01.log`, `gametest-latest-01.log`). |
| `python -B scripts/check_gametest_log.py build/gametest/logs/latest.log` | PASS: FATAL 0. All 80 ERROR and 220 WARN entries matched the 73 unchanged expectations with their declared counts (`gametest-log-checker-01.log`). |
| Final `clean` | BUILD SUCCESSFUL. This worktree's `build/` directory, including the GameTest world, is gone. The evidence directory holds 4.8 MB. |

## Choices where the decisions left room

- The final survey advance, cancel and both releases follow the "adds no record" rule; see F1.
- A restored root whose reservations exceed the root bound keeps the whole-root preflight for
  every ground change. I did not add a cheaper path for that state, because admission cannot
  reach it.
- F2 keeps no global check, so no refusal-cause log line is needed. The refusal reason carries
  the cause instead.
- F4: the V180 provider translates every code after `INSTANCE_NOT_FOUND`, and an unlisted later
  code fails DataGen, as in the v1.6 provider. The frozen and unregistered `V160LanguageProvider`
  is unchanged. It would still throw on the new code if it were ever run again.
- F6: `SatelliteLimits.REGISTRY_FORMAT_EPOCH` stays as the current-epoch constant. A test checks
  that it names the current root's epoch, and pins root 4 to its literal.
- F8: `--tested-commit` is now optional and must name HEAD (at least 7 hex digits). The harness
  accepts `--repository`, which defaults to the script's checkout. A build counts only if it
  wrote the jar within 2 seconds of the build's start time or later; an older jar is refused.
  Without `--build-candidate`, the summary records `candidate_built_by_harness: false`.

## Not done

- F5, F7, F9 and F10 need no code; Root records them, as decided.
- Status files, the ledger and the ADR-051 note are Root's.
- The native harness was not run again. Only its build step was exercised, on this worktree.

## Known risks

- **START benchmark headroom.** START measured 307–337 ms against 500 ms. Heavy machine
  contention, like the load behind F9's 602 ms station miss, could push it over. The test
  asserts the median of 5 runs.
- **The shared reader changed.** `BoundedSavedDataIo` reads every managed SavedData type. The
  change only adds a buffer, and the full suite and GameTests cover it. Still, it is persistence
  code outside the W21b ground files. Under AGENTS.md section 13, the read-only review before
  merge should cover it and the deflate-level argument.
- **Over-budget restored roots.** On such a root, ground changes keep the W21b cost, which is
  about START's cost per change. That would be slow under W21c's 32 advances per tick. Only a
  legacy root above its section budgets can be in this state.
