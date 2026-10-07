# CL18D-AUDIO-LIFETIME-03: planned observations

Date: 2026-10-08. Status: **planned cases only; none executed**. Derives from
[LIFETIME-PROPOSAL-03](LIFETIME-PROPOSAL-03.md) ("P §n"). C2 means the successor contract draft
02 in `D:/GitHub/arce-v180-claude-audio-contract-20261007/docs/work/v1.8.0-c18d-audio/`; R2 means
the independent `c18d-audio-codex-independent-20261008-02/REPORT-01.md`. Expected values are
derived by hand from P's rules and are themselves unverified until a reviewer re-derives them.

Levels: **A0** pure JUnit on the Minecraft-free scheduler with fakes; **V1** one physical client
with the real adapter. No A1 case is needed: this leaf changes no server producer.

## 0. Independent observation instruments (A0)

Each case observes behaviour through instruments that do not read the scheduler's own state or
counters, so a scheduler bug cannot hide behind its own bookkeeping:

| Instrument | What it records |
| --- | --- |
| `FakeWorld` | ground truth: every object's position, active input, Level object, loaded flag and removal, per tick |
| `RecordingPlayback` | every start and stop with tick and key; derives the active instance set |
| `CountingPort` | a discovery port fake with a stable order; counts every entry traversed per tick and per pass; logs which entries were examined |
| `ReadLog` | per fake object, the ticks at which validity was read (P3) |
| `CountingComparator` | comparator invocations per tick |
| `Oracle` | brute force over `FakeWorld` only: distances, nearest-32 eligible set, hard-limit checks |

Scheduler counters (P §6) are compared **to** these instruments, never used instead of them.
Unless stated: block sources are vents at the named block, whose centre is the block position
plus 0.5 on each axis; the listener moves by teleport between ticks; C2's selection triggers
apply; the fixture's frame contains only the named objects, and empty frame columns cost 1.

## 1. M1: retention across distance (A0)

| ID | Case | Expected (instrument) |
| --- | --- | --- |
| T-M1-01 | Vent at block (10, 64, 0), active from tick 0. Listener at (0.5, 64.5, 0.5); from tick 1 it moves 1 block per tick toward −x until tick 40, then 1 block per tick back. No source input after tick 0. Run twice: with `CountingPort` and with a port that returns nothing. | Both runs: start at tick 0; stop at tick 23 (distance 33); no start at distances 32 and 31 on the way back; start at tick 60 (distance 30). Registry size 1 throughout (test hook). The empty-port run shows that retention alone explains the restart; in the `CountingPort` run the vent's chunk column is outside the frames from about tick 33 to tick 47, and its log shows the vent unexamined there. |
| T-M1-02 | Rocket entity in `ASCENT` at (10.5, 64, 0.5); listener fixed at (0.5, 64, 0.5). From tick 1 the rocket moves +1 block per tick for 40 ticks, then −1 per tick; it stays tracked and emits no input. | Stop at tick 23 by the per-tick hard stop. Restart at the first selection run with distance ≤ 30: selections run at 23 (stop), then every 10 ticks, so the start is at tick 63 (distance 27). The 10-tick trigger lag for moving entities is recorded, not hidden. |
| T-M1-03 | As T-M1-02, but a leave input at tick 30 and a join input in `ASCENT` at tick 50 with a new entity object of the same UUID. | Entry removed at tick 30; re-admitted at 50; starts at the first selection with distance ≤ 30; never two instances (`RecordingPlayback`). |
| T-M1-04 | Playing vent; at tick 5 a new active block-entity object replaces it at the same position, with no input. | Exactly one stop at tick 5; start of the new binding no earlier than tick 6 and no later than 5 + 2T (T from `CountingPort`); never two instances; no same-key stop and start in one publication. |
| T-M1-05 | Retained vent 50 blocks away; at tick 10 its chunk unloads with no input; at tick 40 it reloads (active, no input); at tick 41 the listener teleports next to it. | Removed at a tick in 10..25 (`ReadLog`: read at that tick, unloaded); offered again by discovery and started by tick 41 + 2T. |

## 2. M2: rediscovery without an overflow flag (A0)

