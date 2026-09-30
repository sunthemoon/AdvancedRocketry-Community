# V160-SAT-02 (C7b) verification: Satellite Builder, launch of the new kinds and the terminal view

Date: 2026-10-01. Branch `codex/v1.6.0-satellite-resource-missions`, parent
`4b744c8` (C7a). Contracts: [ADR-049](../../decisions/ADR-049-SATELLITE-BLUEPRINTS-AND-ASSEMBLY.md)
§4–§7 and §10, with the proposed revision-4 amendment below. No Gate, candidate
or tag.

## Implemented

- **Satellite Builder** (block, block entity, menu, screen). Slots: chassis,
  primary, six modules, blank chip, package output and a redstone charge slot.
  - The only intent is `ASSEMBLE` through `clickMenuButton`, carrying no item,
    stat or kind. The server re-checks the live player, same Level, 8 blocks,
    loaded chunk, menu binding and catalog generation, and allows one assembly
    per 20 ticks (`RATE_LIMITED`).
  - Refusal order as frozen in ADR-049 §4: `OUTPUT_BLOCKED`,
    `INVALID_COMPONENTS`, `DEFINITION_NOT_FOUND`, `STAT_LIMIT`,
    `REQUIREMENT_UNMET`, `RESEARCH_LOCKED` (lifetime research), `NO_POWER`.
  - Success in one tick: one item per filled slot and 1,000 FE are consumed; a
    new UUID identity (kind, definition, components in slot order) is written to
    the chip and to a `satellite_package` in the output slot. The registry is
    not touched.
  - Automation inserts only matching items and extracts only the package; energy
    is receive-only. The menu preview is server-computed and cached by exact
    content version, catalog generation and energy.
  - Root schema 1 (`schema_version`, `inventory`, `energy`, `last_result`) with
    the ADR-029 preflight bounds, unknown-item quarantine and exactly-once raw
    root carry on removal.
- **Items and DataGen.** The builder item, the generic `satellite_package` and
  eight component items. The v1.6 DataGen writes to `src/generated/v1.6`: the
  builder model, nine item models (vanilla parents only), loot, recipes,
  complete tool tags that supersede the v1.5 copies, the 11 ADR-049 §2
  component files and the four schema-2 kind definitions. Every generated
  component and definition is decoded by the strict runtime decoder before it
  is written. The v0.8 language provider is restored to its frozen code set;
  the codes appended in v1.6 are translated by the v1.6 provider.
- **Launch of the new kinds** (`SatelliteKindLifecycle`, extracted from
  `SatelliteManager`). A non-`data` package launches the satellite idle into the
  selected launch target:
  - replay of a registered identity answers `IDEMPOTENT` before any other check,
    and the terminal consumes the package; another owner, definition or kind
    answers `IDENTITY_CONFLICT`;
  - then the definition of that kind, the orbit body (a launch target, known,
    orbitable, discovered when required), the components re-derived from the
    current catalog, and lifetime research;
  - the kind parameters are snapshotted; the per-owner limit of 256 counts
    every kind;
  - the registry barrier flush runs before the terminal extracts the package.
  - A `data` package replay is now idempotent whatever the satellite did since
    (ADR-049 §6, R2-L6).
- **Terminal.** The package slot accepts a data package only with a `data`
  identity and the generic package only with another kind. A non-`data` chip
  without a package answers `DEFINITION_NOT_FOUND` for the data-mission start.
  The new `DECOMMISSION` button removes an idle satellite (no unfinished
  mission, no live receiver link) with a barrier flush, then blanks the chip.
- **Operator commands.** `/arce satellite admin blank-chip <player>` blanks the
  main-hand chip of a satellite that no longer exists, with the
  `ARCE_SATELLITE_BLANK_CHIP` audit line; `recover-chip` now writes the kind and
  components; summaries show the kind and orbit body.
- **Network.** The new `advancedrocketrycommunity:satellite` channel, protocol
  `1`, S2C only, message 0 `SatelliteTerminalViewPacket` handled on the client
  main thread. The terminal menu open data moves to format 2: the unchanged
  format-1 content followed by a strict view flag. The view is sent on open and
  on change, at most once per 5 ticks per player, and names the open container.
  Instance, product, mission-page and reward-buffer sections are part of the
  wire format now and stay empty until C8b.

## Proposed ADR-049 revision 4

Recorded in the ADR as **PROPOSED**, for the C7 independent review after C7c:

1. an eleventh builder slot for redstone (the mod has no FE generator;
   2,000 FE per item, as at the terminal), so root schema 1 has 11 slots;
2. the view also carries the selected kind and the chip satellite's orbit body;
3. the blank-chip command path, its refusal for registered satellites and the
   kind-aware `recover-chip`;
4. `DEFINITION_NOT_FOUND` for a data-mission start with a non-`data` chip.

## v1.6 balance decisions (data, not contract)

| Definition | Research | Launch targets | Parameters |
|---|---|---|---|
| `survey_satellite` | 0 | Earth, Moon, Mars, Venus, gas giant, Tau Ceti e | 6,000-tick missions, 2 instances, scan energy 1,000 (the legacy base), radius 32, cell 8 |
| `solar_satellite` | 120 | Earth, Moon, Mars, Venus | output 100 % |
| `asteroid_miner` | 240 | Earth, Moon, Mars, Venus, Tau Ceti e | — |
| `gas_harvester` | 360 | gas giant | — |

