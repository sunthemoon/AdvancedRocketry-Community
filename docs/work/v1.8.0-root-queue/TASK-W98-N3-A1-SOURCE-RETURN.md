# TASK W98: P1-N3's first leaf N3-A1, a durable survivor before an ordinary source return removes its record (Root decision R34)

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md), [records/W96-DESIGN.md](records/W96-DESIGN.md) and
the review `D:/GitHub/ARCE-Task-Evidence/root-queue/W96-review/REVIEW.md` (SHA-256
`e58ca443f3cfa7cad7b208d98e0bad8fc853c14ddb26391a73158219e3e1a338`, verdict FREEZE-WITH-AMENDMENTS) first. You are a
new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w98` (new; from the shared branch at `d2ee5614`) |
| Branch | `codex/v1.8.0-w98` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W98.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W98/` |

## Root decision R34 (recorded here)

1. **Frozen:** W96's inventory (section 2), its options analysis (section 3) and the direction for I1 to I3 (keep the
   actual record T, prove the survivor's own entity slot, then remove T), **with the review's amendments W96-R1 to
   W96-R5**, which override the note where they differ:
   - R1: the journal reservation is held only for one bounded proof attempt (one force batch plus readback, with a
     tick deadline); on failure, backoff or any wait it is released, and T stays as the per-rocket interlock; the
     pending state lives in a server-lifetime map keyed by L (at most 64 entries) and reserves again per attempt;
   - R2: the I3 cleanup retires T only when the record is settled, not live, and recovery made the source stationary
     (FUELED or ASSEMBLED, no plan, no active transfer, at its snapshot origin); a reconnect during a live source
     phase only remounts;
   - R3: crew uses the governed R17 placement rule (online, alive and connected, in the survivor's Level, aboard or
     unmounted; a dismount never invalidates delivery); offline crew do not block a countdown cancellation's
     retirement; no new playerdata adapter;
   - R4: the I1 census is the origin-column slot of each involved end plus the slot of each discarded candidate's
     actual position; a candidate off its origin column is a hold with a diagnostic;
   - R5: an ordinary sub-quota in the shared barrier (at most 8 pending, at most 4 entity captures per Level per
     batch), a silent checkpoint sink for ordinary attempts so governed probes never see them, a hold with T retained
     and one WARN while the Level is save-off (retry after save-on), and no refactor of governed classes.
   - **R5(c), Root's choice:** when another mod has replaced the Level's entity storage so the retained-store proof is
     unavailable, keep today's ordering (remove T after the RAM return) with one WARN per Level, and record it as a
     residual. Holding every return forever in such a world would be worse.
2. **Not frozen:** N3-B (I6 and I7; W96-R6 to W96-R8), the player-checkpoint adapter, and W96's proposed R-035 and
   ADR-065 text (W96-R9). They stay named open items; Root writes the text at the merge.
3. **W96-R2 is a possible no-crash defect** (a passenger who logs out during COUNTDOWN and back in during ASCENT may
   make the reconnect cleanup remove a live flight's record). It is confirmed or refuted first, by a GameTest.

## What to do, each in its own commit

1. **W96-R2 first:** a GameTest that logs a passenger out during COUNTDOWN and back in during ASCENT, then asserts
   that T remains and the flight continues. Run it on the unchanged code and record whether it fails. Then the R2 fix
   (the I3 restriction above), with the test passing.
2. **The ordinary retirement helper** for the three removal sites I1 (`RocketTransferContinuations.failBack`), I2
   (recovery's source removals) and I3 (the last reconnect), as W96 section 4 describes it with R34's amendments,
   reusing the existing escrow retained-store, force and readback machinery through the R5 sub-quota and silent sink.
   Keep N2's reserve-before-mutation, W91's per-record scheduling and every governed path unchanged.
3. **Tests:** unit tests for the helper's states (the review's R10 list where it applies to I1 to I3); GameTests for a
   crewed and an uncrewed countdown cancellation, a blocked-pad fail-back, each I2 action and the I3 reconnect; and
   the native cuts with W91K's harness `scripts/run_v180_f3_n2_cuts.py`: rerun N2_MOVED and N2_UNKNOWN unchanged (B
   must be FUELED at its origin, the same UUID and resources, exactly one B, A unchanged, on both restarts), plus a cut
   before the proof's store, after the store and before the force, and after the readback and before the removal (T
   must still own recovery).
4. Run each new class alone, the whole unit suite, `runData` if language data changes, and one full GameTest with the
   unchanged log checker (add an expectation only for a line your new code drives), all on committed source and
   serially. Say whether you add a GameTest batch (about 447 batch names; a reshuffle comes past 768).

## Write scope

`rocket/server/RocketTransferContinuations`, `RocketTransferRecoveryService`, a new ordinary retirement helper class,
the smallest additions to the shared escrow storage and barrier classes that R5 needs (no refactor of governed
classes), new tests, W91K's harness if a new cut point is needed (gated, observation or pause only), and the record.
No saved-format change, no change to a governed path, a proof's meaning, a lease or the replay exclusion, and no
relaxed assertion, limit or timeout. Stop and report if one seems needed.

## Commands and not in this task

As WORKER-RULES says. Other workers (W94B, W91R, W97) also run tests; take the heavy-JVM lock for every GameTest,
`runData` and native run, and release it between runs when another worker waits. Check that C: and D: have more than
10 GiB free before each heavy start. Delete each native session's server copy afterwards. When your heavy runs end,
remove your own lock and your `build/gametest/world` with a plain `rm -rf` after checking that the lock's `owner.txt`
names you; if the tool refuses, write the paths in the record and final answer at once.

Not in this task: N3-B (I6, I7), the player-checkpoint adapter, the N2 refusal and named-pad cuts (a later leaf),
ADRs, the risk register, status files and the ledger (Root).
