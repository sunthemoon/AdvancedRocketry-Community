# V170-SEC-01 (C11a) endgame framework verification

Date: 2026-10-01. Scope: the ADR-054 framework that the endgame devices build on.
**No endgame device, menu, intent or network channel is delivered here**; they
start in C11b. No release Gate is claimed.

Base: `9d30892` (the C10 acceptance merged with the F01 fix). Commits:
`cbae6dd`, `2f02886`, `fedcb1d`, `d045e7f` on `codex/v1.7.0-endgame-systems`.
The evidence run used `d045e7f`. The [implementation log](../v1.7.0-implementation-log.md)
records the slice plan, its decisions and its deviations.

## Delivered

| Area | Contract | Result |
|---|---|---|
| Systems, actions, codes, ID order, fixed maxima | ADR-054 §1, §7, §10 | Stable IDs; codes sent and audited by name; the accounted maximum is 2,932,736 B |
| COMMON config | §1, §4, §6, §9 | Eleven values: six switches (physical mining off), intent and selection spacing, endpoint and zone limits |
| `EndgameAuthority` | §3, ADR-058 §3 | Pure matrix over actors, actions and station cases |
| Protected zones | §6 | Names, 4,096-block spans, 16-player allow lists, per-Level intersection |
| Endgame root | §9, §10, §11 (C11 part) | `ManagedSavedDataType.ENDGAME`, strict codec, growth admission, R3-M2 tombstone caps, housekeeping, save epoch, fail-closed load, pre-start validation |
| Protection chain | §5 steps 1–6 | First failure wins; no chunk is loaded; the API event is last |
| API 1.8 | §5.1 | `EndgameEffect`, `EndgameEffectEvent`; `ApiVersions` 1.8 |
| Service | §9–§12 | Chunk-tag observations, MISSING, tombstone settlement, coalesced and barrier flushes |
| Audit | §13 | 512-byte lines, 64 per tick, summaries, 512-line ring |
| Commands | §13 | `status`, `audit`, `zone`, `endpoint list/retire/forget`, `tombstone evict/settle` |
| Content | §16 | `endgame_casing`, `laser_lens`, recipes, loot, tool tags, translations for every code |
| Runtime identity | — | `1.20.1-1.7.0-dev`; [metadata provenance](../../provenance/v1.7.0-development-metadata.md) |

Implementation decisions for the C11 review are in the implementation log: the
tombstone encoding inside `endpoints`, empty `transits` and `elevator_pairs`
until C12, `firstEpochSchema()` and `hasLegacySchema()`, and pins as a no-op
until C12.

## Commands actually executed

Windows host, Java `C:/Program Files/Java/jdk-17.0.7`, Gradle 8.8.

| Command | Result |
|---|---|
| `gradlew clean build test runData runGameTestServer --no-daemon` at `d045e7f` | Exit 0, 325 s. **293 required GameTests** passed (290 earlier, 2 framework, 1 adapter fixture). `:test` executed. `git status --porcelain` after DataGen: empty |
| `gradlew test --rerun --no-daemon` | Exit 0, 140 s. **1,241 JUnit tests / 228 suites**, 0 failures, errors or skips |
| `gradlew apiJar` in a temporary worktree at `9d30892` | Exit 0; classifier SHA-256 `d29f20aa…54a5d`, equal to the v1.6 build |
| `python -B scripts/validate_repository.py --require-approved-identity` | Exit 0, 45 passed (after packaging) |
| `python -B scripts/validate_v1plus_planning.py` | Exit 0 (after packaging) |
| `python -B scripts/validate_bootstrap_provenance.py` | Exit 0 |
| `python -B -m unittest tests.test_v1plus_planning` | Exit 0, 15 tests (after packaging) |

The API classifier comparison (`api-class-comparison.json` in
`root-checks.zip`) finds exactly one changed class, `ApiVersions`, two added
(`EndgameEffect`, `EndgameEffectEvent`) and none removed, as ADR-054 §5.1 requires.

The full build log has **18 ERROR lines and 0 FATAL**, the same intentional
failure-injection set as the C10 run. The runtime JARs change in this slice
(`artifacts.json`).

## Tests added

- JUnit: the authority matrix; ID order against `UUID.compareTo`; zone bounds;
  settings and config ranges; the root rules, caps and housekeeping; codec
  round trip, strict rejections, worst-case record sizes against the
  accounting, epoch and checked-file flush; world-level preservation with the
  new root; chain order and the API event; audit bounds; chunk-tag
  observations and their timing; the API classifier set and consumers.
- GameTests: the chain on a running server (zone, allow list, API veto, an
  unloaded target that stays unloaded, build height); a zone barrier reaching
  the endgame file before success; the compatibility fixture's veto.

## Retained defects in the evidence trail

- The `build.gradle` API task (`fedcb1d`) was committed before its provenance
  record; the record now states it.
- A Python patch through a shell heredoc turned `\n` into a real newline in
  `gradle.properties`; the metadata test caught it and the line was fixed
  before the commit.

## Not verified

- Devices, menus, intents and the device view (C11b onward).
- The dedicated-server spawn square on a real dedicated server: the GameTest
  server is not dedicated, so only the unit test covers it.
- Multiplayer, real clients, performance (C13).

Next: C11b, the laser drill's logical mode with the device base, structure
validation, the intent guard and the device view.
