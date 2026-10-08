# Airlock native regression triage 18: handoff

Date: 2026-10-08. Delegated Claude source-analysis worker. This is not a
review, an approval, a repair or a Gate result.

## Source and input identity

- Worktree `D:/GitHub/arce-v180-claude-airlock-triage18-20261008`. The fixed
  base `e91ddc16eccd714a936f93db838975f6e6e7ccdb` comes from the task; no Git
  command was run.
- Packet `D:/GitHub/ARCE-Task-Evidence/v1.8.0/reserve-forward-integration17-root-20261008-01/`.
  `RESULTS17.json` and `COMMANDS17.json` name the same source. The NATIVE17
  `runGameTestServer` record has exit 1, and its log SHA-256 is
  `f8491bfbcd7fad0ac691849638bab59a49aa70c40144bda77f563a5b334db0aa` as recorded in the packet. That hash was not recomputed.

## Actions taken

- Completed intake: the task file, plus all 17 governance files listed in
  `TASK-GRAPH-SOURCE16.md`, each read in full.
- Read both JSON files in full, and bounded ranges of the two native logs.
- Read `AirlockDoorGameTests.java` in full, `ServerLevelVolumeWorldView.java` in
  full, and selected airlock/CI task records.
- Wrote only the two new files in this directory, plus REPORT-01.md in the
  assigned evidence directory.

## Result

`ANALYSIS-18.md` is the analysis. The one required failure is installed case
index 3 (`upperOnly=true`, `phase=0`), at the supply prerequisite
(`AirlockDoorGameTests.java:289-297`), before any revocation check. The
diagnostic's seed=OPEN can arise only from the `canSeeSky` branch
(`ServerLevelVolumeWorldView.java:92`). Stale sky light is a hypothesis, not a
proven cause.

## Written versus executed checks

- Executed: none. No Bash, shell, Git, JVM, Gradle, GameTest, server or client
  command was run.
- Written but not executed: proposed checks C1-C4 in ANALYSIS-18.md section 6.
  None is implemented, and C3/C4 need a Root/owner decision.

## Limits

- The full logs were not read, and the other 62 error headers were not triaged.
- Vanilla `canSeeSky` semantics were not re-verified (no JAR access).
  `AtmosphereLevelService` and `VolumeScanTask` were not read.
- No R-021, save-veto, platform or gameplay policy decision is made.

## Unrun Gates

None of G0-G9 was run or assessed: no build, test, runData, diff,
runGameTestServer, dedicated server, restart, V1 or V2. v1.8.0 remains
`IN_PROGRESS`. All earlier native failures stay open.

## Release

All of this worker's process, read, write, source, HEAD, index, branch and
worktree interests are released. Root may commit these two paths:
`docs/work/v1.8.0-airlock-regression/ANALYSIS-18.md` and `HANDOFF-18.md`.
