# TASK W44R: revise the world-first design after the REDESIGN review (design only)

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w44-world-first-design` (at `25f5ff4a` plus this task) |
| Branch | `codex/v1.8.0-w44-world-first-design` (local only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W44-review/REVIEW.md`, SHA-256 `bf8a200ede03566c674322ee2fa5d12c5d2330916beab6aec495fa771e8c379c`. Verdict REDESIGN: F1 High, F2 to F6 Medium |
| Output | Revise `docs/work/v1.8.0-root-queue/records/W44-DESIGN.md` in place. Add a "Round 2 changes" section at the top mapping each finding to the changed sections. No production or test code |

Read the whole review, the design note, [TASK-W44](TASK-W44-WORLD-FIRST-DESIGN.md) and ADR-066
section 6.2. A fresh reviewer will check round 2, so state every change precisely.

## Required changes (from the review)

1. **F1, High: a retained outbox with zero transfers.**
   - Freeze an explicit initialization rule for a loaded, occurred outbox (ELECTED or DELIVERED)
     that does not depend on the transfer count.
   - For example, request one baseline confirmation whenever a loaded outbox has occurred, or keep a
     validated loaded-source authority separate from writer confirmations.
   - Never seed proof from unconfirmed in-session RAM.
   - Add cuts for both ELECTED and DELIVERED roots with zero records, absent or stale player
     advancements, no new flights, and two restarts. Fix FM10's oracle.
2. **F2: an explicit unconfirmed-source state.**
   - Add a source-unconfirmed (unavailable) state with bounded retries. In it: no award, no delivery
     acknowledgement, and no inference of corruption.
   - Enter SOURCE_ROLLBACK only after comparing a validated, known-EMPTY source with an occurred
     receipt.
   - Every progression-unavailable hold fails closed for election. This replaces R3's fail-open
     choice.
   - Define the startup and election order.
   - Add cuts for: no acknowledgement at startup, an unrelated UNKNOWN after DELIVERED, and
     progression unavailable.
   - Ordinary confirmation recovery must never need offline repair.
3. **F3: deferred login reconciliation.**
   - Add a bounded, deferred reconciliation for a login that was skipped for a temporary reason:
     baseline pending, dead or spectator.
   - It keeps the receipt's immutable participants and is idempotent. Do not scan every historical
     participant each tick.
   - Cases: login before the delayed baseline, respawn after death, spectator to survival, and a
     disconnect during the deferral.
   - The oracle checks the actual advancement, not only the receipt.
4. **F4: a root-only checked station operation.**
   - Specify a root-only checked operation that shares the station writer, replacement and outcome
     resolver.
   - It validates exactly-unchanged stations, reservations and balances, the expected ELECTED event,
     the DELIVERED substitution, and the clock sample.
   - Keep the named-transition gates for every other operation.
   - Show that deleting the winning station does not block this acknowledgement.
   - Plan the extraction of shared code so no class exceeds 800 lines and no second writer appears.
5. **F5: an executable cut inventory.**
   - Publish the cut and variant inventory. Each case names the cut thread, the barrier or gate, the
     fixture, the award oracle and the two-restart expectation.
   - Remove the unreachable expectations the review lists.
   - Keep the headless safety restriction and the real-client distinction.
6. **F6: recovery text.**
   - Separate two things: repairing a receipt to match its existing validated source, and restoring
     old authority.
   - Restoring old authority, and any downgrade, needs a consistent complete world snapshot. Do not
     present the migration backup as enough for a downgrade of an evolved world.
   - Remove the receipt-only revocation instruction, or state that supported revocation is not
     designed here.
   - Update section 7's proposed risk and ADR text to match.

Also answer the review's comments on R1 to R6 and the section 9 deviations. Where you keep a choice
the reviewer questioned, give the reason.

## Not in this task

- Any production or test code.
- ADRs, the risk register, status files and the ledger (Root).
