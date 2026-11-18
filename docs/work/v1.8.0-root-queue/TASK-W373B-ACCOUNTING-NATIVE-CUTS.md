# TASK W373B: Slice E part 2b, the new native accounting cuts

Root: Claude. Date: 2026-11-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w373b`, branch `codex/v1.8.0-w373b`, at the shared head with the activation merged (`5951d6df2` or later) |
| Decision | Root decision R505 (QUEUE row W373B) |
| Design | [W357-DESIGN](records/W357-DESIGN.md): sections 2 (quantities, signs and knowledge), 3 (step-by-step recording), 4.2 and 4.3 (migration, older JARs and R-044), 6 (holds and logging) and the third paragraph of 7.3 (the new cuts) |
| Prior work | [W365](records/W365.md), [W366](records/W366.md) and [W372](records/W372.md); the two smokes of W372D: `scripts/run_v180_fluid_loader_restart.py` case `LF_C2_SOURCE_BEFORE` and `scripts/run_v180_cargo_reconciliation.py` case `FC_LOAD_L1_ACK` |
| Record | `docs/work/v1.8.0-root-queue/records/W373B.md` |
| Write scope | The record; gated probe and fixture code under `resourceescrow/` that the release-test gates already guard, and its unit tests; new or extended Python runners and oracles for these cases and their tests; new family entries in `scripts/run_v180_integrated_campaign.py` and its test; `escrow-probe-cases.tsv` entries. No production behaviour, no ceiling or limit, no log-manifest allowance. |

## Why

Design section 7.3 requires native cuts that show the accounting itself survives a stop and two restarts. W372's two
smokes showed only that a root-3 ledger is written, read back and retired. These cases are ordinary disposable-process
cuts, not X01 power loss or X02 fault injection.

## What to do

1. **Write the case plan first**, as the record's first section: each case, its cut point, the starting world, what
   is checked at the cut and after each restart, and the oracle that checks it. Then implement it. Root reviews the plan
   with the diff.
2. **Account-bearing cuts**, each with a forced stop and two restarts, for fluid cargo load and unload, and for the
   fuel loader where it shares the code:
   - immediately before and after the L1 and O1 replacements;
   - before and after the account-checked L2;
   - before and after the O3 and O4 replacements and callbacks;
   - before and after pruning is persisted.
3. **Accounting outcomes**, each natively with two restarts:
   - a known positive loss, and a known negative loss (a gain);
   - a two-fluid substitution;
   - a chained bank;
   - an ABANDON with an unknown endpoint;
   - an exact UNKNOWN candidate retry;
   - a mismatching typed stable readback that must not release.
4. **Format boundaries**:
   - root-2 upgrade cuts: a world with a root-2 ledger and open operations, started on the activated JAR, stopped
     during and after the upgrade, then restarted twice;
   - root-3 old-JAR refusal, with C3's pinned candidate JAR as the root-2 reader (its SHA-256 is in
     [C3-SWITCH-REPORT](records/C3-SWITCH-REPORT.md), section 2.1):
     - take a whole-world backup of the root-2 world before the upgrade;
     - after the activated JAR has written root 3, start a copy on the old JAR. It must refuse with the designed
       reason and must not write a downgraded ledger. Record exactly which files change: C3 recorded the old JAR
       rewriting `level.dat` and `level.dat_old` before it rejects (R-044, design section 4.3), so a refusal is not
       byte preservation;
     - restore the pre-upgrade backup and start it on the old JAR: it must start, with the original ledger;
     - start the copy the old JAR refused on the activated JAR twice, and record the result.

     This is the evidence for R-044's sign-off text. Record every failure as it is; do not waive or fix it.
5. **Oracles** check exact account rows, signed residuals, KNOWN or UNKNOWN as the design says, epochs, and that no
   resource is created or lost. A fresh fluid case must not pass through the legacy UNKNOWN branch.
6. **Run every case natively** on a locally built JAR of your branch head under the heavy-JVM lock, and add each to
   the campaign driver as a family. If a case shows a production defect, do not fix production: record the evidence
   and stop that case.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Never relax an oracle.
- Use `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`. Use the heavy-JVM lock with the 90-second gap, keep at least
  10 GiB free on C: and D:, and delete server copies after each run. Launch detached Python with the real `python.exe`,
  not the pyenv shim. Keep evidence under `D:/GitHub/ARCE-Task-Evidence/root-queue/W373B/`.
- If you change Java, run its unit tests, the full unit suite once, and one full GameTest run with the log checker.
- Commit on the branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- A read-only review by a Codex session follows before the merge.
- The last line of the record, and the last line of your answer, is the path of `records/W373B.md`.
