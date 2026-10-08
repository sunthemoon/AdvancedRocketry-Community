# CL18D-AUDIO-START-READ-04: playback-start validity and read accounting

Date: 2026-10-08. Milestone: v1.8.0 / C18d. Status: **proposed, not frozen, not reviewed**.
Author: Claude, delegated worker (not Root), under
`D:/GitHub/AdvancedRocketry-Community/docs/work/v1.8.0-c18d-audio/TASK-04.md`. Fixed base
`3a60758387118eb82ed0f7929c1e181983f6e0b7`. Companion: [START-READ-HANDOFF-04](START-READ-HANDOFF-04.md).

Cited as: "L3 §n" = [LIFETIME-PROPOSAL-03](LIFETIME-PROPOSAL-03.md); "LT" =
[LIFETIME-TEST-DESIGN-03](LIFETIME-TEST-DESIGN-03.md); "C2" = [CONTRACT-02](CONTRACT-02.md) and
[TEST-DESIGN-02](TEST-DESIGN-02.md); "R3" =
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18d-audio-lifetime-independent-20261008-03/REPORT-01.md`.

Scope: R3 M1 and R3 M3 only. This amends L3; everything else in L3 and C2 stays as written,
including what R3 found open in it. Preserved unchanged: the 32-block hard stop checked every
client tick (L3 §5.2 step 5) and the cap of 32 simultaneous ARCE loops. No native discovery
mechanism is selected; L3 §5.2's step order is kept and only step 6 is refined.

## 1. Disposition

| R3 finding | Disposition | Where |
| --- | --- | --- |
| M1 retained entry started before its current validity is read | **Proposed fix.** Same-tick start read (S2) with a bounded walk and refill (S3) and a retry trigger (S4). Proof limit: same-tick freshness rests on P3-g and C2 §4.5's main-thread claim, neither proved here. | §2 |
| M3 discovery validity reads missing from the read oracle | **Proposed fix in the specification.** Purpose-tagged reads with per-purpose bounds (A1); discovery reads bounded separately from the 128-unit charge (A2, option Y; option X left to Root); a kinded charge ledger (A3). Proof limit: real counts need L3 P2-f and LT T-P-01. | §3 |
| M2, M4, L1 | Not addressed; open (§5). | - |

## 2. Playback-start validity (R3 M1)

**S1 State meaning.** L3 §2's `RETAINED` becomes "admitted and not playing; validity last read at
an earlier tick". It no longer asserts current validity. `PLAYING` keeps L3 §4's per-tick check.

**S2 Start rule.** A start for key `k` is published in tick `t` only if, in tick `t`'s step 6, a
validity read of `k`'s current binding with purpose `PRESTART` returned valid (L3 §2's predicate:
binding not cleared, not removed, current Level object, chunk loaded, same block entity, active
input true). Earlier same-tick reads (`DISCOVERY`, `QUEUE`) are not reused, so the count is exact.
An invalid result removes `k` by L3 §2's rule and counts `prestart_rejected`; `k` gets no start.

**S3 Walk and refill.** Step 6 keeps a bounded heap of the first **64** eligible entries under
C2's comparator (L3: 32) and walks them in order:

- a `PLAYING` entry is selected without a further read (validated in step 4 and kept by step 5);
- a `RETAINED` entry gets its `PRESTART` read; valid: selected; invalid: removed;
- the walk ends when 32 are selected or the 64 are exhausted. Then every still-eligible
  `PLAYING` entry not yet selected (at most 32, taken from the playing set, not the heap) is
  selected in comparator order until 32 are selected. A loop is therefore never stopped while a
  slot stays empty because of rejections.

Per tick: at most 64 `PRESTART` reads, at most 32 selected, at most 32 starts.

**S4 Retry.** If step 6 removed any entry, the next tick's step 6 runs (L3 §5.2's "an entry was
removed" trigger extended to removals made by step 6 itself). Derived, unexecuted: with no world
or listener change after tick `t`, slots left empty by rejection are filled, as far as valid
eligible entries exist, no later than tick `t + 16` (L3 §4 validates each entry within 16 ticks;
a removal triggers selection in its tick).

**S5 Consequences.**

- L3 §5.1's "a kept input can never revive a stale object" becomes: a kept input may admit an
  entry, but cannot start it without S2's read of the bound object.
- A retained stale binding (replaced block entity, unloaded chunk, other Level object) fails S2
  and is removed instead of started. When the replacement starts stays open (R3 M4, T-M1-04).
- An entry admitted from a buffered input in tick `t` and selected in `t` is read before start.
- No same-key stop and start in one publication (unchanged): S3 never starts a `PLAYING` key.

**S6 Proof limits.** Same-tick freshness assumes nothing changes the read state between the
`PRESTART` read and publication inside one end-of-tick handler. That needs P3-g (§4) and C2
§4.5's main-thread claim for packet handling, which this leaf did not verify. A start command
does not prove that the sound engine plays at that tick (P5, unverified). The 64-entry heap and
64-read cap are numeric proposals for Root under C2 A-OPEN-12.

## 3. Complete validity and discovery accounting (R3 M3)

**A1 Purpose tags.** Every P3 read has exactly one purpose: `PLAYING` (L3 §4, <= 32), `QUEUE`
(L3 §4, <= 64), `PRESTART` (S2, <= 64), `DISCOVERY` (A2, <= 128). Counters
`validations_playing`, `validations_queue`, `validations_prestart`, `validations_discovery`;
P3 reads per tick <= 288. P3 has one entry point per purpose, so a fake logs the purpose itself.

**A2 Discovery reads (option Y, proposed).** Discovery evaluates validity through a `DISCOVERY`
read only for examined objects of a known emitter type (the type test is part of the traversal
unit). These reads are **not** charged to the 128-unit discovery budget and have their own
bound: `discovery_offers <= validations_discovery <= known-emitter examinations in the tick
<= 128`. Y leaves L3's `W`, `T` and every `W`-derived expected value unchanged. Option X (each
read costs 1 unit inside the 128) is left to Root under A-OPEN-12: X changes `W` and `T` and
needs a resumable "read pending" cursor to avoid the atomic multi-unit operation analysed in R3
M2, so X must be decided together with M2.

**A3 Charge ledger.** P2 reports each charged unit with one kind: `TRAVERSE` (an entry examined,
native or in a temporary copy); `COPY` (an entry copied from native storage into a temporary,
covering the native read that produced it); `UNLOADED_COLUMN` (a frame column visited and not
loaded); `EMPTY_COLUMN` (a loaded frame column completed with no entry); `RESUME_SKIP` (an entry
re-walked to re-find the cursor). `discovery_charged` is the sum, <= 128 per tick. Counters
`discovery_traverse`, `discovery_copy`, `discovery_unloaded`, `discovery_empty`,
`discovery_resume_skip`. The counting fake records kinds itself; counters are compared to it.

**A4 Replaced L3 §6 rows.**

| Step | Charge | Bound per tick |
| --- | --- | ---: |
| discovery | A3 kinds | 128 units |
| discovery validity | 1 `DISCOVERY` read per known-emitter examination | 128 |
| validation | `PLAYING` + `QUEUE` reads | 32 + 64 |
| start reads | `PRESTART` reads | 64 |
| selection | <= 1,024 distance computations and offers into a 64-entry heap; walk <= 64 | 1 run |

LT T-W-01's comparator figure assumed a 32-entry heap; it must be re-derived once a heap is
chosen (R3's sort note). A binary heap of 64 needs at most 1 + 2 x 6 = 13 comparisons per offer
(derived, unexecuted). This leaf does not settle that ceiling.

## 4. Affected clauses and planned observations

Affected L3 text: §2 table and rules (S1, S2); §3.2 step and §3.3 item 2 (A2); §5.1 last
sentences (S5); §5.2 step 6 (S3, S4); §6 rows and counters (A1, A3, A4); §7 P2-a (A3 kinds) and
P3 (A1 entry points, plus **P3-g**: a read runs no queued task, event or callback and changes no
state). LT: §0 instruments; T-W-01's read row (replaced by T-A-02); I1 added to T-M1-01..05 and
T-H-01. C2 cases: no further change.

Instruments (A0, added to LT §0): `ReadLog` records (tick, object, purpose entry point, result);
`ChargeLedger` records (tick, kind, column, entry) as reported by the fake port; `FakeWorld` is
ground truth. Global invariants: **I1** for every start (`t`, `k`) in `RecordingPlayback`,
`FakeWorld` says `k`'s bound object is valid at `t`, and `ReadLog` holds a `PRESTART` read of it
at `t` returning valid; **I2** per tick, each purpose count in `ReadLog` equals its counter and
its A1 bound holds; **I3** per tick, each `ChargeLedger` kind equals its counter, sum <= 128.

| ID | Case | Expected |
| --- | --- | --- |
| T-S-01 | R3's M1 trace: retained vents keys 0..99, queue order 0..99, none playing; at tick `t` vent 99 turns inactive with no input and the listener teleports within 30 blocks of vent 99 only; the queue slice reads keys 0..63. | No start of 99; `PRESTART` reads 1; `prestart_rejected` 1; 99 removed at `t`. Control with L3 rules: 99 starts at `t`. |
| T-S-02 | T-S-01 plus vent 98 valid and within 30 blocks. | 99 rejected; 98 starts at `t` (same-tick refill). |
| T-S-03 | 200 valid entries beyond 30 blocks at the queue head; behind them 70 stale retained entries nearest the listener and 10 valid eligible entries ranked after them; none playing. | `t`: 64 `PRESTART` reads, 64 removed, 0 starts. `t+1`: 16 reads, 6 removed, 10 starts. I1 holds. |
| T-S-04 | 32 playing, the last (P) ranked 33; one stale retained entry ranked 1. | Stale entry rejected; P stays selected; no stop of P. Control with L3 rules: P stops, the stale entry starts. |
| T-S-05 | Retained vent whose block entity is replaced with no input; listener comes within 30 blocks. | Old binding rejected and removed; no start for the old object. Start time of the new object not asserted (R3 M4). |
| T-S-06 | Buffered active input stamped with the current Level; the vent's block entity is removed later in the same tick with no end input. | Admitted at merge; rejected by `PRESTART`; no start. |
| T-S-07 | After T-S-03 at `t+1`, freeze world and listener. | Playing set equals `Oracle`'s nearest valid eligible 32 by `t + 16`. |
| T-A-01 | Fake frame of 25 columns, x then z: c0..c2 unloaded; c3..c6 loaded, empty; c7 50 vents copied at column start, then examined (ordinals 0..9 active); c8 100 vents in an unordered collection re-walked from its start to resume (ordinals 95..99 active); c9..c24 loaded, empty. Pass starts at tick 1; no mutation. | Tick 1: `UNLOADED_COLUMN` 3, `EMPTY_COLUMN` 4, `COPY` 50, `TRAVERSE` 71, total 128; `DISCOVERY` reads 71; offers 10. Tick 2: `RESUME_SKIP` 21, `TRAVERSE` 79, `EMPTY_COLUMN` 16, total 116; `DISCOVERY` reads 79; offers 5; `discovery_passes` 1. |
| T-A-02 | LT T-W-01's load with T-A-01's port kinds. | I2 and I3 every tick; reads `PLAYING` <= 32, `QUEUE` <= 64, `PRESTART` <= 64, `DISCOVERY` <= 128, total <= 288. Other T-W-01 rows unchanged except the comparator figure (§3). |
| T-A-03 | Negative control: a scheduler fake that evaluates discovery validity through an unlogged path. | I2 fails (`DISCOVERY` reads < offers): the instrument detects R3's bypass. |

## 5. Kept open

R3 M2 (utilization and latency premise; option X couples to it); R3 M4 (merge-trigger semantics,
T-M1-02 and T-M1-04 timing); R3 L1 (mapping of removed fields and stamps); L3 §9 transfers; C2
A-OPEN-1..12 with their owners, A-OPEN-12 gaining the 64-entry heap, the 64-read cap and the
X/Y choice; P2 mechanism, P2-f, T-P-01/02 (Root). Asset, carrier, reload and one-shot
dependencies are unchanged.

## 6. Limits

All bounds and expected values are hand derivations; none was executed. No claim about real
Forge or vanilla packet, tick, enumeration or sound-engine order. Passes no build, listening,
V1, V2 or v1.8 G0-G9 Gate.
