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

## Root decision R192 (2026-10-30 17:30): the meaning of G3 L2 queued, then A4 to C

The fifth session (14:02 to 17:28) delivered `9e574e587` (tested source `3428aa1fa`): snapshots compared by
canonical encoding with the retained images replayed; R2's destructive-source and duplicate-reconnect rows; and six G3
normal-shutdown rows (L1 in flight, L2 in flight after one observer correction, L1 queued, L1 UNKNOWN, L2 UNKNOWN,
failed close), each passing with two restarts; 30 more starts. It asked what "G3 L2 queued" means, because
`EscrowCoordinator.retireNative` (lines 443 to 447) refuses unless the writer is idle and nothing is queued.

**Decision.** In this product an L2 native retirement can never wait in the writer's successor slot: `retireNative`
is refused while the writer is busy or another request is queued, and the caller keeps its checkpoint and retries. So
"L2 queued" is **the native reconciliation checkpoint pending retirement while the writer is busy**: send the normal
stop while that checkpoint is proved and waiting for the idle writer (with an actual L1 or other write in flight or
queued), inspect the stopped durable authority, and restart twice; the pending checkpoint must neither retire early nor
lose the fill. The writer-successor form is structurally excluded, not a missing cut: pin it with a unit test that
`retireNative` returns false for a busy writer and for a non-empty queue, leaving ledger, generation and markers
unchanged, and record the exclusion with that test in the matrix. Do not call a legacy or model retirement API to
manufacture the other form.

A fresh session continues on the same branch and worktree from `9e574e587`: the L2-queued row as decided, then A4
(native G1 veto and saturation), B (loader operator settlement) and C (governed flight, F8, F9 and successor refill
after F8), committing tranche C's concrete selections and counts before its first launch. R190's rule for test-side
defects still applies. The rest of this TASK, R185, R186, R188 and R190 are unchanged.

## Root decision R194 (2026-10-30 18:59): the three G1 oracle defects, then fresh G1 attempts

The sixth session (17:30 to 18:57) delivered `fedd1874b`: R192's L2-pending retirement passed with two restarts
(after one fixture correction), with the writer-successor form pinned as excluded by unit tests; A4's G1 veto reached
its native save experiment on all six starts but failed qualification twice on three independent test-side defects:
the log matcher compares lines with their trailing newline, the global-data inspection reads the whole native
`level.dat` with the celestial reader's 4,096-tag bound (205,682 bytes fail at tag 4,097, so every image became
UNKNOWN), and the binding comparison expects two UUID fields where native bindings also carry schema, Level and x, y
and z. No production failure is shown.

**Decision.** All three are test-side and may be fixed, each with a test that fails first on the real native form:
strip line endings once where lines are captured or matched (not by loosening a marker); read `level.dat` for the
evidence oracle with its own reader and an explicit bound stated in the record and large enough for a native
`level.dat`, never by enlarging the product's celestial bound, and still reporting UNKNOWN, never zero, when a bound
or read fails; compare the complete native binding (every field), not two UUIDs. Because the failed attempts kept
UNKNOWN images, they cannot be replayed: they stay FAILED, and G1 veto gets a fresh two-attempt budget on committed
source. Then G1 saturation, B and C, committing tranche C's concrete selections and counts before its first launch.
R190's rule applies again from here.

A fresh session continues on the same branch and worktree from `fedd1874b`. The rest of this TASK and R185 to R192
are unchanged.

## Root decision R197 (2026-10-30 21:00): the interim review's oracle findings come first

