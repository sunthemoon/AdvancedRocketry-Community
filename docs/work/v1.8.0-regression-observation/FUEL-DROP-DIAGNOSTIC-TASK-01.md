# Fuel-loader native drop failure: bounded count diagnostic

Date: 2026-10-07. Status: implemented-unverified. Integrator/author: Root.
Source base: `22f7d1cae0735a8b8c072165e7a3cf8089e3719c`.

The [new terminal stream](../v1.8.0-ci/RESULT-33.md) fails the existing assertion
that exactly one nearby ItemEntity is visible after native destruction. Its
message does not distinguish zero from multiple queried entities. Expose only
that already-returned list's size in the failure message. No new native/world
lookup, entity filtering or inventory/tag access is needed.

Write scope, isolated Root-owned worktree
`D:/GitHub/arce-v180-fuel-drop-diagnostic-20261007`:

- `FuelLoaderPlacementGameTests.java`: append the observed size to the existing
  assertion message, keeping predicate, query, sequencing and all other bytes.
- This directory's new `FUEL-DROP-DIAGNOSTIC-SOURCE-01.md`: source-only handoff.

Root owns task/plan/log publication, Git commits and normal push. Other agents
may independently review the exact diff but do not edit it. No AGENTS, production,
resource, registration, contract, save protection, budget, deadline or ledger
change. This does not establish a duplicate Item, unique fixture cause or repair.
An actual source-bound CI replay is required after independent source review;
old failed results remain immutable. Applicable static validation is recorded
separately from Java/native execution. Local C is below 10 GB; no local JVM or
native run. Scratch/evidence stays under D's project parent.

Root freezes only the two assigned files. Source/record SHA-256 values are
`da47adb93e07b798f33d4a30f368c61cd9f3e1c4fd169e538c7ffaee4e15cfb7` and
`059676a591ab3ac566579f5e98a365c6b787494919ab9e46d82858b057e1e5ba`.
Actual `python -B check01.py` (`6affa3`, exit 0) verifies nine static controls,
including whole-file exact single-message replacement, original predicate/query,
six identical annotations/deadlines, empty index and owned scope. The
[static receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/fuel-drop-diagnostic-author-20261007-01/CHECKS-01.json)
has SHA-256 `2e045f155277164f96edfdaf10712be8d096e259714a41886a6259b462b8bd1e`.
Independent exact-diff review is complete. The [publication record](FUEL-DROP-DIAGNOSTIC-INTEGRATION-01.md)
binds the unchanged two-file source to `10eb561a` and its new running hosted CI;
no new native result is yet claimed.
