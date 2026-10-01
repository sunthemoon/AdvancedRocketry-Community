# V170-LAS-01 (C11b) orbital laser drill, logical mode

Date: 2026-10-01. Scope: ADR-055 logical mode and the ADR-054 device parts it
is the first user of. **The physical mode, the laser target, links and beam
visuals are not delivered here**; they are C11c. No release Gate is claimed.

Base: `63e779c` (the C11a evidence commit). Commits: `4387137`, `ea7a744`,
`cf51064` and the fix `454e962` on `codex/v1.7.0-endgame-systems`. The
evidence run used `454e962`.
The [implementation log](../v1.7.0-implementation-log.md) records the slice
plan, its decisions and its deferrals.

## Delivered

| Area | Contract | Result |
|---|---|---|
| `laser_drill_tables` | ADR-055 §2 | Strict schema-1 codec (16 KiB, 32 tables, 0..64 bodies, 1..64 unique entries), raw-byte versions, one default, one table per known body, all-or-nothing reload with the celestial catalog |
| Eligibility | §2 | Own table, else the default for surface-arrival bodies; gas giants, stars and orbit-only planets need their own |
| `laser-v1` | §2 | The n-th `SplitMix64(seed ^ LASERDRL)` output; the C10 reference draws match |
| Operation | §2, §4 | Contract order, cost `100 × energyPercent` FE, output-full retry of the same draw, one operation per interval, active admission and the per-tick cap |
| Built-in tables | §2 | Earth, Moon, Mars, Venus and the default (equal to the reference table) under `src/generated/v1.7` |
| Device root | ADR-054 §2 | Identity on placement or first load, ownership only for connected non-fake placers, strict reads, quarantine re-saved unchanged, inert, unbreakable for non-operators |
| Structures | §2.1 | Bounded validator through a loaded-chunk view, on box changes and every 200 ticks, 8 per tick |
| Rates and counts | §7 | Canonical ID order, round-robin grants; first-come admission kept when limits drop |
| Intents and view | §4 | Guard in contract order with the rate last; S2C `endgame` channel protocol 1; device view ≤ 8 KiB, sent at most every 5 ticks; detail only for allowed viewers |
| Controller | ADR-055 §1, §5, §6 | Block, block entity, menu, screen; lens, 18-slot extract-only output, 200,000 FE input-only buffer (20,000 FE per tick); 3 × 3 × 3 casing pattern; audit of starts, stops, settings, status changes and a summary every 1,200 ticks |
| Commands | ADR-054 §13 | `device inspect`, `device owner`; device status in `status` |
| Content | ADR-054 §16 | `orbital_laser_drill` recipe, model, loot, tool tags, translations |

## Commands actually executed

Windows host, Java `C:/Program Files/Java/jdk-17.0.7`, Gradle 8.8.

| Command | Result |
|---|---|
| `gradlew clean build test runData runGameTestServer --no-daemon` at `454e962` | Exit 0, 328 s. **299 required GameTests** passed (293 earlier, 6 laser drill). After DataGen `git status --porcelain --untracked-files=no` shows only this packet's implementation-log line, written before the run; nothing under `src/` changed |
| `gradlew test --rerun --no-daemon` | Exit 0, 135 s. **1,275 JUnit tests / 235 suites**, 0 failures, errors or skips (the first run's `:test` came from the build cache) |
| API jar against the C11a run | SHA-256 `24b6527e…5218ab` in both: byte-identical, so no public API change in this slice |
| `python -B scripts/validate_repository.py --require-approved-identity` | Exit 0, 45 passed (after packaging) |
| `python -B scripts/validate_v1plus_planning.py` | Exit 0 (after packaging) |
| `python -B scripts/validate_bootstrap_provenance.py` | Exit 0 |
| `python -B -m unittest tests.test_v1plus_planning` | Exit 0, 15 tests (after packaging) |

The full build log has **18 ERROR lines and 0 FATAL**, the same intentional
failure-injection set as the C10 and C11a runs.

Earlier attempts are kept in `root-checks.zip`: `attempt-01-failed` (the
protocol pin test rejected the new channel until the committed table listed
it), `attempt-02-uncommitted-tree` (a passing run on the working tree that
became `cf51064`) and `attempt-03-validator-failed` (a passing Gradle run at
`cf51064` whose repository validation failed: the resource audit reported a
translation key split across a string concatenation in the drill screen as
unlocalized; `454e962` spells out every key, with no other change).

## Tests added

- JUnit: table codec bounds and rejections, raw-byte versions, set conflicts,
  eligibility, all-or-nothing reload, built-in tables; `laser-v1` against the
  reference vectors and the generator; the operation order, retry, cost and
  settings; round-robin grants in string order, lapsing grants, admission,
  the structure box index; the intent order, settlement while disabled, rate
  classes; the device view at its 8 KiB maximum and strict decoding; the
  protocol pin table; config values.
- GameTests: a drill on a station mines Earth, then the Moon after a warp,
  loading no chunk; a full output pauses and retries the same draw; intents
  refused for a FakePlayer, rate, distance, another Level, a stranger, a
  changed device and an unloaded chunk (which stays unloaded); a broken
  structure stops the drill until repaired; nine root defects quarantine,
  re-save byte-identical and remove capabilities, a non-operator cannot break
  it, FakePlayer placement creates no owner; energy input bounds and
  extract-only automation.

## Not verified

- Physical mode, laser target, links, debt and credit, beams (C11c).
- The disabled switch on a running server: the operation order test covers
  `SYSTEM_DISABLED`; changing a COMMON value in a GameTest would write the
  config file.
- The screen layout and translations on a real client (V1 is `[H]`).
- Multiplayer, real clients and performance (C13).

Next: C11c, the physical mode with the `laser_target` endpoint.
