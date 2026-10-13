# TASK W22F: W22 review fixes F2, F3 and F7

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w22-station-notices` (W22 plus the shared branch, `652e2c65`) |
| Branch | `codex/v1.8.0-w22-station-notices` (local only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W22-review/REVIEW.md`, SHA-256 `1cbb11093ac46480458f0230dd741e31cac9df1331371f0d135bc243705b103d`. Verdict MERGE-WITH-FOLLOWUP |
| Record | `docs/work/v1.8.0-root-queue/records/W22F.md` |

Read the whole review. Fix each finding in its own commit, together with its tests.

## Root's decisions

1. **F2: visitors get the brief form.**
   - A visitor inside the region who lacks VISIT access receives the station name, the code
     and the target only: no warp cost and no energy balance.
   - Members, the owner and operators keep the full form.
   - Test both forms with code-token assertions.
2. **F3: the actor's current authority is checked at each send.** The acting player receives a
   notice only while they still hold authority over the station, the same check every other
   recipient gets.
   - Update the "De-op" assertion in `StationWarpAbortGameTests` (around line 143), so that the
     de-opped former actor no longer receives notices.
   - The abort still happens, and the audit still records it.
   - Explain this assertion change in your record. It enforces a stricter rule; it does not
     relax a test.
3. **F7: hardening.** An exception from notice delivery in `commit` must not skip the audit line,
   and must not escape after the checked write.
   - Catch it, log one bounded WARN with an exact expectation in
     `scripts/gametest_expected_log.json`, and keep the warp's outcome unchanged.
   - Test it with an injected failure.
4. **F4, F5, F6 and F8 to F10:** no code. Root records them in the ADR note and risk entry at
   merge.

## Commands

- The whole unit suite once.
- `runGameTestServer` under the lock, then the log checker. Report the totals.

## Not in this task

- Other notice behaviour.
- ADR and risk-register edits (Root).
- Status files and the ledger.
