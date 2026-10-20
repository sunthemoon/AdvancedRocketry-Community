# TASK W119: W27c2, the satellite registry's proof, the deployment fences and the shared launch service (deployment disabled)

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w119` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w119` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W119.md` (new; the plan at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W119/` |
| Contract | [records/W27A-DESIGN.md](records/W27A-DESIGN.md), frozen (rounds 1 to 3): section 3 ("The registry proof"), section 7 ("The orbit checkpoint and the production launch service", including N8), the W27c2 row of section 9 with its F2 and F4 matrices and the registry transport matrix (with the N2 cases), and section 10's "ADR-050 implementation note, W27" and "ADR-049 implementation note, W27: replay before new-launch validation (N8)". Sections 4, 5 and 8 belong to W27c3 and W27d; read them only for context |
| Builds on | [records/W27C1.md](records/W27C1.md) (the receipt store, its startup index and the admission authority, merged `76c187ca`) and [records/W27B.md](records/W27B.md) (the bay block) |
| Risk | R-039 in `docs/11-RISK-REGISTER.md` (the bay receipt store and its availability) and R-038 (the bay block) |
| Ledger | `block_variant:loader/1` stays PLANNED until W27d and W27e |

This is a persistence slice (AGENTS.md section 13): it changes how the satellite registry records and acknowledges its
writes. Commit a one-page plan at the top of the record before any Java change; Root arranges an independent read-only
review before merge, and records section 10's two ADR implementation notes at the merge.

## What to do (the W27c2 row; the design is frozen, so a disagreement is a finding to report, not to change)

1. **The registry proof** (section 3): the session `mutationSequence`, the registry `checkpoint` and the section 3 epoch
   rule for all four registry write entries, composed into `SatelliteMissionSavedData` as W11b2 composed its journal
   (`AtomicSavedData.save(File)` is final, and CELESTIAL and ENDGAME share that base). The checked replace records an
   exact acknowledgement (session mutation sequence and carried epoch) and resolves a committer throw by a bounded
   readback in the same call, compared by `equals`. E advances once, to the carried epoch, only when a capture commits
   within its own call with no mutation since it was captured; a definite failure or an UNKNOWN outcome keeps E and the
   dirty and flush-pending state; a later capture carries an epoch above every candidate whose outcome is unknown.
   Frozen bytes are never rewritten, no delayed readback advances E, no snapshot epoch is introduced, and no mutation
   is refused while an outcome is unknown. Write policy, threading, the schema-4 literal epoch, tagged origins, ground
   surveys and limits stay unchanged.
2. **The DEPLOYMENT_PENDING fences**: launch and decommission consult the bay receipt index (W27c1) and fail closed with
   `DEPLOYMENT_PENDING` while a bay deployment of that satellite is unresolved, or before the receipt index is loaded.
3. **The shared `SatelliteLaunchService` with terminal parity** (section 7), including N8's ordering change: an existing
   satellite with the same identity (UUID, owner, definition and kind; never the current or retained initial mission)
   is checked before new-launch catalogs, targets, stats, research and quotas, for every kind. A data package whose
   definition or target has since been removed or disallowed now returns IDEMPOTENT and is extracted. Add the new
   result code's EN and zh text through the language entry points (append only).
4. **The bay checkpoint** forces the directory where supported.

Deployment stays disabled; no production entry deploys a satellite.

## Tests

The F2 and F4 matrices and the registry transport matrix including the N2 cases, as section 9 lists them; each refusal
and each UNKNOWN outcome with no rewritten bytes and no reused epoch; the fences before and after the index loads; N8's
two player-visible changes pinned. Driver S2 B1 to B3, B9 and B11 to B13 test receipt and registry ordering only, never
source recovery acceptance. No existing assertion is relaxed; where N8 changes an existing test's expected result (a
removed definition now IDEMPOTENT instead of DEFINITION_NOT_FOUND, TARGET_NOT_ALLOWED or CATALOG_UNAVAILABLE), change
that expectation in its own commit that names N8 and the design line, and list every such edit in the record.

No saved field beyond section 3's, schema, hash, bound, budget or timeout changes; new classes stay under 500 lines and
`RocketTransferService` (796 lines) must not grow. A new WARN or ERROR that a GameTest drives needs its own exact
manifest entry. Stop and report if any of these seems needed.

## Verification

Each new or changed test class alone, then the whole unit suite. Then one full GameTest with the unchanged log checker
on committed source, under the heavy-JVM lock (other workers hold it in turn; wait), releasing the lock and deleting
`build/gametest/world` in the `finally` of the same detached script that took it (WORKER-RULES). Check C: and D: above
10 GiB first. Memory is tight: one Gradle process at a time, and check that 6 GiB is free before each.

## Not in this task

W27c3 (the ROCKET_TRANSACTIONS checked policy), W27d (the deployment protocol), W27e (admission), ADRs, the risk
register, status files and the ledger (Root).
