# TASK W91K: P1-F3's and P1-N2's native kill/restart cuts

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md), `records/W86-DESIGN.md` section 8 ("S2 and
native", the F3 and N2 cuts), `records/W91.md` (its R23 decision) and `records/W91N.md` (R27, R29, the hold and the
runner) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w91k` (new; from the shared branch at `0d6b839a`, which has W91, W91N and W91N2 merged) |
| Branch | `codex/v1.8.0-w91k` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W91K.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W91K/` |

W91R is changing the landed disassembly's removal continuation and one launch precheck in parallel; neither touches the
F3 or N2 paths that these cuts test. Do not edit those files.

## What to do

1. **The cut harness**, based on `scripts/run_v180_rocket_journal_failures.py` and
   `scripts/run_v180_transfer_concurrency.py`, with its unit test. A cut is a hard process kill (no orderly stop) at a
   gated point, followed by restarts on the same world; compare the region files and the journal on disk after each
   start, as the existing cut harnesses do.
2. **The F3 cuts** (W86 section 8): kill with A's snapshot committed and B's change in the held successor (W91N's
   selected-record hold), and kill after an UNKNOWN write (the existing gated UNKNOWN seams, for example
   `FirstEventCutSeams.journalDirectoryForce`). Two restarts each. After each start: A is durable, B is at its last
   acknowledged phase, and no rocket, entity or record is duplicated or lost. **2 cuts, 6 starts.**
3. **The N2 cuts:** after the refused reservation; after the removal's atomic move and before its continuation; and
   after an UNKNOWN removal. Two restarts each, with the same checks plus the pad claim and the return reason. W11B3's
   fourth cut, a governed exact-source return, is added only if the merged code has one; say whether it does. **3 or 4
   cuts, 9 or 12 starts.**
4. Run the harness's tests, `python -B scripts/validate_repository.py --require-approved-identity`, and, if you change
   Java, the whole unit suite and one full GameTest with the unchanged log checker, all on committed source.

If a cut needs a new kill point or seam, add it only in the existing gated release-test families, under R27's rules
(off unless the release-test gate and its property are set; it observes or pauses, and never changes a decision, a
saved format, an acknowledgement order, a proof or a lease). Report every cut outcome against W86's expectation; a
difference is a finding to report, not to adjust.

## Write scope

The new harness and its test, gated kill points or seams in the existing release-test families if needed, new unit
tests for them, and the record. No production behaviour change; no relaxed assertion, limit or timeout.

## Commands and not in this task

As WORKER-RULES says. Other workers (W94B, W90b, W91R) also run tests; take the heavy-JVM lock for every native run and
GameTest, and release it between cuts when another worker waits. Check that C: and D: have more than 10 GiB free
before each heavy start (C: has about 13 GiB). Delete each cut's server copy and exported tree afterwards; keep only
logs, results and manifests. When your heavy runs end, remove your own lock and any `build/gametest/world` with a plain
`rm -rf` after checking that the lock's `owner.txt` names you; if the tool refuses, write the paths in the record and
final answer at once.

Not in this task: W91R, the admission spikes, ADRs, the risk register, status files and the ledger (Root).
