# TEST-REPORT — v0.4.0

## Bound build

```text
implementation: f880870aa4db0a46758dcc8615dfa2c16b2e3b59
artifact: advancedrocketry-community-1.20.1-0.4.0-dev.jar
bytes: 466433
sha256: 05279656dfae21f682ca45a000517628dfcf706ebc4cce9ce2fe16e0723e96f1
```

## Automated results

| Check | Result | Evidence |
|---|---|---|
| Two clean builds | PASS; byte-identical main JAR | `evidence/artifact/artifact-summary.json` |
| Java unit tests | PASS; 86/86, no skip | Gradle XML and `evidence/automated/summary.json` |
| Targeted Python tests | PASS; 124/124 | `evidence/automated/summary.json` |
| Full Python governance CI | PASS; 596 run, 4 documented skips | GitHub Actions run `33421129566` |
| JAR audit | PASS; 346 entries, 0 findings | `evidence/artifact/jar-content-manifest.json` |
| Client import boundary | PASS; 0 findings | static validator |
| DataGen | PASS; 29 resources, 0 writes, clean diff | v0.4 generated manifest |
| Forge GameTest | PASS; 25/25 | GameTest completion and performance marker |
| Repository validation | PASS; 0 warnings, 0 failures | strict validator output |

Local release preparation intentionally ran the 124 directly affected tests
rather than repeating expensive historical Git-fixture tests. The governance
CI then ran the complete 596-test inventory in 167.201 seconds with four
documented skips and no failure.

## Packaged-server matrix

| Scenario | Result |
|---|---|
| Forge first start, status, save, clean stop, same-world restart | PASS |
| v0.2 Electrolyzer paused-state restart and atomic completion | PASS |
| v0.3 fixed dimensions, invalid reload rejection, recovery, persistence | PASS |
| v0.4 16-Vent five-minute pressure and exact Vent NBT restart | PASS |

The v0.4 server held 20.0 minimum TPS across 60 samples, performed no full GC,
retained exact Vent NBT, failed closed after restart without power, and rebuilt
the derived breathable volume after re-energizing.

## Behavioral and authority coverage

- Breathability, suit completeness, oxygen consumption, and vacuum damage are
  server decisions; the S2C packet only renders bounded display state.
- Scans are incremental, total-volume limited, per-tick budgeted, and return
  `PENDING` at unloaded chunks without forcing them.
- GameTests cover sealed/open rooms, doors, too-large and unloaded boundaries,
  adjacent/merged volumes, duplicate Vents, equipment states, oxygen depletion,
  persistence adapters, registration, interaction, and performance.
- Vent interaction exposes `OPEN`, `TOO_LARGE`, `PENDING`, `NO_POWER`, and other
  finite failure states through translated status text.

## Manual result

Two exact-JAR packaged clients simultaneously joined the dedicated server,
observed matching sealed/open/resealed transitions, disconnected for a clean
same-world restart, and both rejoined. Space tests visibly covered no suit,
partial suit, complete oxygenated suit, and exhausted oxygen. Owner
`sunthemoon` approved G0, G8, and G9 on 2026-09-01.
