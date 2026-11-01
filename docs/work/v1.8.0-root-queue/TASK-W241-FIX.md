# TASK W241-fix: withhold a formed hull whose validated rotation differs from the controller's facing

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w241` (existing; branch `codex/v1.8.0-w241` at `fb3cbe720`, clean) |
| Finding | W241-R1 (Medium) in `D:/GitHub/ARCE-Task-Evidence/root-queue/W241-review/REVIEW.md`, SHA-256 `28af2a558c696e19ba960d08f15fef208ce236d3020204edcfe5e7338ff3690e`, with its probe `rotation-probe-actual-block.log` |
| Task it answers | [TASK-W241](TASK-W241-K4-IMPORT.md) and [TASK-W241-REVIEW](TASK-W241-REVIEW.md), Root decisions R253 and R255 |
| Record | Append a "W241-fix" section to `docs/work/v1.8.0-root-queue/records/W241.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W241-fix/` |

**Root decision R257**, under the owner's delegation: adopt the review's suggested change.

- A formed rolling machine or precision assembler publishes a cleared presentation while its validated
  `selectedTransform().rotation()` differs from the controller's live `FACING`, as the mirrored precision assembler
  already falls back to its JSON blocks.
- The hull comes back only when the two agree, through the existing transition-only publication to tracking clients.
- No new wire field, persisted field or schema. Do not change the retained server transform outside its existing
  binding lifecycle, and do not change formation authority.

## Steps

1. **Fail first.** Add a regression that rotates an already formed controller (normal rolling machine and normal
   precision assembler), revalidates, and checks the public flag and the rendered orientation. It must fail on
   `fb3cbe720` before the fix; keep that log.
2. **Fix** rolling and precision as R257 says.
3. **Sweep the other owners.** For every formed-model owner from K1 to K4 (the classic kernel controllers, the
   centrifuge, the solar array, the beacon, the research machines, and the four endgame controllers), say whether the
   rendered facing can differ from the validated one. Fix any that can in the same way, with a regression; for the
   rest, give the line that keeps them in step.
4. **Reachability.** Say which in-game paths can rotate a formed controller without breaking it (a wrench, a structure
   template, a command), so the record states how likely the mismatch is.

## Verification

- The new regressions failing first, then passing.
- The client model, machine, endgame and projection unit tests, and the whole unit suite.
- The K-model GameTest batches in a focused run under the heavy-JVM lock (the full run is Root's sharded branch run).
- The repository validator.

Commit on the branch; do not push. Root pushes for a sharded run, and the reviewer of W241 rechecks the fix.

## Not in this task

Any other finding, K5, the etcher pair, status files and the ledger.
