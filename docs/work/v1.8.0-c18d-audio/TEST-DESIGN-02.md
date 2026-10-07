# CL18D-AUDIO-02: test design draft 02

Date: 2026-10-08. Status: **planned cases only; none executed**. Derives from
[CONTRACT-02](CONTRACT-02.md) ("C§n"); replaces [TEST-DESIGN-01](TEST-DESIGN-01.md) as the
proposal, which stays unchanged.

Levels: **A0** pure JUnit on the Minecraft-free scheduler with a fake playback and fake objects;
**A1** GameTest for server producers; **V1** one real client (listening, reload, motion);
**V2** two real clients. Positions are in blocks; the listener starts at the origin unless stated.
"Commands" means the ordered list of stop and start calls the scheduler publishes in one tick.
Expected values were derived from the C§4 rules by hand.

## 1. Distance, selection and ordering (A0)

| ID | Case | Expected |
| --- | --- | --- |
| S01 | one active source at (30, 0, 0), not playing | starts (squared distance 900) |
| S02 | one active source at (30.01, 0, 0), not playing | does not start |
| S03 | a playing loop; source moved to (32, 0, 0) | keeps playing (squared 1,024) |
| S04 | a playing loop; listener moves so the distance becomes 32.01 | stops in that same tick |
| S05 | a playing loop at 31.5; listener walks away 3 blocks per tick, no events | stops in the first tick whose distance exceeds 32; never one tick later |
| S06 | non-playing source at 31; listener walks from 31 to 29 in 0.5-block steps | no start while above 30; starts in the tick it reaches 30 |
| S07 | 40 active sources at (0.5·k, 0, 0), k = 1..40 | k = 1..32 play; k = 33..40 silent |
| S08 | 33 active sources of one event, all at squared distance 50 (points from x²+y²+z² = 50) | the 32 with the smallest (x, y, z) play; the one with the greatest (x, y, z) is silent |
| S09 | two sources at equal distance, events `gravity_controller` and `air_hiss_loop`, only one slot left | `air_hiss_loop` gets the slot (ASCII order) |
| S10 | rocket entity and a block source at equal distance, one slot left | the block source gets it (blocks before entities) |

## 2. Registry, admission and re-offer (A0)

| ID | Case | Expected |
| --- | --- | --- |
| R01 | registry full (1,024); new source at equal distance to the last entry with a smaller event ID | admitted; last entry evicted; `overflowed` true; `dropped` 1 |
| R02 | as R01 with a larger event ID | new source dropped; `dropped` 1 |
| R03 | 1,100 active vents at (x, 64, 0), x = 10..1,109, offered with the listener at the origin | registry holds x = 10..1,033; x = 1,034..1,109 dropped; `overflowed` true |
| R04 | R03, then the listener walks to (1,100, 64, 0) with no further source events | after the sweep pass covering the new cube completes (at most ceil(objects in the cube / 128) ticks after arrival), the playing set is exactly x = 1,078..1,109 |
| R05 | R04, then 400 of the vents switch off with their callbacks delivered (registry at most 768) and one more full pass runs without a drop | `overflowed` clears; the sweep stops examining. While 1,100 sources stay active the registry stays full and the sweep keeps running at 128 per tick |
| R06 | a source dropped at 257 blocks, then the listener stands next to it | it plays after the sweep reaches its chunk, without any state change at the source |

## 3. Validity and session (A0 with fake objects; V1 where noted)

| ID | Case | Expected |
| --- | --- | --- |
| V01 | playing source's object state set inactive without a callback | loop stops at that tick's publication; entry removed |
| V02 | non-playing entry made inactive without a callback | removed within 16 ticks |
| V03 | block entity replaced at the same position by a new object, no callback | validation sees a different object; stop and remove |
| V04 | chunk of a playing source unloaded, no callback | stop within one tick |
| V05 | rocket entity removed, no leave event | stop within one tick |
| V06 | new client Level object for the same dimension key (reconnect, respawn) | all ARCE sounds stop; registry empty; generation + 1 |
| V07 | an offer carrying the previous generation after V06 | ignored |
| V08 | `LoggingOut`, then `Clone` | each stops everything and increments the generation |
| V09 | (V1) dimension change through a portal near an active vent | the vent loop stops on the change; no sound from the old Level afterwards |

## 4. Ingestion and aggregate work (A0)

| ID | Case | Expected |
| --- | --- | --- |
| W01 | 300 activations and 300 removals for 600 distinct keys in one tick; run three times with shuffled order | identical commands each time; `selection_runs` 1 for that tick |
| W02 | 600 events in one tick | 512 applied, 88 dropped; `overflowed` true; the sweep later re-offers the active ones among the 88 |
| W03 | far source activation followed in the same tick by a nearer activation that fills the last slot | the far source is never started (no start/stop churn) |
| W04 | one key: active true then false in one tick; then false then true | first: absent, no command; second: present and selected |
| W05 | 1,024 entries plus 512 events per tick for 1,000 ticks | per tick: `events_applied` ≤ 512, `validations` ≤ 96, `sweep_examined` ≤ 128, `selection_runs` ≤ 1, `starts` ≤ 32, `stops` ≤ 32 |
| W06 | listener moves 0.5 block per tick for 20 ticks, no events, no removals | `selection_runs` 10 (every second tick, one block of movement) |
| W07 | a scheduler call from another thread | dropped and counted; no state change |

