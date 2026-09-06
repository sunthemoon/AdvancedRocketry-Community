# v1.0.0 populated Beta-world upgrade verification

Date: 2026-09-05. Development slice verified; the full v1.0 milestone remains
IN_PROGRESS. This is not a frozen release candidate, independent audit or human
Gate approval. [Exact commands](COMMANDS.md) and [mechanical evidence audit](verification/fixture-audit.json).

## Artifacts and immutable fixture

- Source: unchanged published `1.20.1-0.9.0-beta.1`, SHA-256
  `fbddf66938000cba369a83d4a22ff36b5ff1c9c635a0abd14f672b454e3946ad`.
  [Accepted-source preparation](BASELINE.md) records the download and audit.
- Tested development JAR: `1.20.1-1.0.0-dev`, 1,238,476 bytes / 766 entries,
  SHA-256 `cf077ef750149f6b957ac4e8dc8a7da8e5cb91eafbe80703859157aac34a49b8`.
  Clean build reproduces the prior transaction-fix bytes. No Java, resource,
  schema, protocol, dependency or safety-budget change is made by this slice.
- Original world: 59 files, retained as an
  [8,403,030-byte ZIP](verification/populated/original-beta-world.zip), SHA-256
  `9022b2fdf4537168bc33f1db06f96c8d5ce150c221177cd4e0890b037f9bed1c`.
  Every uncompressed archive entry is checked against the stopped-world
  size/SHA manifest, not just the ZIP's outer hash. The original directory and
  every file remain unchanged after both independent candidate-copy sequences.
- Windows 11, Java 17.0.7, Forge 47.4.10, JEI absent, loopback port 25612,
  online-mode=true, no connected players. Existing PCL login is not repeated
  or counted as new client evidence.

## Actual representative state

The Beta itself creates the world with native commands and its existing
release-test operations. No saved-data root is synthesized or rewritten offline.

| Object | Initial Beta state | Verified upgrade/restart outcome |
|---|---|---|
| Energy-paused electrolyzer | 40 actual processing ticks from progress 0; two empty canisters, 1,000 water, energy 0, active recipe retained | Complete machine NBT stays identical through every reopen |
| Running electrolyzer | Progress 25, energy 19,500, eight empty canisters, 4,000 water | Continues processing; final primary-copy progress 26 after two completed recipes, energy 15,480, water 2,000, four empty + two hydrogen + two oxygen canisters |
| Sealed Moon room | Supplied vent, oxygen 3,973, energy 29,160, partial oxygen phase 2 | All 26 non-vent positions match; supply resumes; energy and oxygen/partial-phase ledger conserves exactly |
| Earth rocket | Five blocks, FUELED, 1,000 fuel | Exact entity/logical/owner/transaction IDs, position, full snapshot and flight data preserved |
| Moon rocket | Actual Earth-to-Moon flight, LANDED, 628 fuel after an exact 372 debit | Same identities, committed debit and complete stored structure/inventory preserved |
| Two rocket chests | Each has exactly 17 diamonds at slot 0 and 64 iron ingots at slot 26 | Full payload equality, exactly two rocket authorities, ten source positions remain air, no dropped item entities |
| Space station | One generated station, ownership transferred to `...1002`; previous owner `...1001` is a member | Full owner/member/invitation/region/environment NBT unchanged; all 289 platform blocks match individually |
| Satellite/research state | Three satellites and active/ready/claimed missions; earned research 20 and discovery retained | Active mission becomes READY after its actual deadline; old claimed mission replay returns ALREADY_CLAIMED on every reopen; research stays 20 |

Different copies legitimately run for different tick counts. The reuse copy
ends at two completed recipes plus progress 29 and energy 15,420; its complete
material ledger also balances. The verifier does not equate changing clocks,
processing and oxygen consumption with data corruption, or ignore those fields.
All five managed roots retain schema 2 and `format_epoch=v0.9.0-beta`.

## Actual executions and coverage

1. [Primary capture/upgrade](verification/populated/summary.json): Beta capture,
   first development open, restart 1, restart 2; **four clean Java processes**.
2. [Original-fixture reuse](verification/reuse/summary.json): a second untouched
   candidate copy, first open and two restarts; **three clean Java processes**.
   It reuses the exact original ZIP/manifest rather than regenerating a different
   world. The current harness revision is archived and hash-bound there.
