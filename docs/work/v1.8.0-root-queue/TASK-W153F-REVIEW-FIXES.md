# TASK W153F: fix the W153 review findings (W153-R1 to W153-R6, Root decision R99)

Root: Claude. Date: 2026-10-24. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W153](TASK-W153-C17A-04-LINKER-MONITOR.md),
W153's record (`records/W153.md` on the branch) and the review
(`D:/GitHub/ARCE-Task-Evidence/root-queue/W153-review/REVIEW.md`, SHA-256 `59e893c1…5b45`, verdict BLOCK) first,
including its component probes in that folder. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w153` (W153's; it has finished) |
| Branch | `codex/v1.8.0-w153` at `caef08fd` (local commits only; Root merges) |
| Record | append a "W153F" section to `docs/work/v1.8.0-root-queue/records/W153.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W153F/` |

## Root decision R99 (2026-10-24), on W153-R1

Use a durable acceptance cutoff, not a new cancellation record. The monitor's authority predicate (owner, station
access, route, catalog, binding) decides only before the governed preparation's PREPARED record is submitted. Once that
record is submitted, the launch is accepted exactly as an ordinary governed flight is, and F2 and F3 do not re-run the
monitor predicate. The monitor reports the governed flight's real outcome, never "refused" for a launch that proceeds.
A change after the cutoff invalidates the monitor's arming (ADR-065 4.3), not the accepted launch. If the code shows the
ordinary governed path itself re-checks station access after PREPARED, stop and report it instead of copying it. No
persistence format change.

## What to fix (each finding in its own commit, with a test that fails before it)

1. **W153-R1** under R99. Regressions: access revoked before submission (refused, no PREPARED, no debit); revoked after
   submission and before acknowledgement (launches, reported as launched, arming invalidated); and the same across a
   restart. No launch or debit may follow a refusal the monitor reported.
2. **W153-R2:** a monitor request's owner counts toward the per-owner and global warm-up caps but does not require the
   owner to be online. Expiry, source-presence checks and fresh-edge retries stay as they are. Add a cold destination
   with the owner offline.
3. **W153-R3:** the arming authority includes the effective fuel-catalog signature, checked where the planetary catalog
   is. Test a fuel-only change, a no-op reload, and an armed powered monitor across a reload.
4. **W153-R4:** the monitor root accepts its nested destination only with exact field types and canonical form. Anything
   else goes to the existing raw inert preservation. Use a local strict check, not a change to the shared
   `TravelTargetNbtCodec`. Test fractional, long and byte schema values, unknown fields, and block-entity save and
   reload.
5. **W153-R5:** a refused quote shows each known required bank cost from a bounded required-cost vector. An unknown
   cost shows as unavailable, never as zero. Add insufficient-primary and insufficient-oxidizer view assertions.
6. **W153-R6:** add a correction file beside W153's `SHA256SUMS.txt` that explains the live `events.jsonl` entry.
   Exclude live transcripts from new manifests, and do not overwrite the sealed files.

## Verification

Each fix's tests alone, then the whole unit suite, runData twice with a clean tree, and one complete GameTest with the
unchanged log checker, under the heavy-JVM lock with the atomic pattern and the 90-second hand-off rule. Check C: and D:
above 10 GiB and 6 GiB free memory first. The W153 reviewer re-reviews afterwards.

## Not in this task

New monitor features, cross-dimension rebinding, the pad alias design, enabling admission, ADRs, the risk register,
status files and the ledger (Root).
