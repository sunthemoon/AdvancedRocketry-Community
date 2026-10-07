# CL18D-AUDIO-01: test design draft 01

Date: 2026-10-07. Status: **planned cases only; none executed**. Derives from
[CONTRACT-01](CONTRACT-01.md) ("C§n" below). Levels: **A0** pure JUnit; **A1** Forge GameTest;
**S1** packaged dedicated server; **V1** one real GPU client with audio output; **V2** two real
clients. Listening and waveform checks are human or tool evidence, not JUnit.

## 1. Pure scheduler cases (A0)

The scheduler is Minecraft-free (C§4.1): inputs are candidate records (source key, event ID,
position, Level key, active flag) plus a listener position and tick; outputs are start and stop
commands. Each case states exact expected commands.

| ID | Setup | Expected |
| --- | --- | --- |
| S01 start | one active candidate at 10 blocks | one `start(source, event)` at the next evaluation |
| S02 stop on inactive | S01, then active false | one `stop(source)`; no restart |
| S03 duplicate update | the same active=true update twice | still one instance; no second `start` |
| S04 stale update | inactive update for an unknown source | no command |
| S05 out of range | candidate at 33 blocks | no `start` |
| S06 range boundary | squared distance exactly 1,024 | plays; 1,025 does not start |
| S07 hysteresis | playing source moves from 31 to 33.9 blocks | keeps playing; at 34.1 blocks stops |
| S08 saturation | 40 active candidates at distinct distances | exactly the 32 nearest start |
| S09 nearer arrival | S08 steady, then a candidate nearer than the 32nd | at the next evaluation the farthest playing source stops and the new one starts; one stop, one start |
| S10 deterministic ties | 33 candidates at equal distance | the 32 chosen by event ID then source key order; reversed input order gives the same set and command order |
| S11 evaluation cadence | 100 ticks with no change | at most 10 evaluations and zero commands after the first |
| S12 Level change | playing sources, then the Level key changes | stop all; candidate set empty |
| S13 disconnect | `reset()` | stop all; candidate set empty; next ticks issue nothing |
| S14 resource reload | `reload()` while playing | stop all; after `reloadDone()` the still-active nearest 32 start again |
| S15 candidate cap | 300 candidates offered | at most 256 retained; farthest dropped; dropped count reported; selection still the nearest 32 among retained |
| S16 cap replacement | full set, offer a candidate nearer than the farthest retained | replaces the farthest; a farther one is dropped |
| S17 unload | source unloaded while playing | stop; not restarted until re-offered |
| S18 one instance per key | the same source key offered with two events | rejected with a diagnostic (no rule exists, C§4.3) |
| S19 paused | ticks while paused | no evaluation, no command |
| S20 bounded work | 256 candidates for 1,000 ticks | work per evaluation bounded by the candidate count; no per-tick sort when nothing changed |

All S cases run twice with input records shuffled; command sequences must be identical.

## 2. Input mapping cases (A0 and A1)

| ID | Level | Check |
| --- | --- | --- |
| I01 | A0 | rocket state mapping: `ASCENT`, `DESCENT` → active; every other `RocketFlightState` value → inactive; an unknown name → inactive (C§3.3) |
| I02 | A1 | the vent's `LIT` follows `VentOperatingStatus.ACTIVE` and changes only on status change (existing behaviour; the test pins it as the audio input) |
| I03 | A1 | gravity controller update tag `active` equals the server's active flag after start and after stop |
| I04 | A1 | laser drill update tag `active` true while running, false after stop and on unload |
| I05 | A1 | railgun posts exactly one `EVENT_LAUNCH` block event per launch; none on a refused launch |
| I06 | A1 | rolling machine processing flag (C§3.10, once Root adds it): true when a process runs, false when paused by a full output, a missing input, redstone, unforming, unloading or completion; sent only on change (count update packets over 200 idle ticks: zero) |
| I07 | A1 | the same flag survives no save: after restart a running machine sends true again from its restored state, an idle one false (S1 for the restart) |
| I08 | A1 | laser gun flag (C18b, once added): true only during server-counted valid use; false on target change, cooldown, item change, logout |
| I09 | A0 | the mushroom flash path plays `electric_shock_small` exactly when it already flashes; switch off → neither |

