# TASK W129bc: the satellite registry writer and the barrier callers (ADR-074 slices 2 and 3)

Root: Claude. Date: 2026-10-26. Read [WORKER-RULES](WORKER-RULES.md) first. Then read:

- [ADR-074](../../decisions/ADR-074-OFF-THREAD-REGISTRY-AND-TRANSACTION-WRITERS.md), accepted on 2026-10-26 under
  four conditions;
- [records/W129-DESIGN.md](records/W129-DESIGN.md) revision 2, all of it, especially sections 2 to 7;
- the W129R review's conditions (`D:/GitHub/ARCE-Task-Evidence/root-queue/W129R-review/REVIEW.md`);
- W129a's record and code on its branch (`records/W129a.md`, `CheckedRootWriter`, `RocketTransactionWriter`).

You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w129bc` |
| Branch | `codex/v1.8.0-w129bc` from the commit named in your prompt: W129aM's merge of the shared head into W129a (local commits only; Root merges after W129a) |
| Record | `docs/work/v1.8.0-root-queue/records/W129bc.md` (new; a one-page plan at its top before any code) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W129bc/` |

## What to do

The design's sections 7.2 and 7.3 in one branch. W129b's temporary owner-thread await must never reach production
without W129c, so both merge together.

1. **Plan first.** Map the design's sections 2 to 5 to classes. `SatelliteMissionSavedData` stays under 500 lines:
   the writer state goes in `SatelliteRegistryWriter` and a new capture and encoder class. List every site of section
   4.3 and its stamp or check.
2. **B1 before code that depends on it.** Run section 6.1's B1 under W128's stop rule. If any figure misses docs/17,
   stop and report.
3. **W129b, the registry writer.**
   - The capture and encoder, byte-identical to `save(CompoundTag)` on the fixtures, including 16 MiB.
   - The snapshot epoch with rules 1 to 7.
   - The site split of section 4.3, with finite-long admission and MAX - 1 and MAX refusal before any mutation.
   - `TerminalLocation` freezing and its alias test.
   - `requestCheckpoint` with removal tokens.
   - The unchanged W119 no-mutation matrices, plus the one named proof-test transition (ADR-074 item 5) in its own
     cited commit.
4. **W129c, the barrier callers.**
   - The terminal pending table and protection.
   - Discovery never ahead of its paid receipt.
   - Data actions with stable tokens.
   - Rebind and the release-test hooks, including research's same-target seam.
   - REGISTRY_PENDING and REGISTRY_BUSY appended with EN and zh text; runData.
   - Operator backup documentation (section 4.5) in the record, for Root to place.
5. **B3 before merge** (section 6.1): all three large writers at their bounds, 50 rounds, resident roots. Measure Q8's
   fair IO-side permit beside the unpermitted overlap (R60 and R63). If B3 misses, stop and report with both
   measurements.

**Constraints:**

- No change to schemas, saved formats, numeric bounds, budgets, deadlines or quotas.
- No checker change. Exact manifest entries for deliberate WARN and ERROR lines.
- Each step goes in its own commit with a test that fails before it, where the step adds behaviour.

## Verification

- Section 6.2's unit tests, each class alone once, then the whole unit suite.
- Section 6.3's GameTests and one complete GameTest with the unchanged log checker; runData with a clean tree.
- Section 6.4's native cuts R1 to R7 (including both R7 variants), R3, R4 and discovery, two restarts each. Stop and
  report before more than 60 counted starts; the rest follow in a continuation.
- B1 and B3 reports with hardware, JVM flags and fixture composition.

Run heavy work under the heavy-JVM lock with the atomic pattern and the 90-second hand-off rule; other workers share
it. Before starting, check that C: and D: each have more than 10 GiB free and that 6 GiB of memory is free. Keep the
evidence under 100,000,000 bytes and delete only the server and export copies you create. This is a persistence
slice: a fresh read-only review follows before merge.

## Not in this task

- W27d1 and W27d2, the bay deployment coordinator.
- The Q6 ground-preflight leaf.
- ADRs and the risk register: Root registers the new registry-writer row (R-041) and the R-039 change before merge.
- `build.gradle`, workflows, status files and the ledger.

