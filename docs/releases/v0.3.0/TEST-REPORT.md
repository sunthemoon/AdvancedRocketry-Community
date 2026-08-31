# TEST-REPORT — v0.3.0

## Tested identity

```yaml
version: v0.3.0
build: 1.20.1-0.3.0-dev
tested_implementation_commit: 63d159ef3d9e489862b0d517b76bcc523df852c9
jar_sha256: 920425eaeb8cf8b6e94f23ed3086ca290ae734315059bbcf8eea100272d8bdfb
jar_bytes: 296189
jar_entries: 226
java: 17.0.8
forge: 47.4.10
```

## Automated results

| Check | Result | Evidence |
|---|---|---|
| Two consecutive `clean build` executions | PASS; byte-identical main and sources JARs | `evidence/artifact/artifact-summary.json` |
| JUnit domain tests | 50/50 PASS, 0 skipped | Gradle XML/HTML reports |
| Python unit suite | 580 PASS, 1 justified historical-artifact skip, 0 failed (581 total) | `evidence/automated/summary.json` |
| JAR content audit | 226 entries; 0 findings | `evidence/artifact/jar-content-manifest.json` |
| Client import and celestial identity boundaries | PASS; 0 findings | static validators |
| DataGen | PASS; 7 files, 0 written, clean Git diff | generated manifest and worktree check |
| Forge GameTest | 15/15 PASS with completion marker | GameTest log |
| Strict repository validation | 27 passed, 0 warnings, 0 failed | repository validator output |
| Pull-request checks | 3/3 PASS on READY_FOR_AUDIT head `9c38e23` | Forge and governance URLs in `GATE-STATUS.md` |

## Model, reload, save, and network coverage

- Codecs reject invalid identifiers, values, parents, cycles, duplicate IDs,
  oversized catalogs, and invalid atmosphere/orbit combinations.
- Reload is all-or-nothing. A missing-parent datapack was rejected while the
  last valid generation remained active; disabling it recovered generation 2.
- Schema-1 Overworld `SavedData` records bounded discovery and first visits.
  Future schema is preserved and cannot be mutated by the old implementation.
- The display-only client snapshot is capped at 128 bodies and 96 KiB. The
  canonical three-body payload is 469 bytes per joining player.
- Moon and Space use stable `ResourceKey<Level>` identities. Runtime numeric
  dimension IDs and dynamic Level registration are absent.

## Packaged server, multiplayer, and persistence

The Forge 47.4.10 packaged server passed first start, status, save, clean stop,
and same-world restart. The v0.2 Electrolyzer regression retained paused state
and completed atomically under the v0.3 JAR.

The celestial server matrix loaded both fixed Levels, persisted marker blocks,
rejected invalid reload data without losing the active catalog, and recovered a
valid catalog. Two exact-JAR clients then joined simultaneously, traveled to
Moon and Space, returned safely, disconnected on clean stop, and both rejoined
the same world. The celestial NBT source SHA-256 remained
`1a385871267e5e72a93ec6c13cdc1bd0e3414ded492152fdbaee524a51ee501e`.

## Legacy XML import

The bounded StAX importer disables DTD/external entities, enforces input/node/
depth/string/body/issue budgets, keeps numeric dimension IDs report-only, and
produces deterministic LF JSON. Two runs over the exact 1,031-byte upstream
fixture produced byte-identical output and an explicit warning report.