An interim independent review of `e00ef1ff2..fedd1874b` ([TASK-W211-INTERIM-REVIEW](TASK-W211-INTERIM-REVIEW.md);
`D:/GitHub/ARCE-Task-Evidence/root-queue/W211-interim-review/REVIEW.md`, SHA-256 `3fad14a4…5f67c745c`) found the
completed observations and counts sound (79 starts, 52 restarts; no concealed failure, unexplained image or production
change; every passed image also passes its stricter supplemental checks), but the raw-image oracle accepts wrong
states: W211-I1 (High) cross-restart comparisons omit the complete snapshot and the typed fuel state; W211-I2 (Medium)
a record's endpoint epochs and complete native binding are not checked against the image; W211-I3 (Medium) the G3 L2
check does not enforce the writer generation; W211-I4 (Medium) the real-loader N2 crash-window representative has no
row; W211-I5 (Low) the per-tick log check cannot detect a missing prefix. The reviewer's in-memory mutation witnesses
are in `mutation_checks.py` and `mutation-results.json` beside the review.

**Decision.** The session running when this was written continues to its end; its images are kept. The next session,
before any further native launch:

1. fixes I1, I2, I3 and I5 in the S6 oracle, each with tests that fail first on the reviewer's mutations of retained
   native images (no legacy oracle is relaxed or modified);
2. replays every retained passed image of W211, including those produced after `fedd1874b`, through the corrected
   oracle and records each replay; a passed row that now fails is reported as failed, not re-run silently;
3. for I4, splits W204's shared N2 evidence from the real-loader representative, marks the latter missing and adds its
   cut and restarts to tranche C's committed enumeration.

Then it continues with whatever remains of A4, B and C. R190's rule applies. The rest of this TASK and R185 to R194
are unchanged.

## Root decision R198 (2026-10-30 21:31): the O3J allowlist entries, and the order of the next session

The seventh session (19:04 to 21:29) delivered `943d29ccd`: the three G1 oracle fixes, fresh G1 veto and saturation
runs, and thirteen O1 to O4 operator rows with two restarts each (52 more starts; 131 starts, 86 restarts, 28 passed
and 16 failed attempts in all). O3J was designed but not launched, because `EscrowNamedFillTestHook.CASES` (the
release-gated allowlist that lets historical named-fill fixtures run without opening named production admission)
rejects S6 selectors; its draft is kept as `o3j-unlaunched-draft.patch`.

**Decision.** Add exactly `LF_S6_O3J_BEFORE`, `LF_S6_O3J_REPLACED` and `LF_S6_O3J_ACK` to
`EscrowNamedFillTestHook.CASES` and nothing else. The hook's three conditions stay as they are (the hooks property,
the exact case name, the release gate). Add a unit test that fails first: each new name is refused without the hooks
property and without the release gate, and any other `LF_S6_` name is refused. Name this in the record as a gated
test seam in production source, for the final review. Named production admission stays closed.

The next session continues on the same branch and worktree from `943d29ccd`, in this order: R197 (fix I1, I2, I3
and I5 with failing-first tests on the reviewer's mutations, replay every retained passed image including R194's, and
map the real-loader N2 cut into tranche C); then O3J under this decision; then the rest of B (chains, abandon,
normalization, O-veto) and C (governed F1 to F8, F9, real-loader N2, sticky reservations and P1-F8, successor refill),
committing C's concrete selections and counts before its first launch. R190's rule applies. The rest of this TASK and
R185 to R197 are unchanged.

## Root decision R199 (2026-10-30 22:37): continue with the rest of B, then C

The eighth session (21:31 to 22:35) delivered `beda0b4b4`: R197's oracle fixes (I1, I2, I3, I5) with failing-first
tests, the real-loader N2 cut mapped into tranche C (I4), a final replay of all 93 retained images from the 31 passed
attempts through the corrected oracle (all pass; the failed attempts stay failed), the three O3J allowlist entries
under R198, and O3J BEFORE (after one setup correction under R190), REPLACED and ACK, each passing with two restarts;
146 starts, 96 restarts, 31 passed and 18 failed attempts in all.

A fresh session continues on the same branch and worktree from `beda0b4b4`: the rest of B (chain accept, abandon and
normalization, O-veto, and the required two-loader and bipropellant extensions), then C (governed F1 to F8, F9, the
real-loader N2 cut, sticky reservations and P1-F8, successor refill after F8), committing C's concrete selections and
counts before its first launch. R190's rule applies; a production cause or an unexplained image still stops. The rest
of this TASK and R185 to R198 are unchanged.

