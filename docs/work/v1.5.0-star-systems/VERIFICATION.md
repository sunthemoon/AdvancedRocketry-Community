# V150-STAR-02 / STAR-03 verification

Date: 2026-09-30. Branch: `codex/v1.5.0-orbital-station-warp`.
Base: `7180885` (ADR-043 accepted). Contract:
[ADR-043](../../decisions/ADR-043-STAR-SYSTEMS-AS-ROOT-TREES.md).
Development slice only: v1.5 and inherited G0-G9 remain `IN_PROGRESS`.
No release candidate, tag or human Gate approval. Independent review pending.

## Implemented scope

- `CelestialCatalog` derives each body's star system (its root body's ID) once
  per catalog, with a bounded parent walk. It is never persisted. The catalog
  exposes `systemOf`, `systems` and `systemBodies`, and rejects more than 16
  root bodies (`MAX_SYSTEMS`).
- `PlanetaryCatalog.create` rejects a route whose endpoints are in different
  systems ("Route X connects systems A and B"). The whole reload candidate is
  refused, the previous pair stays active, and on the initial load the server
  refuses to start (existing listener behavior, now tested).
- `StarSystemKnowledge`: a body is known if it needs no discovery or has a
  recorded discovery; a system is known if any body is. No new persistence.
  First consumer: `/arce celestial systems`, which lists each system with its
  known state, body count and known-body count (at most 16 lines).
- Example system (original data, placeholder values inside schema bounds):
  - `tau_ceti`: root star, no Level, not landable or orbitable, solar 0,
    no discovery;
  - `tau_ceti_e`: orbitable planet with no Level, solar 0.5, discovery required.

  The v1.5 data satellite adds `tau_ceti_e` as a target and keeps the v1.4
  economy. Names for both bodies are in the `advancedrocketrycommunity_v150`
  language namespace (en_us, zh_cn).
- Packaging (integrator change): DataGen now writes `src/generated/v1.5/resources`,
  with v1.4 as an existing input. v1.5 is a resource root, and its cache is
  git-ignored. The v1.4 copy of `satellite_definitions/data_satellite.json` is
  excluded from `processResources` and `sourcesJar`, like the v0.8 copy, so one
  authoritative copy is packaged. `build.gradle` and `.gitignore` headers,
  `THIRD-PARTY-NOTICES.md` and the v1.5 provenance note record the MDK-derived
  changes. v1.4 generated files are unchanged.
- The API's 27 class files are byte-identical. The API JAR bytes differ only in
  `META-INF/THIRD-PARTY-NOTICES.md` (new modification dates).

Not in this slice: warp (ADR-044, still under review), per-system sky, routes or
landable planets in other systems, and star-map grouping (the existing layout
already puts each root in its own row).

## Tests

- `StarSystemContentTest` (3):
  - packaged content forms exactly two systems, and every body is in the right one;
  - packaged routes pass, while an added Earth-orbit to Tau Ceti e route is
    rejected;
  - the example stays within schema bounds, the star is public and not a
    satellite target;
  - the satellite keeps every v1.4 target plus the new one;
  - knowledge follows discovery.
- `PlanetaryReloadTest` (+3, from real pack files through the reload lifecycle):
  - systems derived from a second root;
  - a cross-system route rejects the reload and keeps the previous pair;
  - 17 roots are rejected;
  - an invalid initial pair throws "Initial planetary catalog is invalid … connects
    systems", with no pair published.
- `StarSystemGameTests` (real server): `/arce celestial systems` lists the home
  and example systems, and recording a discovery of Tau Ceti e raises its known
  bodies from 1 to 2. A connected player at a station orbiting Tau Ceti e sees
  its orbit and `solar=0.50` through `/arce station environment`.
- Updated for the intended content change (exact assertions, not loosened):
  - `PlanetaryDiscoveryTest` now expects the v1.5 packaged satellite (7 targets,
    same economy, a superset of v1.4);
  - `BootstrapGameTests` expects 8 packaged bodies (6 plus the example system).
- `ConnectedTestPlayers` is a shared GameTest helper for connected mock players.
  The two older station GameTests still carry their own copies, a cleanup for
  later.

## Commands actually executed

| Command | Result |
|---|---|
| `gradlew compileJava compileTestJava` | Exit 0 |
| `gradlew runData` | Exit 0; five new files under `src/generated/v1.5`; v1.4 unchanged |
| `gradlew test` run 01 | Exit 1; 981 tests, 1 failure: `PlanetaryDiscoveryTest` expected the v1.4 satellite (intended change) |
| `gradlew test` run 02 | Exit 0; 981 tests / 178 suites |
| `clean build test runData runGameTestServer` run 01 | Exit 1; JUnit 981 passed; `BootstrapGameTests` expected 6 packaged bodies (intended change) |
| same, run 02 | Exit 0, 186 s; `:test` executed, 981 JUnit / 178 suites, 0 failures; all 240 GameTests passed; `git diff` of `src/generated` clean after runData |

Failed runs are retained. Main JAR
`6f1158aa064d32e5750b6d54e6c13af81f30991973b1291b3128cfa9f9d25fc7`.

## Evidence archive

Source directory: `arce-v150-star-*` under the Windows Temp directory (named in
`evidence-archives.json`).

`root-checks.zip` (552 members, SHA-256
`074e29df966161899fd1565e91d843c7d906a6d0d5b0e3dd1bc07c953925b2ce`) contains:
- the compile, runData, test and full-run logs, including the failed runs;
- their JUnit XML and GameTest logs;
- source, generated-file and artifact hashes;
- the packaging script.

The validators all exited 0: repository (45 passed), planning, provenance,
planning regression and `git diff --check`.
