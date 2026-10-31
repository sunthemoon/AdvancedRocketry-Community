# TASK W230-fix: revise the bay safe-exit design for its review findings

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. Design only: no
production or test code.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w230` (W230's; continue on its branch) |
| Branch | `codex/v1.8.0-w230`, now at `8ff38121c` (pushed). Local commits only |
| Design | `docs/work/v1.8.0-root-queue/records/W230-DESIGN.md`: revise it in place and add a dated "Revision 1" section that lists each change |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W230-review/REVIEW.md` (SHA-256 `bd2246a7…89127b1`): APPROVE-WITH-CHANGES; W230-R1 to W230-R4 Medium, W230-R5 Low |

**Root decision R231.** Resolve all five findings in the design before Q1 goes to the owner.

1. **W230-R1: ordinary fall only.** `LivingFallEvent` carries no damage source, and a pointed-dripstone landing goes
   through the same path. Define exactly which damage the safeguard may suppress and how the server observes it,
   using a source-aware mechanism tied to a genuine landing. Alternatively, disclose a broader scope to Q1 and say
   why. Name the adapter tests: an ordinary fall, a dripstone landing, and a non-FALL source passed through
   `causeFallDamage`.
2. **W230-R2: one uninterrupted descent.** Add a descent identity and continuity rule. The grant ends irrevocably on:
   - a newer same-Level teleport;
   - a trusted passenger transfer, including one that fails before remounting;
   - an observed discontinuity in flight or vertical position.

   Uncertain observations fail closed, without loading blocks or extending the deadline. Name the tests: a teleport
   inside the radius, an upward reposition still below exit Y + 1, and a transfer that fails before mounting.
3. **W230-R3: the anti-refresh history.**
   - Separate the one live grant per player instance from RAM-only (player UUID, T) used and deadline records.
   - Define their retention through every cleanup event, the handling of several Ts for one UUID, the exact
     end-of-T cleanup and the bounded capacity, with refusal rather than eviction on overflow.
   - Name the tests: T-A then T-B then T-A, logout and login as a new instance, and death or clone and re-entry.
4. **W230-R4: competing exits.** State the server ordering, what "deterministic" means here, and how ties and delayed
   completions are sequenced. Disclaim scheduling-independent fairness if that is the intent. Name the tests: the
   losing exit's exact next candidate or fallback and the budget counts, for one available box, the last budget unit,
   reversed request order and contention from a delayed completion.
5. **W230-R5: the decision brief.**
   - Q1 lists the normal-play residuals: deadline expiry, legitimate horizontal or upward movement, no fresh
     safeguard on a later exit in the same T, the unchanged life-support rule, and the consequences of 1 to 3.
   - Q2 is framed as approval of the adapter and message semantics, with the copy checked during implementation, or
     it gives the actual English and Chinese templates.

Keep every constraint D1b sets: dismount is never cancelled; playerdata authority does not change; only loaded chunks
are used; work and memory are bounded per tick.

## Verification

The repository validator. Root reads the revision; the same reviewer's findings are checked again before Q1 goes to
the owner.

## Not in this task

Code, W231's per-owner cap, bay admission, status files and the ledger.