| ID | Case | Expected |
| --- | --- | --- |
| T-M2-01 | R2's M2 trace, buffer-correct. Active vents at (x, 64, 0), x = 10..1,109, inputs delivered 100 per tick in ascending x on ticks 0..10; listener at (0.5, 64.5, 0.5). Tick 20: vents x = 10..409 report inactive (400 inputs). Tick 30: listener teleports to (1,100.5, 64.5, 0.5). No inputs afterwards. | After tick 10: registry x = 10..1,033 (`admission_dropped` 76, x = 1,034..1,109). After tick 20: 624 entries. `CountingPort` charges at tick 30 onward are non-zero, although no drop happened after tick 10 and the registry is below 768 (the condition that cleared C2's flag). By tick 30 + 2T, `RecordingPlayback`'s set equals `Oracle`'s nearest 32: x = 1,078..1,109. For this fixture W ≤ 75 per frame, so T = 1 and the bound is tick 32. |
| T-M2-02 | No overflow anywhere. One active vent at (5, 64, 0) whose initial-state input is suppressed by the fake binder. | Offered by discovery and playing by tick 2T; with discovery disabled it never plays (control that documents the dependency). |
| T-M2-03 | 600 activation inputs for 600 distinct vents inside the cube in one tick. | 512 applied, 88 dropped at the buffer (`inputs_dropped_buffer` 88); all 600 in the registry by 2T, T = ceil(W / 128) with W from `CountingPort`; the 32 played equal `Oracle`'s nearest 32. |
| T-M2-04 | Long run: 2,000 active vents on a 200 × 10 grid, listener walking the grid at 1 block per tick for 3,000 ticks, 10 % of inputs randomly dropped. | At every tick where the listener and sources were stable for 2T + 10 ticks (T from the worst frame so far), the playing set equals `Oracle`'s nearest 32. |

## 3. M3: progress-preserving discovery (A0)

| ID | Case | Expected |
| --- | --- | --- |
| T-M3-01 | R2's M3 trace. The first frame column holds 300 inactive vents, then (in `CountingPort` order) one active vent whose input was dropped. Listener oscillates by +0.5 / −0.5 block on alternate ticks within the same column set for 100 ticks. With the pass starting at tick 1: `CountingPort` distinct-examined count is 128 after tick 1 and 256 after tick 2; the active vent (the 301st entry) is examined and offered in tick 3 and plays at tick 3's publication. Control with C2's restart rule: 128 distinct entries over 100 ticks (R2 F3). |
| T-M3-02 | Frame with W = 1,000 (T = 8) at the origin; at the pass's third tick the listener teleports 1,000 blocks; an active, never-offered vent is beside the new position. | The old frame finishes in the pass's 8th tick (each old entry examined exactly once); the next pass's frame is at the new position and starts on the following tick; the vent is offered within that pass's T' ticks. |
| T-M3-03 | One column with 1,000 entries; the only active vent is at ordinal 999. | Offered in the pass's 8th tick (ceil(1,000 / 128)); charge ≤ 128 every tick. |
| T-M3-04 | During a pass, 50 entries are added and 50 removed, some ahead of and some behind the cursor. | Entries present for the whole pass examined exactly once; added or removed entries at most twice; every tick's charge ≤ 128 (`CountingPort`). |
| T-M3-05 | Port fixture that must re-walk an unordered collection from its start to resume, charging the skipped entries (P2-a). | Conformance check reports a P2-c failure for a column of 1,000 entries (no new entry examined once the skip cost exceeds 128). This is a negative fixture for the port test, not a scheduler failure. |

## 4. M4: fair validation (A0)

