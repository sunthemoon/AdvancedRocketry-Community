# TASK W357R: independent read-only review of W357's accounting design

Root: Claude. Date: 2026-11-13. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new, read-only session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w357r`, detached at W357's commit `4cf28c497` |
| Decision | Root decision R440 (QUEUE row W357R) |
| Under review | `docs/work/v1.8.0-root-queue/records/W357-DESIGN.md` and `records/W357.md` at `4cf28c497`, written by a Codex session under [TASK-W357](TASK-W357-FLUID-SIGNED-ACCOUNTING-DESIGN.md) |
| Deliverable | `D:/GitHub/ARCE-Task-Evidence/root-queue/W357-review/REVIEW.md` (outside Git) |
| Write scope | The review file only. Do not change, stage or commit anything in any worktree. |

## Why

W357 designs per-fluid signed accounting, which R-035 lists as needed before admission. It changes a saved form:
escrow ledger root 3, new account rows, operation schema 3, intent schema 3 and receipt schema 4, with a migration
from root 2. AGENTS.md section 13 requires a separate read-only review before such a change is implemented, preferably
by a different engine from the author.

## What to check

Read the design note in full, then check it against the code at `4cf28c497` (production code is unchanged from the
shared head) under `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/` and the persistence
classes it names (`ManagedSavedDataType`, `SavedDataSchemaMigrator`).

1. **Facts.** Every statement about the current code is true: schema numbers, the receipt dictionary of 47 fields,
   the ceilings (2,048-byte receipts, 12,288-byte operations, the 1 MiB root), the settlement steps O1 to O4, C6 and
   L2, the NO_FILE claim, and the W113 and W114 figures.
2. **Soundness.** The balance identities, the partial-application obligation, chain projection, component
   deduplication and shared adjustment ownership give no double count and no hidden loss. A same-total, wrong-fluid
   substitution is caught both in the live observer and in the readback validator.
3. **Knowledge.** UNKNOWN is never turned into zero. ABANDON, missing endpoints, legacy records and REBUILD_SOURCE are
   handled honestly. No resource is rebuilt, refunded or fabricated.
4. **Saved form and migration.** The root-2 to root-3 migration is lossless and keeps the old digests. Refusal and
   failure paths preserve the original files. No catch-and-fallback reader appears. Every variable-length field has a
   bound, and the bounds fit inside the existing ceilings, or the note says what must be measured first.
5. **Old JARs and R-044.** The statements about older JARs are accurate, and the effect on R-044's downgrade boundary is
   stated correctly.
6. **Logging and holds.** The volumes are bounded as stated. No exception is used as an ordinary save control. The
   proposed register text gives scope, duration, log volume and recovery (AGENTS.md section 3.3).
7. **Tests and slices.** The test plan covers the new behaviour without weakening any existing assertion. The slices
   can each be reviewed and merged without leaving a half-integrated format reachable. The estimates are plausible.
8. **The four Root decisions** in section 10: give your own recommendation for each.

## Output

`REVIEW.md` begins with a verdict: APPROVE, APPROVE-WITH-CHANGES or REJECT. Then give numbered findings, each with a
severity (High, Medium, Low or Information), the design section, the code location you checked, what is wrong or
missing, and the change you recommend. Then give your answers to the four decisions, the commands you ran and the
files you read. Give its SHA-256 in your answer. The last line of your answer is the path of `REVIEW.md`.

## Rules

- Read-only. You may run `git` reads, searches and the targeted unit tests named in the design, from a copy in your
  own Temp folder under `D:/GitHub/ARCE-Task-Evidence/Temp/`. Do not take the heavy-JVM lock, and run no GameTest or
  native server.
- English, full sentences.
