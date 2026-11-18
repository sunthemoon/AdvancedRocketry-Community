# TASK W387: X03-C, the repair family on the accounting-enabled build

Root: Claude. Date: 2026-11-19. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w387`, branch `codex/v1.8.0-w387`, at the shared head after X03-B's merge (W380 to W380J) |
| Decision | Root decision R507 (QUEUE row W387) |
| Design | [W367-DESIGN](records/W367-DESIGN.md), revision 3: section 4 (root 2 and activated root 3), section 5.3 (cases and controls), section 9, and the X03-C row of the slice table |
| Prior work | W380's record (`records/W380.md`, merged with X03-B), all sections, above all "W380J follow-up (R501)"; W380R's review and its W380R2 section in `D:/GitHub/ARCE-Task-Evidence/root-queue/W380-review/REVIEW.md` |
| Record | `docs/work/v1.8.0-root-queue/records/W387.md` |
| Write scope | `scripts/repair_v180_rebuild_source.py`, `scripts/x03_repair_oracle.py`, `scripts/run_v180_x03_repair.py`, the X03 family in `scripts/run_v180_integrated_campaign.py`, their tests, the shared accounting decoder only if a case proves it wrong, `escrow-probe-cases.tsv` entries, and the record. No production code, no format or activation change. |

## Why

X03-B passed on root 2, before the activation. `EscrowAccountingFormat.ENABLED` is now true, so the operator repair
must work on root-3 worlds, and on a root-2 world that the activated JAR upgrades.

## What to do

1. **The order-independent rocket check (W380J's observation).** With the stale copy listed first, `plan` stops at
   `RESOURCE_DIFFERS` and never reports `SECOND_PHYSICAL_ROCKET`. Make the check for a second physical rocket of the
   same logical identity independent of entity order, so that an FF_STALE_PREPARED world reports it in every order.
   Add a test for both orders. The refusal itself must not weaken.
2. **The accounting-enabled profile.** The runner plans with `repair.PROFILE_DISABLED`. Use the enabled profile on
   the activated JAR, and check that the plan's pinned table of root stamps (ledger root 3) matches
   `ManagedSavedDataType`.
3. **The cases of section 5.3 on a locally built JAR of the shared head**, under the heavy-JVM lock:
   - the fresh-root-3 case: the complete accepting sequence as W380J ran it (the three refusal seeds, both cycles with
     the functional descendant, both negative controls, and the whole-world replacement with its observation);
   - the upgraded retired-root-2 case, exactly as section 5.3 says: a root-2 world in the FLOOR_CONFLICT state,
     seeded on a root-2 JAR (W380J's JAR, SHA-256 in its `results.json`); `plan` under the enabled profile refuses
     with `ROOT_NOT_CURRENT`; the qualifying start on the activated JAR migrates the ledger, fences T and stops
     cleanly; a new backup is taken; the procedure runs. Never fake the activation with a schema edit;
   - the accounting checks of section 4: UNKNOWN for a missing baseline, the signed audit values, `fluid_accounts`
     preserved exactly, and the refusal of a future schema.
4. **Contradictions.** If a case shows a new native fact, stop, report it with its evidence, and do not admit it
   yourself. If a case shows a production defect, do not fix production.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Never relax an oracle.
- Use `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`. Use the heavy-JVM lock with the 90-second gap, keep at least
  10 GiB free on C: and D:, and delete server copies after each run. Launch detached Python with the real `python.exe`,
  not the pyenv shim. Keep evidence under `D:/GitHub/ARCE-Task-Evidence/root-queue/W387/`.
- Run the six Python suites W380J ran, and `EscrowCargoCutProbeTest`.
- Commit on the branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- A read-only review by Claude follows before the merge.
- The last line of the record, and the last line of your answer, is the path of `records/W387.md`.