Research thresholds are one, two and three data-mission yields. Recipes follow
the existing satellite and machine progression.

## Tests

New unit tests:

- `SatelliteTerminalViewPacketTest`: the maximum view round-trips; measured
  `ARCE_TERMINAL_VIEW_MAX_BYTES=8405` (bound 12,288), pinned; strict decoding of
  trailing bytes, truncation, oversize, unknown kind, bad presence byte,
  out-of-list positions and over-count pages; constructor bounds.
- `SatelliteLifecycleRegistryTest`: idle launch and replay, identity conflicts,
  data replay after a claimed mission, the owner limit across kinds and after
  decommission, the owner count after restore, decommission rules (owner,
  operator, busy mission, linked solar, missing receiver, finished missions
  kept), and kind-state updates.
- `GeneratedSatelliteContentTest`: the committed v1.6 data decodes into one
  catalog with the ADR-049 §2 numbers and one definition per primary; the
  representative survey blueprint evaluates to {4, 10,720, 1,000, 0, 10}.

New GameTests (`satellite` batch):

- `SatelliteBuilderGameTests`: the refusal sequence, redstone charging,
  assembly with the exact identity, energy and consumed slots, the rate limit,
  automation slot rules, `STAT_LIMIT` before `REQUIREMENT_UNMET`,
  `RESEARCH_LOCKED` before `NO_POWER`, and a future root preserved, blocked
  and carried.
- `SatelliteKindTerminalGameTests`: the package-slot kind rule, idle launch
  with the flushed registry and exact state, the composed terminal view (kind,
  definition 0 of 4, target Earth 0 of 6, orbit body, empty resource
  sections), idempotent replay consuming the package, the data-mission refusal, `TARGET_NOT_ALLOWED` for undiscovered
  Mars, `COMPONENT_UNAVAILABLE`, `RESEARCH_LOCKED`, decommission refusals
  (busy, unauthorized, inert chip) and success, and the blank-chip command
  (allowed for an inert chip, refused for a registered satellite).

Changed to exact new values, with no assertion removed or loosened:

- `NetworkProtocolPinTest`: five channel sources, and the committed table gains
  `advancedrocketrycommunity:satellite|1|0|SatelliteTerminalViewPacket|PLAY_TO_CLIENT`;
- `SatelliteTerminalTargetsTest`: format 2 with the view flag inside the same
  32,600-byte bound, and a new test that format-1 data is rejected.

## Commands actually executed

| Command | Result |
|---|---|
| `gradlew compileJava`, `runData`, and `test`/`runGameTestServer` for the satellite and network tests (iterative development runs) | One GameTest failure on the first run: the launch-recheck test reused the terminal's persisted target selection (Mars) for a later identity. The test was reordered so every refusal before the Mars case uses the first target; no assertion changed. These iterative outputs were not captured into the evidence directory; this table is their only record |
| `gradlew clean build test runData runGameTestServer --console=plain` (attempt 01, `attempt-01-failed/`) | **Failed**, exit 1: 1,087 JUnit tests / 203 suites, 1 failure. `ModMetadataTest` still pinned the C7a description after `gradle.properties` changed. The pin was updated to the exact new text. Before attempt 02, the terminal view was also changed to compose at most once per 5 ticks, and a GameTest assertion on the composed view was added |
| `gradlew clean build test runData runGameTestServer --console=plain` (attempt 02) | Exit 0, 4m33s. **1,087 JUnit tests / 203 suites executed**, 0 failures. **266 required GameTests** passed. The v1.6 DataGen output was staged before the run; DataGen rewrote it byte-identically (generated diff empty) |

The attempt-02 log has 15 ERROR lines and 0 FATAL. They are the same set as
C7a: the intentional failure-injection GameTests (including the injected
satellite launch-flush failure) and the missing `server.properties`.
Repository validators are listed in [the packaging log](#repository-validators).

## Repository validators

Run on the staged tree after packaging; the log is kept outside the archive
(`packaging/out/validation.log` in the evidence directory):

| Command | Result |
|---|---|
| `python scripts/validate_repository.py --require-approved-identity` | exit 0 (45 passed, 0 failed) |
| `python scripts/validate_v1plus_planning.py` | exit 0 |
| `python scripts/validate_bootstrap_provenance.py` | exit 0 |
| `python -m unittest tests.test_v1plus_planning` | exit 0 |
| `git diff --cached --check` | exit 2 on the first run (an extra blank line at the end of ADR-049), fixed; exit 0 on the rerun |

## Not done in C7b

- No survey area scan, no microwave receiver and no receiver-missing detection;
  a linked solar satellite cannot be linked yet (C7c).
- No per-player intent rate limits beyond the builder's assembly cooldown, no
  write-policy change and no budgets (C8a).
- No resource missions; the view's resource sections stay empty (C8b).
- No native server run and no V1 visual check of the new screens (C9, `[H]`).
- No independent review yet: C7 is reviewed as a whole after C7c, together
  with the proposed ADR-049 revision 4.