## Continuation under Root decisions R120 and R122 (2026-10-27)

W129bc delivered `bb8e87b8d`. The fresh Codex review
(`D:/GitHub/ARCE-Task-Evidence/root-queue/W129bc-review/REVIEW.md`, SHA-256 `077920e0…405d`) returned **BLOCK** on two
Medium findings. It found no duplicated satellite and no celestial write ahead of its paid receipt, and it verified
the writer, the capture, the lifecycle, B1, B3 and the eleven native cuts. W129aM has since merged at `bb0eda86f`
(review W129aM-R1 assigned here under R120). A fresh session continues on the same branch:

1. **Merge the shared head first** (a merge, not a rebase): `git merge codex/v1.8.0-classic-content`. The branch then
   compiles on its own, without W129aM's unmerged commits. Resolve conflicts in your own commit and list them in the
   record.
2. **W129bc-R1.** A first discovery-paying claim pays the account and moves the mission to `CLAIM_PENDING_DISCOVERY`,
   but `SatelliteManager.claimMission` replaces that mutated result with `applyDiscovery`'s `PENDING_DISCOVERY` and
   `changed=false`, so the terminal records no DATA entry or token.
   - Keep the original claim's mutation and token whatever the discovery recovery returns.
   - Record the terminal DATA action whenever that claim changed RAM.
   - Keep the paid-receipt gate and the missing-mission no-request rule.
   - Regressions: the reviewer's probe (`review-tests/.../ReviewDiscoveryClaimBarrierTest.java` in the review
     folder), and an end-to-end terminal test of a first discovery-paying claim with the writer paused, then failed.
     Check the stable pending entry, the conflicting-button refusal and delivery after coverage.
3. **W129bc-R2.** `SatelliteTerminalBlockEntity`'s no-package, non-DATA start branch calls `resources.start` before
   `TerminalRegistryActions.admit`. A second terminal can then start a survey while the first terminal's
   cancellation of that satellite is still pending.
   - Apply the by-satellite pending admission before resource starts.
   - Audit every other state-changing branch that bypasses `dataAction` (resource claim and cancel, unlink) and list
     each in the record with its admission.
   - Keep the separate resource-delivery behaviour; do not add a durability barrier to every resource operation.
   - Regression: a two-terminal survey cancel, chip move and start with the cancellation write held, which returns
     `REGISTRY_BUSY` until the cancellation is acknowledged.
4. **W129aM-R1 (R120).** The transaction writer has the post-await clock defect that `79f2653a` fixed for the
   registry. After `awaitWriter`, the bound journal again supplies the unadjusted server tick, so a failure published
   late in an await waits longer than 200 later server ticks.
   - Compensate the bound transaction clock for time spent awaiting, including exceptional exits, as the registry
     adapter does. Production cadence and shutdown's own clock stay unchanged; the release-only gate stays.
   - Regression: a bound-journal test of success and cadence, then failure and backoff, each followed by real
     server-tick advancement. The reviewer's `AwaitClockRepro.java`
     (`D:/GitHub/ARCE-Task-Evidence/root-queue/W129aM-review/`) shows the sequence.
   - Do not enlarge any timeout or shorten the backoff.
5. **No change** to schemas, saved formats, bounds, budgets, deadlines, quotas or the log checker.

### Verification

- The new tests alone, failing first on the merged branch before each fix.
- The 19 new or changed unit classes, the W119 matrices, `SatelliteRegistryEpochTest`, `SatelliteRegistryProofTest`,
  the W129a classes, then the satellite, mission and rocket persistence packages and the whole unit suite.
- runData with a clean tree and one complete GameTest with the unchanged log checker, on the merged tree.
- Native: the R3, R4 and DISCOVERY cuts again with two restarts each (9 starts), since R1 and R2 change their paths.
  B1 and B3 are not rerun: the writers do not change.

The heavy-JVM lock rules above apply; W164, W152K and W156 share the lock. A fresh Claude session re-reviews after
delivery. Root registers R-041 and the R-039 change before the merge.
