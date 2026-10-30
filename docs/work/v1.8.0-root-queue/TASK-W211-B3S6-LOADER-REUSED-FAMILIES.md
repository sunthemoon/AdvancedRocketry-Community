# TASK W211: B3-S6, real fuel-loader evidence for the reused families

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w211` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w211` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W211.md` (new; the matrix and cut design at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W211/` |
| Plan | `D:/GitHub/ARCE-Task-Evidence/root-queue/W203/PLAN.md` (SHA-256 `0667fe44…8f555e`), section 3, "W203-P2", with section 1.2's rows for the existing evidence; its write scope, tests and exclusions are part of this TASK |
| Background | the plan's sources S7 (`records/W11B3-DESIGN.md`, sections 5 and 6) and S1 (`docs/status/CURRENT_VERSION.md`, "What is still open"); R-035 in `docs/11-RISK-REGISTER.md`; the records of W97, W105, W107, W125, W130, W133, W176 and W204 (N3-C, which supplies the N2 representative) |

**Root decision R182.** N3-C (W204) is merged, which unblocks W203-P2. B3-S6 needs a real fuel-loader representative
for each reused family that S7 names: E3, E5, R2, O1 to O4 (including O3J), O-veto, G1, G3, and D2-F0 to F8 (with
F8/N2's crash windows, the failed respawn F9, and refill only after F8). Much of this has evidence already; the task is
to map it honestly and run only what is missing.

## What to do

1. **Matrix and cut design first, committed before any native run:** for every required representative, the existing
   evidence (record, commit, case, restarts) that covers it with a real loader, or "missing", or a labelled residual
   (E4's unobservable failures stay residual). A chest, armour stand or cargo device is not a fuel-loader
   representative. Map W176's applicable G1 and G2 rows without turning its three failed downgrade rows into passes.
   For each missing row: the cut sides (around durable replacement and acknowledgement), the two restarts, the oracle
   checks and the expected outcome. Commit a mapping and count ledger before the first launch.
2. **The oracle and runner** (`scripts/run_v180_loader_s6.py`, `scripts/loader_s6_oracle.py`,
   `tests/test_loader_s6_oracle.py`): the oracle rejects a changed quantity, marker, epoch or identity and an UNKNOWN
   read as zero, with tests that fail first. Observation-only additions to `EscrowLoaderReleaseProbe` or
   `EscrowLoaderFollowUpProbe`, or new test fixtures, only if the existing hooks cannot observe a row; name each in the
   record. Existing loader and escrow runners are read-only imports.
3. **Run the missing cuts natively** under the heavy-JVM lock, in counted tranches, on a JAR built from your committed
   source: each cut with two restarts; include loader fairness behind held or unloaded records, shutdown with queued,
   in-flight, UNKNOWN and failed-close writes, a failed respawn, and successor refill only after F8. Keep exact mono and
   two-loader totals and the required bipropellant representatives. Stop and report if a row fails twice; a production
   cause is reported for a separate fix, never hidden in a fixture.

Other workers use the same lock (W209 now); wait for it, never remove it, and release only your own.

## Verification

The oracle tests failing first; the new unit tests alone and the whole unit suite; the loader and escrow batches that
the matrix names; the native tranches with their counted starts and failures. Root's hosted run on the pushed branch
stands in for a complete local GameTest. An independent review of the matrix, the oracle and the results follows.

## Not in this task

The bay's B1 to B18, C or D acceptance by analogy, the settlement accounting schema, a forced save to repair an oracle,
escrow writer or recovery logic, any production change, enabling admission, budgets, timeouts, ADRs, status files and
the ledger.

## Root decision R185 (2026-10-30 10:25): continue after the A1 blockers

The first session (09:39 to 10:21) delivered `c05847631` as BLOCKED/partial: the matrix and cut plan
(`4f67f826e`), the strict S6 oracle and the E3 cache-close cut (`395529b3a`), and A1's native results (two attempts,
six starts: the source-first K4 image passed both restarts; the destination-first K5 image failed both on
`EscrowLoaderCutProbe.recover`'s "Native restart violated raw control total"). It names two blockers, both decided
here:

1. **Inventory registration is authorized.** Add the new producer `run_v180_loader_s6` to
   `scripts/inventory_escrow_probe_cases.py`, its pinned `src/test/resources/escrow-probe-cases.tsv` and their
   focused tests, exactly as the other producers are registered; no other change to the inventory or its rules.
2. **An S6 recovery observer is authorized, test-side, under these conditions.** The reused K7 check sums native
   resources plus source-debit-only held resources, so it cannot represent a destination-first image whose
   destination is credited while the source debit is still held under the lock. Keep `EscrowLoaderCutProbe` and its
   assertion unchanged for its own cases. The S6 observer must classify every store on every tick as available or
   held under the durable record, with the held amounts taken from the record (never inferred from the raw sum), and
   require: raw native equals available plus held exactly; nothing is held without a record; the completed image's
   totals equal the initial totals exactly, with no record or markers left. Its tests fail first and reject a
   destination credit larger than the record holds, a held amount with no record, a final total that differs, and
   UNKNOWN read as zero. Write the observer's rule in the record before the native rerun. If the image cannot be
   explained by the record, stop: that is a production finding, not a fixture change.

A fresh session continues on the same branch and worktree from `c05847631`: read the record first and keep every
retained result. Rerun A1's destination-first image with two restarts, then the remaining tranches A2 to C, committing
tranche C's concrete selections and counts before its first launch. The rest of this TASK is unchanged.

## Root decision R186 (2026-10-30 11:27): diagnose E5's unload observation, then continue

The second session (10:23 to 11:25) delivered `068d10bd1`: the inventory registration, the record-backed S6 recovery
observer (`9f8e0fe59`), A1's destination-first K5 image passing both restarts, and E5's unloaded-fairness cut
(`9a3ad49f9`), which failed twice and stopped. In both attempts `EscrowLoaderS6UnloadedProbe.tick` threw "S6
fairness slot unloaded without endpoint removal": `getChunkNow(128,128)` was null and `areEntitiesLoaded` false, but
the retained loader and rocket references were not both `isRemoved()`; the log does not say which. The healthy request
was never queued, so nothing about fairness or resources was observed.

A fresh session continues on the same branch and worktree from `068d10bd1`:

1. **Diagnose first, test-side.** Add observation only: for each retained reference, its type, `isRemoved()`, its
   removal reason where the type has one, and the tick on which each of the three conditions (chunk absent, entities
   unloaded, reference removed) first holds; and whether the reference is still the object the level holds at that
   position or id. Run E5 once natively and record the sequence.
2. **If both references are removed within a few ticks after the chunk and entity conditions,** the probe asserted
   too early: make it wait for the complete unloaded state (all three conditions) within a fixed bound stated in the
   record, failing if the bound passes, with a test that fails first for a reference that never becomes removed. The
   failed attempts stay recorded; the revised E5 gets a new two-attempt budget.
3. **If a reference stays live after its chunk and entities have unloaded,** or the probe held a stale object, say
   which. A stale fixture reference is fixed in the fixture. A live block entity or entity after unload is a production
   finding: stop and report it with the evidence.

Then run E5 and continue R2 and the tranches A3 to C as the TASK and R185 describe, committing tranche C's concrete
selections and counts before its first launch. The rest of this TASK is unchanged.

## Root decision R188 (2026-10-30 12:45): fix the oracle's native UUID handling, then continue

The third session (11:28 to 12:43) delivered `588c90ef6`: the diagnosis showed the probe asserted two ticks too early
(the rocket is removed with the chunk, the loader block entity two to three ticks later), so `598b487a6` waits for the
complete unloaded state within a fixed 20-tick bound. Both revised E5 attempts then reached the fairness checkpoint
(the healthy fill retired behind the unloaded far record) and all four restarts completed with exact totals and
unchanged identities, but both failed in `scripts/loader_s6_oracle.py` `check_e5`: native UUIDs decode as integer
lists, and `set(retired_ids)` raises `unhashable type: 'list'`. Root pushed the branch at 12:45. (Correction, 13:30: the governance workflow, which runs the whole Python suite,
runs only on the shared branch and main, so the branch gets only the regression run; see point 4.)

A fresh session continues on the same branch and worktree from `588c90ef6`:

1. **Fix the oracle's representation only:** normalize native UUIDs (the four-integer array form, and any other form
   the native images carry) to one canonical value before comparing or counting, everywhere the oracle reads an
   operation, record, marker or entity ID, not only in `check_e5`. Tests that fail first use the native integer-array
   form taken from a retained image. No check is removed or relaxed.
2. **Replay the retained images** of both revised E5 attempts through the corrected oracle and record the result as a
   replay, not as new attempts.
3. **Then a renewed E5 budget** (two attempts) on a fresh native run from committed source, then R2 and the tranches
   A3 to C as the TASK, R185 and R186 describe, committing tranche C's concrete selections and counts before its first
   launch.
4. Read the hosted regression result on the pushed branch. For the whole Python suite, which only the shared branch's
   governance runs (on Linux): compare your local discovery's failures and errors, test by test, with the same
   discovery on your base `e00ef1ff2` in a detached checkout on this machine. A failure only on your branch is yours to
   fix; one the base also has is recorded, not fixed here.

The rest of this TASK is unchanged.

## Root decision R190 (2026-10-30 14:01): snapshot comparison, and fixture defects without stopping

The fourth session (12:46 to 14:00) delivered `ab2f17b9e`: native UUIDs canonicalized throughout the oracle
(`fd3ef6bbd`), both retained E5 images replayed, a fresh E5 run passed, and R2's KEEP_SOURCE cut passed; R2's
destructive-source cut failed twice because the new fixture compares independently decoded snapshots with object
equality, while the raw images stayed unchanged. The whole Python suite passes locally (2,325 tests, four skips).

A fresh session continues on the same branch and worktree from `ab2f17b9e`:

1. **Compare snapshots by value:** compare the canonical encodings (or a field-by-field value comparison covering every
   field), never object identity, with a test that fails first for two equal snapshots decoded separately and one that
   still rejects a snapshot differing in any block, state, payload or anchor. Replay the two retained destructive-source
   images, recorded as a replay, then give the row a renewed two-attempt budget.
2. **From now on in W211, a fixture or oracle defect does not stop the campaign** when all of these hold: the retained
   raw images are unchanged and explained by the records; the defect is in test code; the fix comes with a test that
   fails first; the retained images of the failed attempts are replayed through the fix; and the failed attempts stay
   recorded as failed. The row then gets one renewed two-attempt budget. A second defect in the same row, a production
   cause, or an image the records cannot explain still stops the campaign for Root.
3. Then the duplicate-reconnect row and the tranches A3 to C, committing tranche C's concrete selections and counts
   before its first launch.

The rest of this TASK, R185, R186 and R188 are unchanged.