## 3. Resource and registration cases (A0 over generated data)

| ID | Check |
| --- | --- |
| R01 | every registered ARCE sound event has a `sounds.json` definition and each definition's file exists in the JAR resources (ADR-061 §5.2 validator rule) |
| R02 | no definition points at a file whose SHA-256 is `f33cbdac…3ba3` (the silent placeholder) |
| R03 | `lathe` and `rolling_machine` are two registered IDs; if they share a file, both definitions name the same path and the file exists once |
| R04 | loop definitions have `attenuation_distance` 32 and `stream` false; one-shots as specified |
| R05 | each event has `subtitles.advancedrocketrycommunity.<id>` in `en_us` and `zh_cn` |
| R06 | each shipped OGG has a provenance record: an importer record with a `CLEARED` origin finding, or a NEW record dated before the file's first commit |
| R07 | no event is registered without its file (registration list equals the set of events with approved files) |
| R08 | DataGen twice produces byte-identical sound definitions |
| R09 | client package: no class under the audio package is referenced from common or server code (static import check, existing sidedness rule) |

Negative fixtures for R01, R02, R06 and R07 (a definition with a missing file, a placeholder-hash
file, a file without a record, an event without a file) must each make the check fail.

## 4. Asset evidence (per file, before import)

| ID | Check | Evidence |
| --- | --- | --- |
| P01 | Ogg Vorbis header decodes | tool log with tool name and version |
| P02 | channel count; positional sounds mono | tool log; a stereo legacy file needs a recorded downmix transformation |
| P03 | duration, sample rate, peak and RMS level | tool log; RMS above a silence floor proves audible content (threshold frozen by Root before measuring) |
| P04 | waveform image | PNG in the evidence packet |
| P05 | loop seam: last and first 50 ms joined without a click | listening note and waveform of the joined region |
| P06 | manual listen | listener, date, device, verdict, notes; fits "no louder than a machine hum" for the laser drill |
| P07 | origin finding or NEW pre-authoring record | committed file, reviewer, date |

## 5. Client and multiplayer runs (planned)

| ID | Level | Scenario | Expected |
| --- | --- | --- | --- |
| C01 | V1 | stand next to an active vent, walk to 31, 33, 35 blocks and back | audible at 31; stops beyond 34; restarts inside 32 |
| C02 | V1 | 40 active gravity controllers/vents around the player | at most 32 ARCE loops (debug sound count or log), nearest ones audible |
| C03 | V1 | toggle a rolling machine's input to pause/resume | loop follows the server state within one evaluation (≤ 10 ticks after the update) |
| C04 | V1 | F3+T resource reload while loops play | all stop, then the eligible ones resume; no error log |
| C05 | V1 | change Level through a portal or rocket; disconnect; reconnect | no orphan loop after each step |
| C06 | V1 | rocket launch from 20 blocks | loop during ascent; silence after the rocket leaves |
| C07 | V1 | railgun launch | one bang per launch; nine launches in one second produce at most eight |
| C08 | V1 | stormland rain with the mushroom switch on and off | shock sound only with flashes, at most one per five seconds; none when off |
| C09 | V1 | GUI scale/resolution and sound volume sliders | no effect on scheduling; volume 0 mutes |
| C10 | V2 | two clients near one machine, one far | the near client hears the loop, the far one does not; both see the same state |
| C11 | V2 | one client reconnects while the machine runs | the reconnected client hears it again from the synchronized state |
| C12 | S1 | dedicated server with no client classes | starts; no client audio class loaded on the server |

## 6. Not covered

Server performance is unchanged except for the processing flag updates (I06 counts them). No MSPT
budget applies to client playback; client frame-time impact is part of the V1 performance record of
docs/17 §8.

## 7. Executed checks for this draft

None of the cases above has been executed. The only checks run for this draft are the static text,
hash and link checks recorded in [HANDOFF-01](HANDOFF-01.md).
