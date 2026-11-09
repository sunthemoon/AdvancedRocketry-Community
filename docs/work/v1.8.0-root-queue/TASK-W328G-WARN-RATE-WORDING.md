# TASK W328G: the fence WARN rate wording and two pinning tests

Root: Claude. Date: 2026-11-09. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w328f`, branch `codex/v1.8.0-w328f`, at W328F's delivered commit `bdb4371ef`. Commit on top of it. |
| Decision | Root decision R400 (QUEUE row W328G), from the second review `D:/GitHub/ARCE-Task-Evidence/root-queue/W328FR/REVIEW.md` |
| Record | `docs/work/v1.8.0-root-queue/records/W328G.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W328G/` |

## Why

The second review approved W328 and W329 and found no production defect in W328F. It asks for one wording fix (R1)
and records one coverage residual (check 4). Root copies the corrected text into the risk register, so the wording
must be exact. No production code changes in this task.

## What to do

Each item is its own commit.

1. **R1: the WARN rate is a cycle bound, not a rolling-window cap.** Only `reevaluate` is throttled; `joined` reaches
   `fence` without that throttle (`rocket/server/RocketTransferFences.java`, about lines 136 to 170 and 253 to 258). So
   three WARNs for one record can fall within three ticks around a reevaluation, as the review's probe showed
   (ticks 1,199, 1,200 and 1,201). Correct the wording in `W329-DESIGN.md` (about lines 183 to 187, and the abbreviated
   claim at about lines 225 and 226), `W329.md` (about lines 144 and 145) and `W328F.md` (about lines 153 and 154):
   - keep the mechanism: one reevaluation per record per 1,200 ticks, at most two reevaluations per server tick, and at
     most one join or leave reason transition between two successive reevaluations;
   - describe the two-WARN figure as a steady-state bound per reevaluation cycle, not a maximum in every 1,200-tick
     window, and account separately for the first fence and the transitions at a cycle boundary;
   - remove or qualify the unqualified "128 WARNs per 1,200 ticks server-wide";
   - state that there is no per-session total.
   Use a labelled correction, `Correction (W328G, 2026-11-09)`, and keep the sentence it corrects.
2. **A boundary sequence in the fence test.** Extend `RocketTransferFencesTest` with the review's sequence: after the
   first fence and an observation at tick 0, a join reason at tick 1,199, the due reevaluation at tick 1,200 and another
   join reason at tick 1,201 each log one WARN, and a repeated stable join reason adds none. Use the same test paths
   W328F used, with no production seam.
3. **A structural pin of the pre-classification join (check 4).** Add a unit test, in the style of
   `EscrowRelaunchClassesTest` (ASM over the compiled class), that pins the calls `RocketReleaseAccess.joined` makes: it
   filters the manifest (`filter`), checks `idle`, and updates the flight data (`withPassengers`, `updateFlightData`),
   and it calls no resource transfer, admission or ledger write. Name it as structural evidence, not cold-start
   evidence, in its Javadoc and in the record. If ASM is not on the test classpath, use whatever
   `EscrowRelaunchClassesTest` uses.

## Verification

- `RocketTransferFencesTest` and the new structural test alone; then `./gradlew build` with every unit test, under the
  heavy-JVM lock (backslash lock path, as WORKER-RULES says; W330 shares it). Call Python by its full path in any
  detached script.
- Show once that the structural test fails if `joined` gains a forbidden call, in a scratch copy outside the
  worktree, then delete the scratch copy.
- `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews and merges.

## Not in this task

Any change to `src/main`; the transfer-fence deduplication (R397 keeps it); the risk register, the status file and
QUEUE; GameTests.