3. [Post-run audit](verification/fixture-audit.json): recomputes all six
   field-level comparisons, verifies seven full logs and fourteen actual
   capture/flush chat receipts, and re-hashes both archive contents and the
   original directory. No missing evidence or mismatch is found.

Native command storage copies complete machine, vent and entity NBT. The
diagnostic function separately tests entity type/dimension, geometry and
single-material authority, observes successful `save-all flush`, then stops
the server. It never edits observed resources to satisfy comparisons. The
private test server permits functions at level 4 only for this save/stop
capture; the diagnostic datapack is not included in the mod JAR and is not a
production-world installation recipe. Test-only forced chunks are explicit,
bounded setup operations, not a claim about reference-load ticket behavior.

## Regression commands

| Command/check | Actual result | Evidence |
|---|---|---|
| `gradlew.bat clean build` | 0; 22 s, cache used; reproduced the same JAR | [Build log](verification/gradle-clean-build.txt) |
| `gradlew.bat test --rerun-tasks` | 0; 45 s; all 362 Java tests executed, 70 suites, no failures/errors/skips | [Rerun log](verification/gradle-test-rerun.txt), [JUnit XML](verification/junit/) |
| `gradlew.bat runData` | 0; generated resource diff empty | [DataGen](verification/gradle-run-data.txt), [diff](verification/generated-diff.txt) |
| `gradlew.bat runGameTestServer` | 0; all 44 required tests pass | [GameTest log](verification/gradle-game-tests.txt) |
| Python server-harness regression discovery | 0; 101 tests, including 25 new upgrade tests | [Final regression log](verification/python-server-tests-reuse.txt) |
| Strict repository governance | 0; 45 checks, zero pending/warnings/failures | [Governance](verification/repository-validation.txt) |
| Artifact / client-import / celestial audits | 0 each | [Artifact](verification/artifact-audit.txt), [imports](verification/client-import-audit.txt), [identity](verification/celestial-audit.txt) |
| Planning/source-package validation | 0; 11 plans and all 33 original input sizes/hashes | [Planning audit](verification/planning-validation.txt) |
| Generated/historical-release diffs and `git diff --check` | 0 each | [Git results](verification/git-results.txt) |
| Full `git diff --exit-code` | 1; intended uncommitted development tree, not a clean RC | [Development diff](verification/development-tree.diff.txt) |

The earlier complete Python suite remains the separate 735-test result. This
slice reruns all 101 server-harness tests, not the complete expanded Python
suite. The [718-input inventory](verification/source-inventory.json) verifies
that all prior 714 runtime/test/harness inputs are unchanged; only the three
new Python modules and their test file are added. Fresh-world GameTest/default
configuration diagnostics are preserved, not called warning/error-free logs.
The [additional link audit](verification/work-links.json) checks the active work
documents, including untracked additions. [Checksums](checksums.txt) cover the
complete evidence bundle; [verification checksums](verification/checksums.txt)
also cover the execution subdirectory independently.

## Retained failures and harness corrections

- [First attempt](verification/room-chunk-failure/summary.json), exit 1: a room
  wall crossed the loaded chunk boundary. The console rejected the fill;
  the supplied-room assertion failed. Setup now loads the entire bounded
  footprint and waits for readiness before placement. The original 30-second
  assertion timeout is unchanged, and unloaded-position console errors fail.
- [Second attempt](verification/function-receipt-failure/summary.json), exit 1:
  the server saved/stopped cleanly, but a native function suppresses the normal
  console save message. Capture now checks the actual save command's success
  result and its conditional chat receipt, plus persisted success and exit 0.
  The preserved native [NBT](verification/function-receipt-failure/actual-command-storage.dat)
  also shows why entity type is explicitly observed instead of assuming an
  `id` field in native entity data. No production behavior or assertion budget
  was changed for either harness failure.
- [Initial successful matrix](verification/initial-upgrade/summary.json) remains
  separate from the later geometry/claim-replay coverage; it is not relabeled
  as the final, broader harness result.

## Remaining milestone work

`V100-DATA-02-CAPTURE` and the development `V100-DATA-02-RESTART` are verified.
The final-candidate rerun remains explicit in the [implementation plan](../v1.0.0-implementation-log.md).
Pinned Forge/JEI compatibility, four-hour reference load, real-GPU/client
scenarios, passengers, independent review, uninvolved installation, candidate
identity/reproduction and human release approval remain. This fixture has no
real passengers or pending invitations and does not replace V1/V2. No v1.0
Gate, release status or tag is self-approved.
