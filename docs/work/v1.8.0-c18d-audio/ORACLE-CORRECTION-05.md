# CL18D-AUDIO-ORACLE-05: bounded observation corrections

Date: 2026-10-08. Milestone: v1.8.0 / C18d. Status: **proposed, not frozen, not reviewed**.
Author: Claude, delegated worker (not Root, reviewer or approver), under TASK-05 at
`D:/GitHub/arce-v180-claude-draft-revisions-20261008/docs/work/v1.8.0-c18d-audio/TASK-05.md`.
Fixed base `e7c1f7b34b25b5ea954e62bed2e7df1a54f52d17`. Companion:
[ORACLE-HANDOFF-05](ORACLE-HANDOFF-05.md).

Cited: "S4" = [START-READ-PROPOSAL-04](START-READ-PROPOSAL-04.md); "H4" =
[START-READ-HANDOFF-04](START-READ-HANDOFF-04.md); "L3" and "LT" = LIFETIME-PROPOSAL-03 and
LIFETIME-TEST-DESIGN-03; "RV" =
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18d-audio-start04-independent-20261008-01/reviewer-01/REPORT-01.md`.

Scope: RV SR04-M1, M2, M3 and L1 only. This successor amends S4 section 4 and supersedes two H4
statements; S4, H4 and all sealed records stay unedited. Unchanged: the 32-block hard stop each
tick, the 32-loop cap and every S4/L3 bound (reads 32/64/64/128, total 288; 128 charge units;
64-entry heap). No runtime mechanism, X/Y option or numeric policy is selected. Still open: R3 M2
throughput, R3 M4 moving source and merge trigger, R3 L1 mapping, P2/native port, assets and
whole-contract consolidation.

## 1. Scheduler-owned versus independently observed

| Scheduler-owned (compared, never trusted) | Independently written in A0 |
| --- | --- |
| `validations_playing/queue/prestart/discovery`, `prestart_rejected` | `ReadLog`, written by the P3 fake at each purpose entry point |
| `discovery_charged` and the five `discovery_*` kind counters | `ChargeLedger` and the examination log, written by the P2 fake (`CountingPort`) |
| `discovery_offers`, `starts`, `stops`, registry test hook | `AccessLog` (F1), `RecordingPlayback`, `Oracle` over `FakeWorld` |

Equality between a counter and a log that one faulty path can skip together is not evidence.
Each corrected invariant has an independently written side.

## 2. SR04-M1: discovery reads against independent examinations

**F1.** Bound objects are opaque handles. Every read of validity state (binding cleared, removed,
Level, loaded, block entity at position, active input) goes through a `FakeWorld` accessor that
appends (tick, object, field, mediator) to `AccessLog`; the mediator is the P3 fake purpose whose
call is active, else `NONE`. The scheduler under test has no other route to that state.
**F2.** `CountingPort` logs each examination (tick, entry, known emitter yes/no). Copies and
resume skips are charges, not examinations.

Replacing S4 I2:

- **I2-a** per tick, each purpose count in `ReadLog` equals its counter and meets its A1 bound
  (kept; insufficient alone).
- **I2-b** per tick, `DISCOVERY` reads match `CountingPort`'s known-emitter examinations one to
  one (same object, same tick). This is S4 A2 option Y; option X would replace it and stays open.
- **I2-c** `AccessLog` has no `NONE` entry; each `DISCOVERY`-mediated access is on an object
  examined in that tick.
- **I2-d** `discovery_offers` equals the examined known-emitter objects that `Oracle` finds valid
  and active in that tick; S4 A2's `discovery_offers <= validations_discovery` is kept.

T-A-01's figures meet I2-b: 71 and 79 examinations, all vents, give 71 and 79 reads.

| ID | Case | Expected |
| --- | --- | --- |
| T-A-03 (replaced) | Test-only defective scheduler: five valid, active vents examined through `CountingPort` in one tick, evaluated by reading the handles directly (no P3 call, no read counter), offered and counted as offers. | I2-a passes (0 = 0): this reproduces SR04-M1 and is recorded, not counted as detection. I2-b fails (0 reads, 5 examinations); I2-c fails (at least 5 `NONE`); A2's inequality fails (5 > 0). |
| T-A-03b | Same path, but it re-evaluates five bindings it already holds, without the port. | I2-b passes (0 = 0); I2-c fails; I2-d fails if offers are counted. |

Limit: an offer made with no validity access and no counter is visible only by its effects (I1
for starts, `Oracle` for admitted invalid objects), not by I2.

## 3. SR04-M2: T-A-02 applies A3 to T-W-01

| LT T-W-01 row | Under T-A-02 |
| --- | --- |
| `CountingPort` traversals <= 128 and equal `discovery_charged` | **Removed.** Per tick (I3): `ChargeLedger` sum equals `discovery_charged`, <= 128; each kind equals its counter; traversals equal the `TRAVERSE` count, a component <= 128. Traversal equals the total only when the other four kinds are zero. |
| `ReadLog` reads <= 96, equal playing + queue | I2-a..d; `PLAYING` + `QUEUE` <= 96; all purposes <= 288 |
| comparator figure | open (S4 A4 heap) |
| `merge_sorts`, starts, stops | unchanged |

Reason: T-A-01 has traversal 71 against total 128 in tick 1 and 79 against 116 in tick 2 (RV
re-derived both); a correct mixed-kind port fails the old equality.

## 4. SR04-M3: isolating PRESTART in T-S-04 and T-S-06

Setup **Q64**: valid, active vents V0..V63 at blocks (-40, 64, j), j = 0..63, `RETAINED`,
installed by the test hook at the head of the validation queue before tick `t`. Their squared
distance exceeds 900, so they are ineligible; step 4's 64-key slice reads exactly them at `t` and
re-appends them. Discovery uses LT T-M1-01's empty port (no examinations). Listener at
(0.5, 64.5, 0.5). Ranks use C2's comparator (same event, then block x, y, z ascending).

| ID | Fixture | Expected at `t` |
| --- | --- | --- |
| T-S-04 | Q64; then P1..P31 at (0, 64, k + 1), k = 1..31, and P at (32, 64, 0), all `PLAYING` with `RecordingPlayback` instances (P at distance 32 ranks after P31 by x); then S at (0, 65, 0), `RETAINED`, distance 1. S's block entity is replaced with no input before `t`; the hook sets the last selection to `t - 10`. | Reads: `PLAYING` 32, `QUEUE` 64 (V only), `PRESTART` 1 (S, invalid). S removed; `prestart_rejected` 1; no stop, no start. Control with L3 rules: P stops, S starts, I1 fails. |
| T-S-06 | Q64; nothing playing. A buffered active input for vent N at (0, 64, 2), stamped with the current Level; N's block entity is removed in `FakeWorld` before the end-of-tick handler, with no end input. | N admitted and appended behind V63; `QUEUE` 64 (V only); `PRESTART` 1 (N, invalid); N removed; no start. |
| T-S-04-Q, T-S-06-Q | The same without Q64 (RV's literal reading). | S or N rejected by its `QUEUE` read in step 4; no `PRESTART` read; no start; P keeps playing. Shows the QUEUE path only. |

S2's predicate is unchanged; T-S-01 and T-S-03 keep their queue order.

## 5. SR04-L1: superseded H4 statements

| H4 statement | Corrected statement | Evidence and limit |
| --- | --- | --- |
| section 2: numbered Read count "equals the `lines` value" | Each of the twelve public Read results ends with an empty numbered line, so its count is `lines` + 1. All twelve texts were read in full before the first Write. | RV SR04-L1, normalized public Read comparison (CHECKS-05, 12/12). The receipt field `all_twelve_observed_before_first_write: false` is, per RV, an observer artifact. Not re-verified by this author. |
| section 3: one handoff Edit, "only edit", "No other edit" | Public records show two Edits to H4 (sequences 335 and 337). | RV SR04-L1; not re-verified by this author. |

## 6. Limits

All expected values are hand derivations; nothing was executed. Q64 coordinates and ranks need
independent re-derivation. No claim about real Forge or vanilla order or sound-engine behaviour.
Passes no build, listening, V1, V2 or v1.8 G0-G9 Gate.
