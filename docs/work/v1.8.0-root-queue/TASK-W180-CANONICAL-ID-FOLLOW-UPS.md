# TASK W180: the remaining canonical identifier bounds (W177's review findings)

Root: Claude. Date: 2026-10-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w180` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w180` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W180.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W180/` |
| Source | `D:/GitHub/ARCE-Task-Evidence/root-queue/W177-review/REVIEW.md` (SHA-256 `e365ef0c…ff26`), findings W177-R1 to R3, all older than W177 |

W177 bounded the canonical identifier in three content parsers (merged at `c48b10313`). Its review found three
related gaps:

1. **W177-R1 (Medium).** Gas table file IDs have no length bound. An ID over 176 characters makes
   `ResourceTables.create` throw instead of returning the catalog's normal refusal and diagnostic. Bound the canonical
   ID as W177 did, and refuse through the existing path.
2. **W177-R2 (Low).** `SatelliteItemData` bounds item-NBT identifiers by their spelling, so a canonical rewrite can fail
   to round-trip. Bound the canonical form there too.
3. **W177-R3 (Low).** The review names the adjacent content parsers that still bound only the spelling. Bound each
   one the same way, or record why one is safe.

Reproduce each with a test that fails first. The refusals must use each parser's existing diagnostic. Change no
saved format, decoder acceptance, bound, budget or protocol.

## Verification

- The new tests alone.
- The `satellite` and `celestial` packages, then the whole unit suite.
- One complete GameTest with the unchanged checker; R131 applies.

## Not in this task

W173's option A, decoder changes, ADRs, the risk register, status files and the ledger. A short read-only review
follows.
