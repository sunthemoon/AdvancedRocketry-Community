# TASK W01F: fix the W01 independent review findings

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w01-hatch-kernel` (now at the W01 candidate `7fb1c1a3`) |
| Branch | `codex/v1.8.0-hatch-kernel` (local only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W01-review/REVIEW.md`, SHA-256 `5954064319de8587de9f7b50e89a08d5c6ec2387f9e2a994bce932384ebd388a`. Verdict BLOCK, with findings W01-R1 to W01-R4. |
| Record | `docs/work/v1.8.0-root-queue/records/W01F.md` |

## Why

W01 built the hatch family, the generic classic controller and the lathe. The
independent review found two High and two Medium problems on paths where saves are
interrupted or roots are malformed. Read the whole review first: it gives concrete
scenarios and line references. Task W05 (arc furnace and cutting machine) is being
built on this same branch in parallel. Keep the runtime's public shape stable, and
mention any change W05 must follow.

## Root's decisions for each finding

Fix each finding in its own commit, together with its tests.

1. **W01-R1** (whole-chunk save refusal for classic roots).
   - **Narrow the triggers.** Hatches never own Item or Fluid resources.
     - A hatch whose required root is missing loads as an unbound, empty hatch.
     - A controller whose required root is missing loads as a fresh, unformed controller.

     Neither case refuses any chunk save. Nothing remains to preserve in either case,
     so refusing would only roll back unrelated blocks.
   - **Keep the refusal only** for a present root that cannot be written back
     unchanged: oversized, unbounded or not representable. Normal play cannot produce
     these (the review confirms this). Bounded unsupported or future schemas stay
     quarantined and are written back verbatim, without refusal, as they are today.
   - **Document the remaining scope** as AGENTS.md section 3.3 requires:
     - extend R-021 in `docs/11-RISK-REGISTER.md` with the classic controller and hatch
       objects and their exact remaining triggers;
     - state the whole-chunk scope, the saturation to the whole Level, the Level-lifetime
       duration, the unbounded retry and log volume, and the offline backup and repair
       procedure;
     - add a short, clearly labelled implementation note to ADR-064 §11.

     Do not change any other R-021 content.
2. **W01-R2** (an orphaned claim after controller removal).
   - When a hatch or plug finds its claimed controller position loaded, and no
     controller with the claimed machine UUID is there, the claim is stale. Retire it
     and the part becomes unbound. Never load a chunk to decide this.
   - An unloaded controller position keeps today's refusal.
   - A plug keeps its own FE when its claim retires.
   - Test the saved-controller-absent plus old valid hatch claim combination, including
     a plug with stored FE.
3. **W01-R3** (generation skew after restart).
   - When the controller identity matches (same machine UUID and position), accept an
     older generation from the same owner and rebind it to the controller's current
     generation, under loaded-identity checks.
   - A foreign owner is still refused.
   - Old facades must stay unusable after the rebind.
   - Test both saving orders.
4. **W01-R4** (drop position). An Item hatch's bank drops at the controller position,
   as ADR-064 §1.2 says. The GameTest asserts the drop location separately from the
   exactly-once count.

## Tests and commands

- New JUnit and GameTests as listed above.
- `test --tests '*machine.classic.*'`.
- At the end: `runData` and `runGameTestServer` (under the lock), then the log checker.
  Report the GameTest totals. Never weaken an existing assertion.
- If the startup failure from the W01 record recurs (`AtmosphereBoundaryRegistry` rejects
  the adapter test provider), retry once and record it. It is a known parallel bug.

## Not in this task

- Other machines.
- ADR-068.
- Any change to `GuardedChunkSaves` itself.
- Status files and the ledger.
