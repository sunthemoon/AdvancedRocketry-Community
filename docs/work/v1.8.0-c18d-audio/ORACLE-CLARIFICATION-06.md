# CL18D-AUDIO-ORACLE-CLARIFICATION-06: access scope, fixture timing and record fidelity

Date: 2026-10-08. Milestone: v1.8.0 / C18d. Status: **proposed, not frozen, not reviewed**.
Author: Claude, delegated worker (not Root, reviewer or approver), under
`D:/GitHub/arce-v180-claude-draft-revisions-20261008/docs/work/v1.8.0-c18d-audio/TASK-06.md`.
Fixed base `6923dae6a3d8cbd86df198efb5049d821535b8b5`. Companion:
[ORACLE-HANDOFF-06](ORACLE-HANDOFF-06.md).

Cited: "C5", "H5" = [ORACLE-CORRECTION-05](ORACLE-CORRECTION-05.md),
[ORACLE-HANDOFF-05](ORACLE-HANDOFF-05.md); "S4" = START-READ-PROPOSAL-04; "L3", "LT" =
LIFETIME-PROPOSAL-03, LIFETIME-TEST-DESIGN-03; "C2" = CONTRACT-02; "R5" =
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18d-audio-oracle05-claude-review-20261008-01/REPORT-01.md`
(not hashed here). `X:n` is line n of X at the fixed base, read in this tree.

Scope: R5 O05-L1, L2, L3 and I1, I2, I3 only. Section 1 lists what this note supersedes; C5, H5
and all sealed records stay unedited. Unchanged: S4 S2's predicate; reads 32/64/64/128 (288);
128 charge units; the 64-entry heap proposal; the 32-block hard stop each tick; the 32-loop cap.
No runtime mechanism, X/Y option or numeric policy is selected.

Kept distinct and open: **throughput** (R3 M2, option X coupling; Root); **moving source** (R3
M4 merge trigger, T-M1-02/04 timing, whether the merge or selection re-reads entity positions;
oracle successor); **mapping** (R3 L1 fields and stamps, R2 M6 coverage; coverage successor);
**installed native ports** (real P1-P5, P2-f, T-P-01/02, P3-g, C2 4.5's main-thread claim, real
behaviour of a cleared reference or a removed entity's position; Root); **assets** (A-OPEN-1,
A-OPEN-9, provenance, listening; owner and asset task); **whole contract** (consolidating C2,
L3, S4, C5 and this note; A-OPEN-1..12; Root).

## 1. Superseded clauses

| Clause | Replaced by | Finding |
| --- | --- | --- |
| C5:36-37, F1 "else `NONE`" | 2, A-1..A-4 | O05-L1 |
| C5:47-48, I2-c | 2, I2-c' | O05-L1 |
| C5:46, "This is S4 A2 option Y" | 3 | O05-I1 |
| C5:80, comparator parenthesis | 4 | O05-I2 |
| C5:76-79 and C5:84, Q64 "before tick `t`" and the hook's `t - 10` | 5, Q-1..Q-4 | O05-L2 |
| C5:85, T-S-06 without an L3 control | 5 | O05-I3 |
| C5:12, RV by path only | 6 | O05-I3 |
| H5:32 Grep count; H5:56 first sentence | 6 | O05-L3 |

## 2. Observed accesses (O05-L1)

- **A-1 Scope.** The harness opens a `SCHEDULER` scope around each call into the scheduler
  under test (end-of-tick handler, binder enqueue) and nowhere else; the scope API is test code
  that production code cannot reference. Each `AccessLog` record carries scope and mediator. In
  `SCHEDULER` scope the mediator is the P3 purpose while a P3 fake call is active, `PORT` while a
  `CountingPort` call is active, else `NONE`. Outside it the mediator is `HARNESS`: `Oracle`
  (LT:24), the test hook, fixture setup and mutation, assertions.
- **A-2 Fields.** Validity fields are C5 F1's six. An entity's `POSITION` is not one; a block's
  position comes from its key. Handle identity is not an access: handles are compared by
  reference, with no accessor and no record. This covers the merge's different-binding check
  (L3:69-72) and the Level-stamp check (L3 5.1). The reset check's current-Level read and the
  listener read (P4) are port reads, not object accesses.
- **A-3 Allowed non-P3 reads.** In `SCHEDULER` scope, `NONE` is allowed only with field
  `POSITION` (hard stop, selection, any merge sort; C2:125-126). `PORT` records may touch only
  column loaded state and the emitter-type test; another `PORT` record fails the instrument's
  self-check, not the scheduler.
- **A-4 Cleared handle.** The harness may clear a handle. It then compares equal only to itself;
  every P3 read of it returns invalid (`binding cleared`); `POSITION` returns the last position
  stored before clearing. A removed or unloaded entity's `POSITION` likewise returns its last
  stored position, so neither identity nor `POSITION` signals validity. The fake binder never
  creates an input with an already cleared handle; a handle cleared after enqueue is compared by
  identity at the merge and found invalid by its next P3 read (`QUEUE` or `PRESTART`).

**I2-c'** (replaces C5 I2-c): in `SCHEDULER` scope every `NONE` record has field `POSITION`, and
every `DISCOVERY` record is on an object `CountingPort` examined in that tick. `HARNESS` records
are outside I2-c'. T-A-03 and T-A-03b still fail it (at least five `NONE` validity records
each); their other C5:56-57 results are unchanged.

| ID | Planned case | Expected |
| --- | --- | --- |
| T-A-04 | Positive control, 40 ticks, scheduler implementing C5 and this note: two `ASCENT` rockets moving 1 block per tick within 30 blocks; a vent replaced by a new block entity whose input carries the new handle; a retained vent whose handle the harness clears; `Oracle` and the hook run after every tick. | I2-c' holds every tick; `NONE` records are `POSITION` only; `Oracle` and hook records are `HARNESS`; the replaced vent's old entry is removed at the merge with no access; the cleared vent is removed by its next P3 read. |

Limit: A-4 is a fixture rule, not a claim about Forge `WeakReference` or entity behaviour.

## 3. I2-b equality (O05-I1)

L3:98-99 offers every examined known-emitter object that is valid and active at its
examination. C5 F1 and A-1 leave a P3 read as the only route to validity, and the discovery step
precedes every other P3 read of the tick (L3 5.2). Each known-emitter examination therefore
needs its own `DISCOVERY` read, and C5 I2-b pairs them one to one. S4 A2's chain
(`discovery_offers <= validations_discovery <= examinations <= 128`, S4:79-81) stays true; I2-b
is its equality form under the current offer rule, which is what C5:46 calls option Y.

Recorded consequence: a scheduler that skips the `DISCOVERY` read for an examined emitter already
registered fails I2-b and loses L3 3.3 step 2's same-tick override. Allowing that skip changes
L3's offer rule and stays open with A-OPEN-12 X/Y and R3 M2.

## 4. Comparator (O05-I2)

C5:80's ranks use C2's full comparator (C2:106-109): squared distance from the tick's listener
position ascending, then event ID (ASCII), then source key (blocks before entities; blocks by x,
y, z ascending). Every source in Q64, T-S-04 and T-S-06 is an `air_hiss_loop` block vent, so ties
after distance fall to x, y, z. C5's ranks are unchanged; R5 re-derived them.

## 5. Fixture timing (O05-L2, O05-I3)

Q64, amending C5:76-79 and C5:84:

- **Q-1 Warm-up.** At least one scheduler tick with the fixture Level, an empty registry, the
  empty port and the listener at (0.5, 64.5, 0.5) precedes installation, so a first-tick Level
  check (L3 5.1) cannot clear installed entries. Whether a first tick resets is
  implementation-specific; the warm-up removes the dependency.
- **Q-2 Gap.** Every fixture action happens after tick `t - 1`'s end-of-tick handler returns and
  before tick `t`'s begins: V0..V63; for T-S-04, then P1..P31, P, S in that queue order, S's
  block-entity replacement and the hook's last selection `t - 10` at the fixed listener
  position; for T-S-06, N's buffered input and N's block-entity removal.
- **Q-3 First tick.** `t` is the first scheduler tick after installation. Check: a harness-owned
  tick counter (not a scheduler counter) shows no scheduler tick in the gap, and `AccessLog` has
  no `SCHEDULER`-scope record there.
- **Q-4 Listener.** Fixed from the warm-up through `t`.

| ID | Change | Expected |
| --- | --- | --- |
| T-S-04, T-S-06 | Q-1..Q-4 apply | as C5:84-85, plus the Q-3 check |
| T-S-06 | adds an L3-rule control | N starts at `t`; I1 fails (`FakeWorld`: N removed at `t`; no `PRESTART` read). The control needs Q64: in T-S-06-Q the `QUEUE` read removes N under L3 rules too. |
| T-S-04-G | negative fixture: T-S-04 with every fixture action before tick `t - 1` | `t - 1`: `PLAYING` 32, `QUEUE` 64 (V0..V63, re-appended), no selection (9 ticks after `t - 10`). `t`: `QUEUE` 64 = P1..P31, P, S, V0..V30; S removed by `QUEUE`; `PRESTART` 0; no start or stop. The Q-3 check fails, showing why Q-3 is needed. |

## 6. Source-record fidelity (O05-L3, O05-I3)

| Statement | Corrected | Evidence and limit |
| --- | --- | --- |
| H5:32, Grep count 3 | Five Greps: three before the H5 Write and two after it. | R5 O05-L3, citing Root's RECEIPT-01.json:28-33 and RESPONSE-01.md:11; neither read nor re-verified here. |
| H5:56, "All author tool calls are complete" | Untrue when written. The release took effect at the author's final answer, which reported the two later Greps; H5:34-35 had said later probes go there. | as above |
| C5:12, RV by path only | RV is pinned by TASK-05:22 as SHA-256 `399eeaac...a5bbac`, abbreviated as R5 gives it. | TASK-05 not read here; hash not recomputed. |

C5 section 5's H4 corrections stay as C5 states them: carried from RV, not re-verified.

## 7. Limits

All expected values are hand derivations; nothing was executed. T-A-04 and T-S-04-G need
independent re-derivation. No claim about real Forge, vanilla or sound-engine order. This note
passes no build, listening, V1, V2 or v1.8 G0-G9 Gate.
