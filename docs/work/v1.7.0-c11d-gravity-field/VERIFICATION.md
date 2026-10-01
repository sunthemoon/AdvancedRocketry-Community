# V170-GRAV-01 (C11d) area gravity field controller

Date: 2026-10-02. Scope: ADR-058, the area gravity field controller, its
consent list and its place in the player gravity hook. No release Gate is
claimed.

Base: `c618064` (the C11c evidence commit). Commits: `ea95ffe`, `593d8c6`,
`c76af8e` (provenance record) and `6ab7786` (development description) on
`codex/v1.7.0-endgame-systems`. The evidence run used `6ab7786`. The
[implementation log](../v1.7.0-implementation-log.md) records the decisions,
including the player-aware field layer that refines ADR-058 §4 for the C11
review.

## Delivered

| Area | Contract | Result |
|---|---|---|
| Settings | ADR-058 §2 | Radius 2..16 (step 1, default 8), multiplier 0.10..2.00 g (step 0.05, default 0.50 g), 50,000 FE buffer, 1,000 FE per tick input |
| Box and clipping | §2 | Cube of radius r, clipped to the station region; an empty clip is `FIELD_OUTSIDE_STATION`; the C10 box vectors match |
| Activation | §3 | Switch, owner, running, redstone, station (`STATION_OWNER_REQUIRED`), clip, chain steps 2–6 with `ENTITY_GRAVITY`, upkeep, index; re-checked on settings changes and every 200 ticks; an unpaid tick drops the field |
| Index | §4 | Chunk buckets, ≤ 16 per chunk and ≤ 4 per owner per chunk (`FIELD_DENSITY`), 8 per owner, 256 per Level, 1,024 per server (`ACTIVE_LIMIT`), all COMMON values that can only be lowered; cleared by the switch, Level unload and server stop; removed on unload and removal |
| Lookup | §4 | Field, then station region, then Level profile; smallest box, then the lower canonical ID; the C10 winner vectors match |
| Consent | §5 | Station fields affect everyone, capped at 1.00 g; elsewhere only the owner and players who trust the owner; `/arce endgame field trust|untrust|trusted` from the player's own source; ≤ 32 owners in persisted player data; the C10 consent and jump-height vectors match |
| Menu and visuals | §6 | Buttons for start, stop, redstone, radius and multiplier; view with settings, upkeep and field size, coordinates only with detail; update tag `active`, `r`, `m`; at most 4 particles per tick |
| Audit | §6 | Activation, deactivation with code, settings with old and new values |
| Content | ADR-054 §16 | `gravity_field_controller` recipe (advanced circuit, ender pearl, machine casing), model, loot, tool tags, translations |

## Commands actually executed

Windows host, Java `C:/Program Files/Java/jdk-17.0.7`, Gradle 8.8.

| Command | Result |
|---|---|
| `gradlew clean build test runData runGameTestServer --no-daemon` at `6ab7786` | Exit 0, 289 s. **303 required GameTests** passed (301 earlier, 2 gravity). `git status --porcelain --untracked-files=no` after DataGen: empty |
| `gradlew test --rerun --no-daemon` | Exit 0, 117 s. **1,290 JUnit tests / 238 suites**, 0 failures, errors or skips |
| API jar against the C11c run | SHA-256 `24b6527e…5218ab` in both: no public API change |
| `python -B scripts/validate_repository.py --require-approved-identity` | Exit 0, 45 passed (after packaging) |
| `python -B scripts/validate_v1plus_planning.py` | Exit 0 (after packaging) |
| `python -B scripts/validate_bootstrap_provenance.py` | Exit 0 |
| `python -B -m unittest tests.test_v1plus_planning` | Exit 0, 15 tests (after packaging) |

The full build log has **18 ERROR lines and 0 FATAL**, the same intentional
failure-injection set as the earlier runs.

Development GameTest runs before the commits are kept in `root-checks.zip`
under `attempt-01-failed`: a test named a player in a command on a server
without a profile cache (a crash of the test server); non-operator players
may not use the selector the test switched to; a forked `/execute` reports
its fork count rather than the command's result; a field box that crossed
the build height was correctly refused; and a settings click inside the
10-tick intent spacing was correctly refused. Each was a test fixture
problem; the production code was unchanged by them.

## Tests added

- JUnit: boxes, clipping, upkeep, settings ranges, winners (nested, equal
  volume in canonical string order, outside, inclusive boundary), consent,
  jump-height thresholds, index buckets across a chunk corner, density,
  owner and server caps, replacement, Level clear; config value count and
  ranges.
- GameTests: on a planet the owner and a trusting player get the field, a
  stranger keeps the Level gravity, `/execute as` cannot consent for another
  player, untrust takes effect, the list survives a respawn, an unpaid tick
  drops the field, a settings change re-activates at once, a zone and the API
  event refuse it, the switch restores gravity and unloading removes it; on a
  station a member's field is refused (`STATION_OWNER_REQUIRED`) and a member
  fails `MANAGE_STATION`, the station owner's 2.00 g field gives every player
  1.00 g inside the clipped box, and an ownership transfer deactivates it.

## Not verified

- Spawn protection on a real dedicated server (the C11a unit test covers the
  square).
- Relog on a real client (the modifier is transient, so nothing is saved).
- Lookup cost with 256 fields and 20 players (C13).
- Particles and the screen on a real client (V1 is `[H]`).

Next: the independent implementation review of C11 (C11a–C11d).
