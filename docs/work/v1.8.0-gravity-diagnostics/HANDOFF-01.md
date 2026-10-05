# Gravity switch diagnostic source handoff

Status: READY_FOR_REVIEW. Source is uncommitted and has not been compiled or run.
Root alone integrates, publishes and schedules verification.

## Exact scope

Fixed isolated base: `37ef84c26d33d5f369478bd5060dc97b9dc7fbdd`.
Only `GravityFieldGameTests.java`, this handoff and `TASK.md` are changed/new.
There are no helper scripts inside the worktree.

The Java postimage is 23,375 bytes, 378 lines, SHA256
`f1ad52c11c8c0d3cc01c303c4c3c2ddfc83af7810626f56cb3a7cc9263e6725d`.
Its fixed original is 20,488 bytes, SHA256
`5becfd86095123391c233679b7774e5f549a6a7b36021d95bb001994ef3e58ae`.
The source diff has 59 insertions and 2 deletions; the two original cleanup
statements remain in the same order within the new cleanup wrapper.

## Observations and boundaries

Five literal stages log once each on the successful planet fixture route:
`disabled_set`, `before_disable_assertions`, `enabled_set`,
`after_batch_before_restore`, and `after_batch_after_restore`. A per-fixture
counter rejects any observation after six lines; there is no per-tick logging.

The line records the test/world ticks, COMMON spec loaded flag, raw Forge
BooleanValue getter, effective CommonConfig settings, installed operational
runtime settings (or `UNAVAILABLE`), cached device active/status, actual index
membership, removed flag, and exact fixture position's loaded/entity-loaded/
entity-ticking predicates. `raw` is an in-memory config getter, not TOML bytes.
These reads neither acquire chunks nor change config, tickets, device or index.
The readings are sequential server-thread observations, not a new atomic snapshot.

One test-only context retains the helper/device and is cleared in AfterBatch
finally, including when an original assertion or a diagnostic read throws. Both
original restoration mutations remain in finally. Process termination before
AfterBatch is outside that cleanup guarantee. No exceptions are ignored, and a
diagnostic error can therefore fail the fixture rather than manufacture a pass.

Every original expression, string and method is retained, apart from wrapping
the original two cleanup statements and inserting the read-only observations.
All 32 assertTrue calls, three GameTest annotations, config mutation order,
two-tick delay and 1200-tick planet timeout remain unchanged. Station/trust tests
and all production files are untouched. No API, chunk force-load or oracle mask
was added.

## Actual verification

Own thin evidence directory:
[gravity-switch-diagnostics-author-20261006-9c4061](D:/GitHub/ARCE-Task-Evidence/v1.8.0/gravity-switch-diagnostics-author-20261006-9c4061).
All new Python subprocess temporary environment points to its `tmp` directory.

- `python -B check_static02.py`: exit 0, 20 lexical controls, no input drift.
  The explicit diagnostic inverse restores the entire fixed original source.
- `git diff --check`: exit 0. Source numstat is 59/2. HEAD and branch remain fixed.
- First attempted `py -3.13` command: shell exit 1 because `py` is unavailable.
- First actual Python check: exit 1 from an author-only placeholder-count error
  (expected 17; actual format has 16). The corrected helper changes that check
  alone, retains the failed helper/result/log, and does not change Java.
- A preliminary adjacent-source read used three wrong package locators. Its
  composite shell exit 0 did not mean those reads passed; correct literals were
  subsequently discovered from imports and read successfully.

These are static controls, not Jupiter, compilation, GameTest or native evidence.
No Java/Gradle/native command ran. The original hosted failures remain unchanged;
this candidate neither proves a cause nor fixes gravity behavior or rocket flight.
The separately reported ticket-comparator compilation failure is outside scope.

## Remaining delivery / rollback

Different-agent review, compilation and hosted GameTest observation are required.
Publish a reviewed-unverified diagnostic checkpoint if needed for hosted execution;
do not label that checkpoint verified delivery. A later observation must distinguish
raw/effective config, native readiness and cleanup timing without relaxing the
existing assertion or delay. No current-version Required Gate is satisfied here.
Rollback scope is exactly the one GameTest delta and these two task records.
