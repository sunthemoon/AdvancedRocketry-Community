# CL18D-AUDIO-LIFETIME-03: bounded source-lifetime proposal

Date: 2026-10-08. Milestone: v1.8.0 / C18d. Status: **proposed, not frozen, not reviewed**.
Author: Claude, a delegated worker (not Root) dispatched by `claude -p` under
`D:/GitHub/AdvancedRocketry-Community/docs/work/v1.8.0-c18d-audio/TASK-03.md`. Fixed source base
`2e397a49c92a323eb1886a538e226765e7f4edc9`. Companion files:
[LIFETIME-TEST-DESIGN-03](LIFETIME-TEST-DESIGN-03.md) ("T-") and
[LIFETIME-HANDOFF-03](LIFETIME-HANDOFF-03.md).

This is one narrow leaf. It amends only the source-lifetime rules of the successor contract draft
02 (`D:/GitHub/arce-v180-claude-audio-contract-20261007/docs/work/v1.8.0-c18d-audio/CONTRACT-02.md`,
cited as "C2 §n"): registry retention, rediscovery, discovery cursor, validation fairness and
the work they charge. It answers findings M1-M4 and L2 of the independent review
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18d-audio-codex-independent-20261008-02/REPORT-01.md`
("R2"). It writes no source, asset, registration, carrier or ledger entry; it selects no OPEN
owner or Root decision and accepts no contract or Gate. Everything in C2 not replaced below
stays as C2 states it, including its open items, until a separate task changes it.

Preserved without change: the accepted hard stop distance of 32 blocks for a playing loop
(ADR-066 §7.1; checked every client tick) and the cap of 32 simultaneous ARCE loops per client.
No native Minecraft or Forge enumeration API, enumeration order, packet order or thread order is
assumed proved here; every such dependency is written as a port requirement in §7.

## 1. Disposition

| Finding | Disposition in this leaf | Where |
| --- | --- | --- |
| R2 M1: a distance stop deletes a still-valid entry | **Proposed fix.** Validity and eligibility are separated. Distance never removes an entry; it only decides whether the entry may hold a slot. | §2, §5 |
| R2 M2: clearing `overflowed` loses dropped sources outside the cube | **Proposed fix.** The `overflowed` flag and its clear condition are removed. Neighbourhood discovery runs every tick within a fixed budget, independent of drop history. | §3 |
| R2 M3: listener movement restarts the sweep and starves later objects | **Proposed fix.** A pass works on a frame fixed at its start; movement never resets the cursor; each tick advances by at least one charged unit. | §3.2 |
| R2 M4: snapshot round-robin misses the 16-tick bound | **Proposed fix.** A first-in-first-out validation queue replaces the snapshot; the 16-tick bound is derived in §4. | §4 |
| R2 L2: discovery and admission work undefined | **Proposed fix in the specification only.** Every traversed native entry, copy and resume skip is charged; admission uses one batch merge per tick, not a per-admission scan; counters cover each step. The actual adapter is not chosen. | §6, §7 |
| CLI F2 conditional generation trace (R2: not proved) | **Mitigation only.** Inputs are stamped with the Level object instead of a generation number (§5.1), and discovery recovers a dropped input inside the cube. No platform order is claimed. | §5.1 |

Other R2 findings (M5, M6, L1, L3, L4) and C2 open items are outside this leaf and are
transferred, not deleted (§9).

## 2. Entry states: validity separated from eligibility

An entry keeps C2 §4.2's fields (key, event ID, last known position, weak binding) except that
the stamp is the Level object (§5.1). Each entry is in exactly one of these states:

| State | Meaning | May hold a loop slot |
| --- | --- | --- |
| `RETAINED` | valid and active; not playing (any distance) | only if selected (§5) |
| `PLAYING` | valid, active, selected, a loop instance exists | yes |

Two separate predicates are evaluated:

- **Valid** (object-level, independent of the listener): the weak binding is not cleared; the
  object is not removed; its Level is the current client Level object; its chunk is loaded on the
  client (checked without loading it); for a block, `getBlockEntity(pos)` returns the bound
  object; and its current synchronized active input is true (block state `LIT`, the `active`
  update-tag field, or the decoded rocket state, exactly as C2 §3 and §4.4 define).
- **Eligible** (listener-level): squared distance from the tick's listener position ≤ 1,024 for a
  `PLAYING` entry, ≤ 900 for a `RETAINED` entry (C2's 30/32 hysteresis, unchanged).

Rules:

- An entry leaves the registry only when: validation finds it invalid; a buffered or discovered
  input reports it inactive or ended; the batch merge evicts it (§3.3); or a reset (§5.1).
- **Distance alone never removes an entry.** A `PLAYING` entry whose squared distance exceeds
  1,024 at a tick is stopped at that tick's publication and becomes `RETAINED`. When the
  listener later comes within 30 blocks, the next selection may start it again with no source
  callback and no discovery needed. This removes R2 M1's walk-away-and-back loss for blocks and
  for a moving entity that stays tracked.
- A tracked entity that leaves the client (leave event or validation finds it removed) is
  removed; if it returns, its join callback or discovery offers it as a new entry.
- If an input for an existing key carries a **different binding object** (for example a
  replaced block entity), the old entry is removed (its loop stops at this publication) and the
  input is admitted as a new entry, eligible for selection from the next tick. A key therefore
  never has two loop instances and is never stopped and started in one publication.

## 3. Discovery: always on, bounded, progress-preserving

### 3.1 Why always on

C2 §4.2 ran the re-offer sweep only while `overflowed` was set; R2 M2 shows that the flag can
clear while dropped active sources exist outside the current cube. Two policies remove the
defect: (a) a generation-long "drop happened" memory, or (b) discovery every tick. This leaf
**proposes (b)**. Policy (a) would still depend on every initial-state binder callback being
delivered and on unverified packet/tick ordering (CLI F2); (b) has no clear condition to get
wrong and also recovers missed initial or change callbacks inside the cube. Its cost is a
constant budget (§6), which Root may reject in favour of (a) as a numeric/design decision under
C2 A-OPEN-12; that alternative is not silently selected.

### 3.2 Frame and cursor

- **Cube.** Axis-aligned, half-size 32 blocks around the listener position (C2, unchanged). It
  contains every point within the 30-block start radius. It intersects at most 5 × 5 = 25
  client chunk columns.
- **Frame.** At the start of a pass the scheduler records the frame: the list of chunk columns
  intersecting the cube at the listener position of that tick, ordered by column x then column
  z ascending. The frame is immutable until the pass ends.
- **Cursor.** `(frame index, column token)`; the column token is opaque to the scheduler and
  owned by the discovery port (§7, port P2). It is a value, not a native iterator.
- **Step.** Each tick the port examines entries starting at the cursor until the tick's charged
  budget is spent or the frame ends. Each examined object that is a known emitter type, valid
  (§2) and active is offered (§3.3). Columns that are not loaded are skipped at a charge of 1.
- **Movement never restarts a pass.** When the frame is exhausted, the next tick starts a new
  pass whose frame uses that tick's listener position. Only a reset (§5.1) clears the frame and
  cursor.
- **Progress.** Each step consumes at least one charged unit of new frame work (port rule
  P2-c). A pass over a frame of total charged work `W` therefore ends within `ceil(W / 128)`
  ticks, regardless of listener motion. Teleporting mid-pass wastes at most the rest of the
  current pass.

**Discovery latency (derived).** Let `T` be `ceil(W_max / 128)`, where `W_max` is the largest
charged work of any frame during the interval. If a loaded, valid, active source stays within 30
blocks of the listener from tick `t`, the first pass that starts at or after `t` has it in its
frame. That pass starts by `t + T` and ends by `t + 2T`, so the source is offered by tick
`t + 2T` and, if it ranks among the first 32 eligible entries, plays at that tick's publication.
`W_max` depends on the port's charge for the ≤ 25 columns (§7); the bound is stated in that
measured quantity, not as a fixed tick count.

### 3.3 Inputs, batch merge and admission

Per tick, after the generation check and the single listener read (§5.2):

1. **Drain** the change buffer (C2's 512-event cap unchanged) into a map `M` from key to the
   last input for that key (arrival order within one key; C2 rule unchanged). Inputs whose
   Level stamp is not the current Level object are dropped and counted (§5.1).
2. **Discovery step** (§3.2). An offer for a key already in `M` overrides it, because the
   offer was read from the object later in the same tick. Discovery offers are active-only; it
   never deletes. Discovery offers do not use the change buffer and are capped by the discovery
   budget (≤ 128 offers per tick).
3. **Merge.** Removals and inactive inputs delete their entries. Inputs for present keys update
   position (and binding per §2). New keys are collected. If `registry + new ≤ 1,024`, all new
   keys are appended to the validation queue (§4) in comparator order. Otherwise the registry and
   the new keys are sorted once by C2's complete comparator at this tick's listener position;
   the first 1,024 stay; the rest are dropped (`admission_dropped`) or evicted (`evicted`;
   a `PLAYING` evictee stops at this publication). Surviving new keys are appended in comparator
   order.

Consequences: there is no per-admission search for the worst entry. A tick makes at most one
sort: of the ≤ 640 new keys, or, when the registry overflows, of at most 1,024 + 640 = 1,664
entries. Because new keys
are inserted and truncated by the comparator, the registry after the merge does not depend on
the arrival order of inputs for **different** keys whenever the change buffer did not overflow.
This is a structural property; the W01 oracle itself stays with R2 M5's owner (§9).

## 4. Fair validation queue

- The registry keeps a first-in-first-out **validation queue** of its keys (for example an
  insertion-ordered set with O(1) removal). New keys are appended at the merge. An input for a
  present key does **not** move it (repeated offers cannot postpone validation).
- Each tick, after the merge: validate every `PLAYING` entry (≤ 32), then take up to 64 keys
  from the head of the queue; an invalid one is removed; a valid one is re-appended at the tail.
- Removal or eviction deletes the key from the queue immediately.
- No snapshot is taken.

**Bound (derived).** The registry never exceeds 1,024 keys, so when a key is appended at most
1,023 keys are ahead of it. Keys appended later are behind it; removals only shorten the
prefix; each tick removes 64 keys from the head. A key appended in tick `a` is therefore
validated no later than tick `a + 15` (in R2 M4's example: admitted at tick 2, validated by tick
17), and after each validation it is validated again within 16 further ticks. Hence every entry
is validated at least once in every window of 16 consecutive scheduler ticks while it remains,
and an invalidity that begins without a callback is applied within 16 ticks. A `PLAYING` entry
is additionally checked every tick. Paused ticks are not scheduler ticks (C2 §4.8 unchanged).

## 5. Selection, hard stop and reset

### 5.1 Reset and input stamping

- The reset triggers stay those of C2 §4.4 (different `ClientLevel` object, `LoggingOut`,
  `Clone`, missing Level). A reset stops every ARCE loop, clears the registry, validation
  queue, discovery frame and cursor, and increments the diagnostic generation counter.
- **Stamping changes.** Binder inputs carry a weak reference to the `ClientLevel` object they
  came from, not a generation number. A reset keeps buffered inputs whose Level is the new
  current Level and drops the others (`stale_level_inputs`). An input created for a new Level
  before the end-of-tick generation check is therefore not discarded merely because it arrived
  first. R2 records that such an order is a conditional trace, not a proved platform race; this
  rule removes the dependency instead of asserting the order. Validation reads the object, so a
  kept input can never revive a stale object.
- Within one Level object a `Clone` still resets; inputs stamped with that same Level are kept
  and re-validated.

### 5.2 Order within one end-of-client-tick

1. Reset check (§5.1).
2. Read the listener position once; every distance in this tick uses it.
3. Drain (§3.3.1), discovery step (§3.3.2), merge (§3.3.3).
4. Validation (§4).
5. **Hard stop:** every `PLAYING` entry with squared distance > 1,024 at this tick's position is
   marked to stop and becomes `RETAINED` (≤ 32 distance checks). This runs every tick even when
   selection does not.
6. Selection, when the merge changed the registry, an entry was removed or stopped, the
   listener moved at least one block since the last selection, or 10 ticks passed (C2 triggers,
   unchanged): the first 32 eligible entries by the comparator, using a bounded 32-element heap.
7. Publication (C2 order unchanged): stops in ascending key order, then starts in selection
   order, skipping unavailable events.

## 6. Work charged per tick (replaces C2 §4.7)

| Step | Charge | Bound per tick |
| --- | --- | ---: |
| callbacks buffered | 1 per input | 512 (then dropped and counted) |
| drain into `M` | 1 per buffered input | 512 |
| discovery | 1 per native or index entry traversed, per unloaded column skipped, per entry copied into any temporary, and per entry skipped to resume a cursor (port P2-a) | 128 |
| discovery offers | 1 per offer | ≤ discovery charge |
| merge | key lookups ≤ 640; append ≤ 640; either a sort of ≤ 640 new keys or, on registry overflow, one sort of ≤ 1,664 entries | 1 sort |
| validation | 1 per entry checked | 32 + 64 |
| hard stop | 1 per playing entry | 32 |
| selection | ≤ 1,024 distance computations and heap offers | 1 run |
| publication | stops / starts | 32 / 32 |

Counters (readable by a test hook or the debug screen): `inputs_buffered`,
`inputs_dropped_buffer`, `inputs_applied`, `stale_level_inputs`, `discovery_charged`,
`discovery_offers`, `discovery_passes`, `merge_sorts`, `admission_dropped`, `evicted`,
`validations_playing`, `validations_queue`, `distance_stops`, `selection_runs`, `starts`,
`stops`, `off_thread_calls`. Charges are counted by the code that performs the work; the test
design (T-W) observes them independently through counting fakes.

These numbers bound the scheduler and the port contract. They do not prove that any real native
traversal fits them; that needs the executed port evidence of §7.

## 7. Adapter ports needed (Root-owned mechanisms)

| Port | Purpose | Requirements before source work |
| --- | --- | --- |
| P1 binder inputs | initial state, change and end per producer (C2 §4.6, A-OPEN-10) | carries the Level object stamp (§5.1); main thread only; never loads a chunk; may be incomplete without breaking §3's latency inside the cube |
| P2 discovery | resumable bounded examination of emitter objects in a frame | see P2-a..f below |
| P3 validity read | evaluates §2's validity for one entry | O(1); main thread; reads loaded state only; never loads chunks or creates objects; charged 1 |
| P4 listener | main camera position | read once per tick (§5.2) |
| P5 playback | start, stop, `isActive` for loop instances | as C2; motion of a playing entity sound remains R2 L3 (§9) |

Port P2 contract:

- **P2-a charge.** Every native or index entry traversed, every entry copied into a temporary
  snapshot (charged when copied), every unloaded column visited and every entry skipped to
  re-find a cursor position counts against the 128 budget. No single native call may cost more
  than the remaining budget; a bulk copy of a column larger than the budget is not allowed.
- **P2-b cursor value.** The column token survives arbitrary additions and removals between
  ticks without holding a native iterator.
- **P2-c progress.** While the frame is not exhausted, each step examines at least one entry
  not yet examined in this pass (or finishes a column).
- **P2-d mutation tolerance.** An entry added or removed during a pass may be examined zero,
  one or two times in that pass; an offer is idempotent. An entry present for the whole pass is
  examined exactly once.
- **P2-e scope.** Only columns in the frame; no other loaded chunk is touched; client-provided
  positions only; nothing is force-loaded.
- **P2-f evidence.** A conformance test (T-P) over the real adapter on a physical client, with
  dense columns, mutation during a pass and a measured `W` for the worst fixture.

Candidate mechanisms, **not selected**: (1) traversal of the client chunk's block-entity
collection and the client entity storage, acceptable only if a stable resumable position
satisfying P2-a/b/c is shown for Forge 47.4.10 (an unordered map that must be re-walked from
its start to resume fails P2-a for large columns); (2) a binder-maintained index of loaded
emitter objects per column, which satisfies P2-b/c by construction but depends on P1's load
callbacks and needs its own capacity and overflow rule. Root chooses and supplies P2-f evidence.

## 8. Changes to C2 test cases

| C2 test | Change |
| --- | --- |
| S04, S05 | expectation changes from "stops" to "stops and is retained"; see T-M1 |
| S06 | not changed here; with 0.5-block steps C2's 1-block selection trigger can delay the start by one tick, so its "starts in the tick it reaches 30" needs the oracle owner's review (§9) |
| R04, R05, R06 | superseded by T-M2 (no `overflowed` flag; always-on discovery) |
| V02 | 16-tick claim superseded by T-M4 |
| W02 | "the sweep later re-offers" becomes "discovery offers within T-M2's latency" |
| W05 | counter list replaced by §6 and T-W |

All other C2 test cases are untouched by this leaf.

## 9. Transfers (kept open, not deleted)

| Item | Owner / next step |
| --- | --- |
| R2 M5 (W01, O03, S10 oracles) and the S06 timing note of §8 | audio oracle successor |
| R2 M6 coverage mapping, including draft-01 items and per-file asset evidence | audio coverage successor |
| R2 L1 configuration-reload clearing (ADR-066 §7.1) | audio lifecycle successor; must also reset §3.2's frame and cursor |
| R2 L3 one-shot ingress, freshness and moving sound position | audio one-shot successor |
| R2 L4 command and evidence narrative | applies to every packet; this leaf records what it did in its handoff |
| reload ordering (C2 A-OPEN-11, R2 on L03) | Root |
| C2 A-OPEN-1..12 | unchanged owners; A-OPEN-10 gains port P1's stamp rule and A-OPEN-12 gains §3.1's policy choice and the 128 discovery budget |
| P2 mechanism and P2-f evidence | Root |

## 10. Limits of this proposal

All bounds in §3.2 and §4 are hand derivations from the rules above; none is executed. The
discovery latency depends on the measured port charge `W`. A retained entity that moves back
within 30 blocks while the listener stands still can wait up to 10 ticks for C2's periodic
selection trigger (T-M1-02 records this; changing the trigger belongs to the oracle successor).
No claim is made about real
Forge/vanilla packet, tick, reload, `Clone` or enumeration order, or about sound-engine
behaviour. This proposal passes no build, listening, V1, V2 or v1.8 G0-G9 Gate.