| ID | Case | Expected |
| --- | --- | --- |
| T-M4-01 | R2's M4 trace. 1,023 entries present before tick 1 (installed through the test hook, queue in comparator order). At tick 2 an entry E is admitted that ranks last and whose object is already inactive (no input). | `ReadLog`: E first read at tick 17 (= 2 + 15) at the latest; E removed at that tick. Control with C2's snapshot: tick 32 (R2's arithmetic). |
| T-M4-02 | 1,000 ticks; registry kept near 1,024 by random admissions, evictions and removals; 5 % of fake objects per tick flip inactive or unload with no input. | For every entry, the gap between admission and first read, and between consecutive reads, is ≤ 16 ticks (`ReadLog`); every no-input invalidity is removed within 16 ticks of its start (`FakeWorld` vs. test hook). |
| T-M4-03 | Full registry; one entry receives an active input every tick with the same binding. | Its queue position does not reset; read at least once in every 16 ticks. |
| T-M4-04 | Single-player pause for 50 ticks with 600 entries. | No validations while paused; the 16-tick windows count scheduler ticks only. |

## 5. L2: work accounting (A0)

| ID | Case | Expected |
| --- | --- | --- |
| T-W-01 | 1,024 entries, 512 inputs per tick, a dense frame, 32 playing, for 1,000 ticks. | Per tick: `CountingPort` traversals ≤ 128 and equal `discovery_charged`; `ReadLog` reads ≤ 96 and equal `validations_playing + validations_queue`; `merge_sorts` ≤ 1; `CountingComparator` ≤ 28,864 (one sort of 1,664 at 11 comparisons per element, plus a 32-element heap over 1,024 at 10 per offer, plus 320 to order the 32 selected; this is a derived proposal bound, to be confirmed by the reviewer); `RecordingPlayback` ≤ 32 stops and ≤ 32 starts. |
| T-W-02 | Full registry; the same 512 admissions delivered in one tick versus spread across 512 ticks. | Comparator calls per tick never exceed T-W-01's bound; no tick makes more than one sort, independent of the number of admissions. |
| T-W-03 | Registry at 900; 400 inputs for distinct keys (no buffer overflow); three shuffled arrival orders. | Identical registry contents and identical published commands. This checks P §3.3's structural property only; the W01 oracle replacement remains with R2 M5's owner. |

## 6. Reset and stamping (A0)

| ID | Case | Expected |
| --- | --- | --- |
| T-R-01 | Reset (new Level object) in the middle of a discovery pass with 300 entries queued. | All loops stopped; registry, queue, frame and cursor empty (test hook); next pass starts at the new position. |
| T-R-02 | Conditional order fixture: an input stamped with the new Level object is buffered before that tick's reset; an input stamped with the old Level object is buffered too. | New-Level input kept and admitted; old-Level input dropped (`stale_level_inputs` 1). This shows the rule; it does not show that Forge delivers inputs in this order. |
| T-R-03 | `Clone` with the same Level object; buffered same-Level input present. | Registry cleared, generation counter + 1; the input kept and validated before any start. |

## 7. Accepted hard limits (A0 property)

| ID | Case | Expected (`Oracle` at every publication) |
| --- | --- | --- |
| T-H-01 | 10,000 ticks; 200 sources (40 moving rockets); random activity flips with 20 % of inputs dropped; random listener walk with occasional teleports. | ≤ 32 active instances; every active instance's source within squared distance 1,024 at that tick; at most one instance per key; no start for a source beyond squared distance 900. |
| T-H-02 | After T-H-01, freeze world and listener with no source in the band 30-32 blocks. | Within 2T + 10 ticks the playing set equals `Oracle`'s nearest 32 active, valid, loaded sources within 30. |

## 8. Port conformance and real client (V1)

| ID | Case | Expected / evidence |
| --- | --- | --- |
| T-P-01 | Real P2 adapter on Forge 47.4.10; a column with 4,096 vents, a frame with mixed entities, and placement or breaking during a pass. | Per-tick charge ≤ 128 with an independent counting wrapper; P2-b, P2-c and P2-d hold; worst measured W and the resulting T recorded; client tick time recorded. Required before any source task relies on §3's latency. |
| T-P-02 | Real P1 and P3: block-entity replacement, chunk unload and reload, entity leave and rejoin. | P3 never loads a chunk or creates an object (instrumented); stamps are the Level object. |
| T-V-01 | Walk 40 blocks away from one running vent and back, with no state change at the vent. | Audible within 30 blocks again (R2 M1 regression on a real client); recording and listening note attached. |
| T-V-02 | Teleport into a dense base whose sources were dropped earlier in the session. | Nearest loops audible within the measured 2T + 1 ticks. |

## 9. Draft-02 case mapping

| C2 TEST-DESIGN-02 case | Status after this leaf |
| --- | --- |
| S04, S05 | expected "stops" stays; add "entry retained" (T-M1-01) |
| S06 | unchanged here; its 0.5-step timing is a transferred oracle question (P §8) |
| R04, R05, R06 | superseded by T-M2-01..04 |
| V02 | superseded by T-M4-01..04 |
| W02 | recovery path is discovery (T-M2-03) |
| W05 | superseded by T-W-01 |
| all other C2 cases | untouched; W01, O03 and S10 stay with R2 M5's owner |

## 10. Executed checks

None of these cases has been executed. No Java, JVM, Gradle, Python, Git or hashing command was
run for this leaf; see [LIFETIME-HANDOFF-03](LIFETIME-HANDOFF-03.md).