## 5. Reload and resources (A0; V1)

| ID | Case | Expected |
| --- | --- | --- |
| L01 | reload begins with 10 loops playing | all stop; no start while `reloading` |
| L02 | our apply completes 30 ticks after the start; the overlay remains for 5 more ticks | first start in the first publication after the overlay is gone |
| L03 | apply completes but vanilla sound reload is later (fake ordering) | no start before both are complete (A-OPEN-11 confirms the real order) |
| L04 | an event whose sound resolves to nothing in this resource generation | never started; one log line |
| L05 | 12 unavailable synthetic event IDs in one generation | 10 log lines, then a counter of 2 |
| L06 | (V1) F3+T resource reload near a running gravity controller | the loop stops during reload and resumes after; no duplicate instance |

## 6. One-shots (A0 with a fake sound lifetime; V1)

| ID | Case | Expected |
| --- | --- | --- |
| O01 | railgun launches: 4 at tick 0, 4 at tick 1, 1 at tick 13; sound lasts 30 ticks | visual grants 9; bangs started 8; the ninth bang dropped (8 instances still active at tick 13) |
| O02 | as O01 with a 10-tick sound | bangs started 9 |
| O03 | 4 bangs at tick 0 and 4 at tick 1 with a 200-tick sound; a launch at tick 40 | the cut-off stops the four tick-0 bangs at tick 40 before admission; the new bang starts; 8 active afterwards |
| O04 | 8 visual slots busy, a ninth launch in the same tick | no visual, no bang |
| O05 | launch with the listener at 33 blocks | no bang |
| O06 | `EVENT_ARRIVAL` | no bang (A-OPEN-6) |
| O07 | mushroom flashes accepted 100 ticks apart | two shocks; a cooldown-refused flash plays nothing; with `electricMushroomFlashes` off nothing |
| O08 | fixture with flash cooldown 0 and a 30-tick shock, two flashes 5 ticks apart | second shock dropped (cap 1) |
| O09 | 32 loops, 8 bangs and 1 shock active; another launch | 41 instances; the bang is dropped |

## 7. Producer inputs (A1 unless noted)

| ID | Case | Expected |
| --- | --- | --- |
| I01 | vent in each `VentOperatingStatus` | `LIT` true only for `ACTIVE`; loop only then |
| I02 | gravity controller activate, admission refused, deactivate | update tag `active` true, false, false; a block update is sent on each change only |
| I03 | rocket in each `RocketFlightState`; a corrupt state string | loop only for `ASCENT` and `DESCENT`; corrupt string decodes to `FAILED_RECOVERABLE`, silent |
| I04 | laser drill: running with status OK; running with each non-OK status; not running; status changes from OK to a stop code | `active` true only in the first; a false update is sent on the change; a running blocked drill is silent |
| I05 | laser drill chunk unloaded on the server | no `active` update is sent (the producer's existing behaviour); the client stops the loop through its own unload (V04 / V1) |
| I06 | railgun launch and claim | block events 1 and 2 posted once each |
| I07 | rolling machine processing today | no client input exists; silent; becomes testable after the Root processing state |
| I08 | (A1 + client hook) chunk arrives with an active vent, drill or controller; update tag before and after the block entity's load | offered exactly once; playing within one publication when within 30 blocks |
| I09 | (V1) rocket enters tracking range already in `ASCENT` | loop starts on join |

## 8. Assets and registration (A0 resource checks; per-file evidence)

| ID | Case | Expected |
| --- | --- | --- |
| P01 | every registered event | has a definition whose OGG exists and is listed in provenance as `CLEARED` import or NEW |
| P02 | `lathe` and `rolling_machine` | two IDs; one shared file allowed |
| P03 | the five rejected silent events | not registered |
| P04 | subtitles | keys present in `en_us` and `zh_cn` |
| P05 | loop definitions | `attenuation_distance` 32, `stream` false |
| P06 | NEW files | provenance record time precedes the first authoring artefact recorded by the author; a commit date alone is not accepted |
| P07 | `ui_select` | v0.1.0 import unchanged; not required to have a new origin finding |
| P08 | positional files | Vorbis identification header channel count 1 (stdlib header parse) |
| P09 | functional audio | non-silent waveform check plus a manual listen record per file |
| P10 | a registered ID removed in a fixture | the permanence check fails (ADR-061 §1.4) |

## 9. Real clients (V1, V2)

| ID | Level | Case | Expected |
| --- | --- | --- | --- |
| C01 | V1 | walk from 40 to 0 and back past one active vent | silent until 30, audible inside, stops just past 32 |
| C02 | V1 | 40 active machines in a room | 32 nearest audible; walking changes which |
| C03 | V1 | railgun fired 9 times quickly | at most 8 bangs overlap |
| C04 | V1 | pause in single player; volume 0 | sounds pause; muted loops still capped |
| C05 | V1 | logout and reconnect to the same world | no stale loop; loops resume for active sources |
| C06 | V2 | two clients near one launching rocket | each hears its own loop; no extra packets beyond existing entity data |
| C07 | V1 | client tick time with W05 load | recorded in the performance record |

## 10. Executed checks for this draft

None of the cases above has been executed. The only checks run for this draft are the static
text, hash and link checks recorded in [HANDOFF-02](HANDOFF-02.md).
