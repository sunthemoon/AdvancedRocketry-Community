# TASK W423: retarget the campaign and release records from candidate C5 to candidate C6

Root: Claude. Date: 2026-11-21. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w423`, branch `codex/v1.8.0-w423` from the shared branch |
| Decision | Root decision R582 (QUEUE row W423), following R581 |
| Model | [TASK-W410](TASK-W410-RETARGET-TO-C5.md) and its record [W410](records/W410.md): do the same for C6 |
| Record | `docs/work/v1.8.0-root-queue/records/W423.md` |
| Write scope | A new `records/C6-SWITCH-REPORT.md` from `C5-SWITCH-REPORT.md`; a superseded line in `C5-SWITCH-REPORT.md`; the fuel report, packet, R-044 draft, plan and checklists A to J where they name the current candidate, its hashes or evidence paths; the release drafts in `docs/releases/v1.8.0/` (RELEASE-EVIDENCE, GATE-STATUS, REQUIREMENT-MAP, TEST-REPORT and the others W411 wrote, plus MATRIX-PROVENANCE-AUDIT, MACHINE-CENSUS, JEI-APPLICABILITY and RECIPE-GRAPH-REPORT only where they name the candidate) where they name C5 as the current candidate; the record. No status file, QUEUE, ADR, register, code or test. |
| Not allowed | Changing any result, expectation, decision or historical statement about C1 to C5. Any JVM, server or client. |

## Facts to use (Root decision R581, 2026-11-21)

- **Candidate C6**: commit `c8fa16e16be87554f26c402546733df6555d15e6`; hosted regression run 44770334930, attempt 1:
  build 14.7 minutes, shards 0 to 3 in 15.3, 21.6, 22.2 and 16.1 minutes, 1,886 GameTests, 4, 4, 4 and 2
  `server-overloaded` entries (maximum 8); governance run 44770334865, attempt 1; JAR
  `advancedrocketry-community-1.20.1-1.8.0-dev.jar`, 16,026,312 bytes, SHA-256
  `9f7e9d1ef708e27fa5778ca3c72ea3b3605f889465ae0d0d1054a890eb808dd4` (artifact
  `v180-build-c8fa16e16be87554f26c402546733df6555d15e6-1`; the same hash in the build evidence's `artifact-sha256.txt`);
  local copy `D:/GitHub/ARCE-Task-Evidence/root-queue/C6/candidate/unpacked/advancedrocketry-community-1.20.1-1.8.0-dev.jar`.
- **What changed from C5**: the restart GameTests of W415 to W417 merged through W418 (R579), the JEI operation
  information of W419 (R574), the operational graph tests and report of W420 (R575), the machine guide of W421 (R573),
  the campaign runner's lock yield of W422 (R580), and the records. All five admission switches and the fuel loader's
  admission stay off.
- **C5's run** (R566): the pre-run of eight families passed (14:13 to 21:21 on 2026-11-20); the full campaign, X01 and
  X02 were not run on C5 and run on C6. C5's results are development evidence.
- **C6's run**: started 2026-11-21 at 00:48 from worktree `D:/GitHub/arce-v180-c6` with
  `D:/GitHub/ARCE-Task-Evidence/root-queue/C6/launch_c6.ps1`: all 78 families with `--yield-seconds 120`
  (`C6/campaign`), then X01 S3 (`C6/x01-s3`) and X02 S5 (`C6/x02-s5`). Results PENDING.
- **Campaign inputs on C6**: the four cells under `D:/GitHub/ARCE-Task-Evidence/root-queue/P3/cells` hold C6's JAR
  (compute each `cell.json`'s SHA-256 yourself); the evidence root is `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/`;
  K13 and K06 were regenerated there (read their `SHA256SUMS.txt`); all 23 K06 packs are byte-identical to C5's.

## What to do

1. Create `C6-SWITCH-REPORT.md` from `C5-SWITCH-REPORT.md` as W410 did for C5: C6's identity, a "C5 is history"
   subsection, the changes above in sections 2.2 and 2.3, every C6 result PENDING; mark C5's report superseded.
2. Retarget the other records and the release drafts to C6 where they name the current candidate, its hashes or its
   evidence paths; keep statements about earlier candidates true.
3. Run `python -B scripts/validate_repository.py`; it must end with `0 failed`. The record lists each file changed and
   why.

## Rules

- Commit on the branch in one reviewable commit per concern plus the record, in the style of AGENTS.md section 6. Do
  not push.
- The last line of your answer is the path of the record.
