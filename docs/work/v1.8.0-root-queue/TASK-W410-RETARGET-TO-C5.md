# TASK W410: retarget the campaign records from candidate C4 to candidate C5

Root: Claude. Date: 2026-11-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w410`, branch `codex/v1.8.0-w410` from the shared branch |
| Decision | Root decision R556 (QUEUE row W410), following R555 |
| Record | `docs/work/v1.8.0-root-queue/records/W410.md` |
| Write scope | A new `docs/work/v1.8.0-root-queue/records/C5-SWITCH-REPORT.md`; in `C4-SWITCH-REPORT.md` only a superseded note at the top; [FUEL-ADMISSION-REPORT](records/FUEL-ADMISSION-REPORT.md), [OWNER-FINAL-PACKET-DRAFT](records/OWNER-FINAL-PACKET-DRAFT.md), [R044-SIGNOFF-DRAFT](records/R044-SIGNOFF-DRAFT.md), [FINAL-CAMPAIGN-PLAN](records/FINAL-CAMPAIGN-PLAN.md) and the step lists [CAMPAIGN-CHECKLISTS-A](records/CAMPAIGN-CHECKLISTS-A.md) to [-J](records/CAMPAIGN-CHECKLISTS-J.md), only where they name the current candidate, its hashes or its evidence paths; `scripts/campaign_prep/k06_packs.py` and `tests/test_campaign_prep_k06.py` for one change (item 4); the record. No status file, QUEUE, ADR, register, other record or product code. |
| Not allowed | Any JVM, server or client. Changing any result, expectation, decision or historical statement about C1 to C4. |

## Facts to use (Root decision R555, 2026-11-20)

- **Candidate C5**: commit `9172113ad4ecfb9047432f02b97d240fc06130b6`; hosted regression run 44681973284, attempt 1:
  build 14.2 minutes, shards 0 to 3 in 15.0, 21.2, 19.1 and 18.0 minutes, 1,867 GameTests, 3, 5, 1 and 3
  `server-overloaded` entries (maximum 8); governance run 44681973374, attempt 1; JAR
  `advancedrocketry-community-1.20.1-1.8.0-dev.jar`, 15,946,403 bytes, SHA-256
  `e5cd2beb4a38b4ea741c5103b18ba224d3f102ef6231adbc3a0849ad4f1e35c8` (artifact
  `v180-build-9172113ad4ecfb9047432f02b97d240fc06130b6-1`; the same hash is in the build evidence's
  `artifact-sha256.txt`, `artifact-audit.log` and `client-bytecode.json`); local copy
  `D:/GitHub/ARCE-Task-Evidence/root-queue/C5/candidate/unpacked/advancedrocketry-community-1.20.1-1.8.0-dev.jar`.
- **What changed from C4**: W404 (`dd483ca5e`, R545), the gated settlement probe's fluid check for an empty tank;
  W407 (`f3808fed0`, R552), the gated release-test subcommand `perf ticks raw`; W409 (`4d2f97abd`, R554), a unit test
  only. All five admission switches and the fuel loader's admission stay off. Records: [W404](records/W404.md),
  [W407](records/W407.md), [W409](records/W409.md).
- **C4's run** (`D:/GitHub/ARCE-Task-Evidence/root-queue/C4/campaign/campaign-attempt1.json`): started 07:18, stopped
  by Root at 14:12 (R555) during `cargo-B3-S6-REUSE`; 29 families passed, `cargo-C-S4` failed on the probe defect
  (R539), `cargo-C-S4-LOAD` and `cargo-C-S5` were not run by their group runner, the rest and X01 and X02 never ran.
  C4's results are superseded development evidence, not candidate evidence. List the 29 passed families by name from
  the JSON.
- **C5's run**: started 14:13 from worktree `D:/GitHub/arce-v180-c5` with `D:/GitHub/ARCE-Task-Evidence/root-queue/C5/launch_c5.ps1`:
  a pre-run of the eight families W404 named (`C5/prerun`, partial evidence), then all 78 families (`C5/campaign`),
  X01 S3 (`C5/x01-s3`) and X02 S5 (`C5/x02-s5`). Results PENDING.
- **Campaign inputs on C5**: the four cells under `D:/GitHub/ARCE-Task-Evidence/root-queue/P3/cells` now hold C5's JAR
  (Root ran `prepare_campaign_cells.py --replace-candidate`; compute each `cell.json`'s SHA-256 yourself and use
  those values); the evidence root is `D:/GitHub/ARCE-Task-Evidence/final-campaign/9172113ad/`; K13 was regenerated
  there from C5's JAR (`fixtures/K13/`, read its `SHA256SUMS.txt`).

## What to do

1. Create `C5-SWITCH-REPORT.md` from `C4-SWITCH-REPORT.md`: the same structure and rows; the header, section 1 and
   section 2.1 name C5 with the identity above; add a subsection "C4 is history" with C4's run as stated above; add
   W404, W407 and W409 to sections 2.2 and 2.3; every C5 result cell PENDING. Retain C1 to C4 history exactly. In
   `C4-SWITCH-REPORT.md` add one line under the title: superseded by C5-SWITCH-REPORT (R555), kept as history.
2. In the fuel report, the packet, the R-044 draft and the plan, change what names the current candidate: C4 to C5,
   `PENDING_C4` to `PENDING_C5`, C4-SWITCH-REPORT links to C5-SWITCH-REPORT where they mean the current report, and
   the evidence root path. Keep statements about C4's history true, for example "C4's run found the probe defect".
3. In checklists A to J, replace C4's identity, hashes and evidence paths with C5's, and K13's hashes with the
   regenerated ones; keep every step, expectation and marker otherwise as it is.
4. `k06_packs.py` refuses any JAR but C4's (a fixed hash). Replace the fixed hash by a required argument
   `--expect-sha256`, keep the refusal on a mismatch, update the test, then run it for C5 into
   `D:/GitHub/ARCE-Task-Evidence/final-campaign/9172113ad/fixtures/K06/` and compare every pack's hash with C4's
   `final-campaign/659bbcb6a/fixtures/K06/`; record whether they are identical and, if not, why. Update the
   checklists' K06 hashes if any changed. Python tests must also pass on Linux.
5. Run the K06 test and `python -B scripts/validate_repository.py`, which must end with `0 failed`. The record lists
   each file changed and why, and the K06 comparison.

## Rules

- Commit on the branch in one reviewable commit per concern plus the record, in the style of AGENTS.md section 6. Do
  not push.
- The last line of your answer is the path of the record.
