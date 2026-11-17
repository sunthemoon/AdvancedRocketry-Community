# TASK W367F: revise the X03 repair design after its review

Root: Claude. Date: 2026-11-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w367`, branch `codex/v1.8.0-w367`, brought to the shared head by Root |
| Decision | Root decision R473 (QUEUE row W367F) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W367-review/REVIEW.md` (W367R, a Claude session; APPROVE-WITH-CHANGES; SHA-256 `83ee8c9cb1762f77d60273fbf50688e3c4fec194d6f6f90ade2603217a78e5a4`) |
| Write scope | `docs/work/v1.8.0-root-queue/records/W367-DESIGN.md` and `records/W367.md` only |

## What to do

Revise the design so that it resolves every finding of the review. Then append a revision section to `records/W367.md`,
with a table that gives each finding and the section that resolves it.

1. **High 1, the negative control.** Choose the tamper target by a rule: an unlisted file that is still byte-identical
   after the second stop. The dry run must prove that one exists, and the case refuses to run if none does. Drop the
   POI-specific wording.
2. **High 2, the verification starts.** Specify the new gated Java code the repaired copy's starts need: the probe
   attachment, a scenario that works once the transfer record is gone, and the ordinary unload admitted on the server
   thread. List its files, its gating, and the probe inventory counts it changes (`EscrowProbeRoutingTest`, the Python
   inventory). Make it its own slice.
3. **The Medium findings.**
   - Finding 3: a negative control for the semantic comparison of the rocket and the unloader.
   - Finding 4: say that the preserved identity makes production elect KEEP_SOURCE and that the fence fires at
     `EscrowRecoveryFence`. The tool must refuse a FF_KEEP_SOURCE world explicitly.
   - Finding 5: a stated rule, stricter than production, for the Python tool, with the candidate's own
     `ARCE_TRANSFER_FENCED` line as bound evidence.
   - Finding 6: one consistent treatment of the historical witness.
   - Finding 7: operator steps separated from harness-only steps, and the real-world case where nothing loads the
     rocket's chunk.
   - Finding 8: a reason per entry in the runtime file list, and at least two dry runs.
   - Finding 9: refuse unless every root is already current, so a root-2 ledger is never migrated by the copy's first
     start.
4. **The Low findings.**
   - The new family asserts FLOOR_CONFLICT itself.
   - Specify the lock mechanism, the reproducible gzip output and the empty-list encoding.
   - Revise the estimate (X03-B about 2 to 2.5 days, total about 4.5 to 6).
   - Give the operator text its trigger.
5. **Root's answers of R463 stay**, with any wording the review's findings change.

## Rules

- Docs only. Commit on the branch with a message in the style of AGENTS.md section 6. Do not push.
- English, full sentences, UTF-8 without BOM, LF line endings. Links only to tracked files.
- The last line of your answer is the path of `records/W367.md`.
