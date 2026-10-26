# TASK W155: the journal lease keeps the named transfer's history (W153-R7, Root decision R103)

Root: Claude. Date: 2026-10-24. Read [WORKER-RULES](WORKER-RULES.md) and the W153 re-review
(`D:/GitHub/ARCE-Task-Evidence/root-queue/W153-review/REREVIEW.md`, SHA-256 `7ef3540d…9c2b`, finding W153-R7), with its
probe `rereview-evidence/LeaseHistoryProbe.java` and log in that folder, first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w155` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w155` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W155.md` (new; a short plan at its top before any code) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W155/` |

## The finding

`RocketJournalLeases` remembers the mutation sequence of the logical rocket L, but for the transfer T it only checks
whether T is present: in RAM, in the acknowledged generation, or among unacknowledged removals. If another logical
rocket publishes T and its removal is acknowledged while a lease holder waits, the lease accepts again, although T
changed in between. T is the flight plan's request ID, which a client supplies for a player launch, so a second rocket
can name the same T. F1's preparation lease (`EscrowFlightJournal.PreparationLease`, W153H) and the other
`logicalLease(L, T, …)` callers rely on the stronger property.

## Root decision R103 (2026-10-24)

The gap predates W153H: F1's lease had the same presence-only T check before it. W153 merged without it (`8dee8680`)
and this fixes it separately:

1. The lease binds T's mutation history at acquisition, as it does L's, from bounded, immutable published views. Any
   publication or removal of T after acquisition refuses the lease, whichever rocket names T. Unrelated rockets with
   unrelated transfers stay independent.
2. The history is bounded and kept in RAM only. No saved format, schema, journal bound, budget or timeout change. If
   the bound of remembered T changes is reached, refuse conservatively; never accept.
3. Check every `logicalLease` caller (F1's preparation lease, the governed cleanup and launch checks, the pre-F2
   cancellation, the members leases of reconciliation and settlement) keeps its present behaviour for an unchanged T.

## Verification

A regression with the reviewer's unsampled sequence: T published by another rocket, then removed and acknowledged,
with no lease read between. Keep the existing unacknowledged-removal and peer-serial tests. Each changed class alone,
the `rocket.flight.persistence` and `resourceescrow` packages, the whole unit suite, and one complete GameTest with the
unchanged log checker, under the heavy-JVM lock with the atomic pattern and the 90-second hand-off rule. Check C: and
D: above 10 GiB and 6 GiB free memory first. It touches journal lease semantics, so a fresh Codex session reviews it
before merge.

## Not in this task

Request-ID policy for clients, other escrow changes, ADRs, the risk register, status files and the ledger (Root).
