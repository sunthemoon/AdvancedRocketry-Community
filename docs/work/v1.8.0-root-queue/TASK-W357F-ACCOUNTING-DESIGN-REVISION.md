# TASK W357F: revise W357's accounting design after its review

Root: Claude. Date: 2026-11-13. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w357`, branch `codex/v1.8.0-w357` at `4cf28c497` (W357's delivery) |
| Decision | Root decision R441 (QUEUE row W357F) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W357-review/REVIEW.md` (W357R, a read-only Claude session; APPROVE-WITH-CHANGES; SHA-256 `21f26acc525e4945d521f0edb05e9e342fd1dfad16bbd7373caede34599ddc14`) |
| Write scope | `docs/work/v1.8.0-root-queue/records/W357-DESIGN.md` and `records/W357.md` only. No code, test, script or Root-owned file. |

## What to do

Revise the design note so that it resolves every finding of the review, then append a short revision section to
`records/W357.md`.

1. **Finding 1 (migration and the pre-start validator).** Write into section 4.2 the four concrete changes the review
   recommends: a dedicated escrow branch in `SavedDataSchemaMigrator`; the root-2 and root-3 epochs pinned separately
   in `ManagedSavedDataType.formatEpoch(int)`, with the migrator checking the root-2 epoch; `supportsSchema` limited
   to exactly 2 and 3; and the baseline check split, so that the source file is compared with the pinned root-2
   re-encoding and the migrated root is validated on its own. Add a test that a root-1 escrow file is still refused.
2. **Finding 2 (mixed components).** Write the rule in sections 2.3, 4.1 and 4.2:
   - the intent is schema 3 whenever at least one member has an account row;
   - the commitment covers exactly the row-bearing members, in member order;
   - item and legacy members keep schema-3 UNKNOWN receipts;
   - a component with any legacy fluid member has total status UNKNOWN;
   - admission refuses a new fluid operation whose prospective component contains a legacy schema-2 fluid record.
   Add both mixed cases to section 7.1.
3. **Finding 3 (receipt schema 4 in the lock predicates).** Require one shared settlement-receipt test used by every
   lock and release predicate, in place of equality with one constant, and a test that runs each predicate on the
   same receipt in schemas 3 and 4 and expects equal results.
4. **Finding 4 (pinned capacity).** Remove the double-charge wording. State that Slice A measures the true worst
   account row and the largest real component, and proposes the smallest row ceiling that fits (the review suggests
   about 2,048 bytes). State that Slice A reports, before Slice B is written, the new value of every pinned count the
   format moves: `EscrowCargoCapacityTest`'s 61, the exact 1 MiB benchmark fixture, and the saturation families
   `w336-saturation-fluid-load`, `-fluid-unload` and `-fuel`. Root then records the accepted numbers in a decision
   row (R441's rule below). No ceiling is raised.
5. **Findings 5 to 9 (Low).**
   - Amounts are aggregated per endpoint and key, and the bank placement stays protected by the existing fuel-vector
     signature; cite the code if two banks can never hold the same key.
   - The projected-baseline and later-prefix rules apply to every endpoint that has a prefix chain, loader tanks and
     cargo devices included, or explain why cargo cannot chain.
   - The whole-world backup is the operator's step before the upgrade. The migrator's own backup covers managed files
     only. The `BACKUP_LIMIT` refusal goes into the proposed R-035 text (scope the world; until the operator archives
     old migration backups; one ERROR per start).
   - Under ACCEPT_CURRENT a remaining outstanding obligation is recorded as the signed discrepancy and shown in the
     preview as a half-applied transfer.
   - The log bound reads "one WARN per held operation; at most 64 operations held at once". Say whether an ID is
     forgotten on release.
6. **Root's decisions.** Record in section 10 Root's answers of R441, given below, in place of the open questions.
   Update the status line to REVISED after W357R, under R440 and R441. Change Slice E's estimate to 3 to 4 days and the
   total accordingly. State that Slice A is dispatched first and alone, and that every saved-form slice (B, and D's
   receipt work) gets its own read-only review before it merges.

## Root's answers (R441)

1. **Scope and discrepancy policy:** approved as recommended, with findings 5, 6 and 8 written in. There is no item
   count account in v1.8, because R-035 names only per-fluid accounting.
2. **The same-file account list:** approved, on the condition of Slice A's measurements. The row ceiling becomes the
   smallest measured size that fits the worst real row and component. A failed fit comes back to Root, never as a
   raised ceiling.
3. **Root-2 migration with legacy UNKNOWN holds:** approved, with findings 1, 2 and 7 resolved. The whole-world
   pre-start refusal of a root that no longer fits is accepted. A root-1 file stays refused.
4. **Schedule:** approved. Slice A first and alone, then B to E, with read-only reviews of the saved-form slices. Root
   writes the R-035 and ADR-065 text before any code merges.

**Re-pinning rule (R441).** Capacity counts that move only because the format deliberately grows (61 concurrent fluid
operations, the 1 MiB benchmark fixture, the saturation families' byte refusal) may be re-measured with the real codec
and re-pinned. Each re-pin is recorded with the old value, the new value and the measurement. No ceiling, limit or
assertion purpose changes, and no refusal side-effect check is removed (AGENTS.md section 5). Root accepts the new
numbers in a decision row before Slice B merges.

## Rules

- Docs only. Commit on the branch with a message in the style of AGENTS.md section 6. Do not push.
- English, full sentences, UTF-8 without BOM, LF line endings. Links only to tracked files.
- The last line of your answer is the path of `records/W357.md`.
