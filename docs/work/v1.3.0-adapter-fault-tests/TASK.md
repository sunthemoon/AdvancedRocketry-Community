# V130-ROCKET-01 — Adapter failure GameTests

- Branch: `codex/v1.3.0-adapter-fault-tests`.
- Base: `c15bd7e140bac66bffe49091a83c3296de49713e`.
- Status: READY_FOR_REVIEW (baseline failures retained; production integration pending).
- Write scope: the new `RocketAdapterFailureGameTests` class, one package-private
  fixture helper, and this task directory only.
- Runtime implementation owner: root integrator, outside this worktree.

## Contract and scope

Exercise existing internal adapter dispatch and the actual ServerLevel transaction
world with bounded container faults. Failed callbacks must not leave untracked
partial placements, claim a completed rollback while cleanup is incomplete, or
lose the retained snapshot/journal authority. Unavailable adapters must preserve
that authority and must not indefinitely prevent unrelated recoverable work.

Use existing method signatures, NBT IDs and the `rocket_test` template. Tests own
their positions, transaction IDs and entities; cleanup runs in `finally`. No global
runtime manager replacement, new production API, full compatibility mod, release
Gate change, remote server, or long-duration campaign is included.

## Verification

One baseline `runGameTestServer --offline --no-daemon` run with Java 17 is permitted.
Retain the actual pre-fix failures and setup failures rather than changing the
contract to fit the baseline. Root will integrate with the production correction
and run the final applicable checks independently.

The single baseline run completed: 132 Required tests, 124 passed and 8 failed;
all failures are newly added adapter-fault cases. See [the report](REPORT.md).