## Root decision R200 (2026-10-31 00:48): the C journal observer, then tranche C

The ninth session (22:37 to 00:46) delivered `169bccaf3`: eighteen B selections (two-loader operator chains,
checkpointed veto, chain abandon, neutral chain normalization) each passing with two restarts, and the retained replay
at 147 of 147; 215 starts, 142 restarts, 49 passed and 23 failed attempts in all. It found that C's F2 and F8 journal
REPLACED boundaries (after the checked replacement, before the owner's acknowledgement) have no S6 observer:
`EscrowTerminalProbe.journalPolicy` serves only its exact `FT_JOURNAL_*` selectors, and the chain that applies
such policies is in `RocketTransferSavedData.requestWrite` (lines 740 to 742), outside the listed write scope. The
first BI O-veto attempt stays FAILED (sixteen extra guarded ChunkMap save attempts before the selected denial in its
restart logs; resources and authority unchanged; its second attempt passed); that residual is carried into status.

**Decision.** Add exactly one link to the existing journal-policy chain in `RocketTransferSavedData.requestWrite`,
calling a new S6 probe's `journalPolicy` built like `EscrowTerminalProbe.journalPolicy`: it returns the given policy
unchanged unless the hooks property is set, the scenario is `start`, the case is one of C's exact committed S6
selectors and the release gate passes (`EscrowTestEndpoints.requireEnabled`); then it only wraps the committer to
pause before or after the existing commit when a read-only check of the staged and previous payloads matches the
selected F2 or F8 replacement. No failure injection, no change to what is written, no forced save, no reflection. A
unit test that fails first shows the identical policy object comes back when any condition fails and for any other
selector. Name it in the record as a gated test seam in production source, for the final review.

A fresh session continues on the same branch and worktree from `169bccaf3`: commit C's complete concrete selections
and counts (F1 to F8, F9, the real-loader N2 cut, sticky reservations and P1-F8, successor refill after F8) before
its first launch, add the observer under this decision, then run C. R190's rule applies; a production cause or an
unexplained image still stops. The rest of this TASK and R185 to R199 are unchanged.

## Root decision R201 (2026-10-31 01:42): the two F1 setup defects, and C's renewal rule

The tenth session (00:48 to 01:41) delivered `8503c0cc4`: C's committed enumeration (50 selections), R200's gated
journal observer with its tests, and the F1 fixture and oracle; F1 STORE BEFORE failed both attempts before the cut,
on two test-side defects explained by unchanged images (`r200/f1-diagnosis.json`): the fixture passed a filled but
still `ASSEMBLED` rocket to `RocketFlightData.withPlan`, which correctly refuses it, without the documented launch
promotion (`RocketFlightData.launchReady`); and the oracle's expected empty journal omits the native
`format_epoch` field. 221 starts, 146 restarts, 49 passed and 25 failed attempts in all.

**Decision.** Fix both, each with a test that fails first: the fixture takes the missing step through the product's
own launch-promotion path, never by bypassing or weakening the product's check; the oracle expects the complete native
empty journal, including `format_epoch`. Replay the six retained images through the corrected oracle as a replay;
the two attempts stay FAILED; F1 STORE BEFORE gets a renewed two-attempt budget.

For the rest of tranche C, R190's rule is extended: a row whose failed attempts are all explained by unchanged
retained images and caused only by test-side defects (a fixture that skipped a step the product requires, taken
through the product's own path; or an oracle that misreads the native form) may be fixed once, with failing-first
tests and a replay, even when two such defects appear in the same row; the row then gets one renewed two-attempt
budget. A production cause, an image the records cannot explain, a fix that would bypass or weaken a product check,
or a failure after that renewal still stops the campaign for Root.

A fresh session continues on the same branch and worktree from `8503c0cc4` with the rest of C as committed. The rest
of this TASK and R185 to R200 are unchanged.