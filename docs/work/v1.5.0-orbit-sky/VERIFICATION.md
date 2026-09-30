# v1.5 per-station sky (ORBIT-03)

Date: 2026-09-30. Branch: `codex/v1.5.0-orbital-station-warp`. Base: `50a0f42`.
Development slice only: v1.5 and inherited G0-G9 remain `IN_PROGRESS`. This record is
not a Gate approval. Contract: [ADR-047](../../decisions/ADR-047-PER-STATION-SKY-CONTEXT.md)
revision 2, accepted after an independent contract review (archived here as
`independent-review-sky.zip`). It amends ADR-036 and ADR-046 UI-01.

## Implemented

- **Server:**
  - `station/orbit/StationSkyContextService` runs every 10 ticks.
  - It derives each online player's context (the orbited body ID, or none) from their
    own Level and position with one indexed registry lookup.
  - It sends the context only on change and forgets a player on respawn, Level change
    and logout. It is wired once at mod construction.
- **Protocol:** `celestial_snapshot` 2 → 3, adding message 1 `StationSkyContextPacket`.
  The message is one strict boolean byte plus a bounded ID, at most 131 bytes, with no
  trailing bytes. The pin table and `CelestialSnapshotV2Test` pin the new version.
- **Client:**
  - `StationSkyContextCache` is cleared on logout and on `ClientPlayerNetworkEvent.Clone`.
  - `SkySelection` takes the context as part of its cache key, and only in Space.
  - `OrbitalAppearance` holds explicit colours for packaged bodies.
  - `SkyMath` places the disc at 30° radius, 25° below the horizon toward +X. That is
    65° from the nadir sun of Space's fixed time.
  - `PlanetarySkyRenderer` draws the disc with the existing sun disc mesh, after the
    stars and before the sun.
- **Docs:**
  - ADR-036 and ADR-046 carry amendment notes;
  - the sky guide, CHANGELOG, implementation log, CURRENT_VERSION and COMPLETION-PLAN
    are updated, with the V0 deviation recorded.

## Review handling

| ID | Severity | Handling |
|---|---|---|
| F1 | High | Explicit per-profile orbital colours (`OrbitalAppearance`); `OrbitalSkyTest.everyPackagedOrbitableBodyHasAVisibleColour` checks every packaged orbitable body against the Space sky |
| F2 | High | Fixed disc direction and size; drawn before the sun; `OrbitalSkyTest.theDiscNeverCoversTheSunOrItsHaloInTheSpaceLevel` derives the sun angle from Space's `fixed_time` |
| F3 | High | One lifecycle rule: the client clears on `Clone`, the server forgets on respawn and Level change. The GameTest covers both re-sends |
| F4 | Medium | `amends` declared; notes in ADR-036 and ADR-046; sky guide updated |
| F5 | Medium | 128-character ID, at most 131 bytes, strict boolean, malformed input closes the connection (`StationSkyContextPacketTest`) |
| F6 | Medium | The sun follows the snapshot's solar intensity with or without a profile (`SkySelectionTest`); the ADR states on/off opacity |
| F7 | Medium | GameTest: two players in two stations, respawn and Level change, blocked registry, expansion ring, deletion, map cleanup. Also a traceability table, the V0 deviation recorded, and ORBIT-03 capped at implemented-unverified with the V1/V2 cases listed |
| F8 | Low | Wiring stated in the ADR |
| F9 | Low | Context applied only in Space; cache key by value; lookup only on a miss |
| F10 | Low | Wording corrected (catalog path, quarantine, schema 2 vs protocol 3, no-screen reason, `blockPosition()`) |
| F11-F13 | Info | Disclosure analysis recorded; channel choice kept; revision 2 matches the implementation |

## Commands actually executed

| # | Command | Result | Evidence |
|---|---|---|---|
| 1 | `./gradlew test` (packet, selection, pin, snapshot tests) | exit 0 | `focused-unit-01.log` |
| 2 | `./gradlew runGameTestServer` (before the review changes) | exit 0: 257 GameTests | `gametest-01.log`, `gametest-01/` |
| 3 | `./gradlew test` (packet, selection, orbital sky, sky math) after the review changes | exit 0 | `focused-unit-02.log` |
| 4 | `./gradlew runGameTestServer` (extended sky GameTest) | exit 0: 257 GameTests | `gametest-02.log`, `gametest-02/` |
| 5 | `./gradlew clean build test runData runGameTestServer` (full run 01) | exit 0: 1,051 JUnit tests, 0 failures, 0 errors, 0 skipped; all 257 required GameTests passed; matrix 345/345; `git diff --exit-code -- src/generated` exit 0 | `root-gradle-01.log`, `run-01/`, `junit-summary.json`, `artifacts.json`, `generated-diff.log` |
| 6 | `python scripts/check_client_imports.py` | PASS (no client references outside the client package) | `validation.log` |
| 7 | repository validators, `unittest` and `git diff --check` (before staging) | see `validation.log` | `validation.log` |

## Not verified

- **V0** (Xvfb/LLVMpipe): not available on this Windows host, so not run.
- **V1** (real GPU) and **V2** (two clients), with the ADR-047 case list: open for
  ACC-02.
- No real client has rendered the disc. These tests show the context, selection,
  geometry and colours, not the rendered picture.
