# TASK W149: W98-R1, an ordinary capture invalidating a governed proof on the same entity chunk (design only)

Root: Claude. Date: 2026-10-23. Read [WORKER-RULES](WORKER-RULES.md), W98-R1 in the W98 review
(`D:/GitHub/ARCE-Task-Evidence/root-queue/W98-review/REVIEW.md`) and in [records/W98.md](records/W98.md), Root decision R35
(QUEUE), residual (b) of R-035 in [docs/11-RISK-REGISTER.md](../../11-RISK-REGISTER.md), and the governed proof path
(`EscrowChunkStore`, the barrier, `EscrowSaveMonitor`) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w149` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w149` (local commits only; Root merges) |
| Output | `docs/work/v1.8.0-root-queue/records/W149-DESIGN.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W149/` (probes or source excerpts, if any) |

R-035's residual (b) says an ordinary capture (W98's retirement census) invalidates a governed proof on the same entity
chunk, so the governed proof has to be redone, and the ordinary sub-quota does not limit this; it must be handled
before governed admission opens. W147 has since shown that proofs superseded by repeated saves stall and recover once
the saves stop, and that a finite load does not prove bounded liveness under continuous saves.

## What to write (no code)

1. **The behaviour on today's code**: how an ordinary capture supersedes a governed proof on a shared entity chunk, how
   often it can happen per tick and per rocket, what the governed side does (retry, backoff, STALLED), and whether a
   steady stream of ordinary captures can starve a governed operation; with source references and, if useful, a probe.
2. **A design** that bounds it: for example coordinating the two capture kinds on a shared slot (one proof serving both,
   an ordering or a short exclusion), or a fairness rule that guarantees the governed proof completes within a stated
   number of attempts, without weakening either proof and without blocking the server thread.
3. **Constraints**: no saved format, schema, bound, lease, budget or timeout change unless unavoidable (then state it);
   bounded per-tick work; W98's census, backoff and the W123 rules kept; how it interacts with W145's queue.
4. **Tests**: unit and GameTest cases, and a native load case showing the governed proof completes within the stated
   bound under continuous ordinary captures.
5. **Proposed implementation slices.**

An independent read-only review by a fresh Codex session follows; Root then decides.

## Verification

Read-only source inspection and probes kept in the evidence folder; no production change; the repository validator.

## Not in this task

Code, N3-B (W148), ADR and risk-register edits, status files and the ledger (Root).
