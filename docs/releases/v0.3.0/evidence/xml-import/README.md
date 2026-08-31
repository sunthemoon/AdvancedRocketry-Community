# Legacy XML import evidence

This directory records the deterministic import of the exact upstream
`Template.xml` fixture approved in
[`docs/provenance/v0.3.0-upstream-xml-fixture.json`](../../../../provenance/v0.3.0-upstream-xml-fixture.json).

## Input

- Repository: `Advanced-Rocketry/AdvancedRocketry`
- Commit: `c5cd5af62fc07cd4e0d24f06a16033f181c47c04`
- Source path: `Template.xml`
- SHA-256: `40674cb8a730e5b6baf2baa3943d34d1712f6f24ffa07db79467627b9c0176e1`

## Command

```powershell
./gradlew importLegacyCelestial `
  -PlegacyXml=src/test/resources/io/github/sunthemoon/advancedrocketrycommunity/celestial/legacy/upstream/Template-c5cd5af6.xml `
  -PlegacyOutput=build/v030-import-evidence `
  --no-daemon
```

The command completed successfully on 2026-08-31. A second run into an
independent empty directory produced the same relative paths and SHA-256 values.

| Output | SHA-256 |
| --- | --- |
| `data/advancedrocketrycommunity/celestial_bodies/imported/planet_a.json` | `d4b52e4b98778aba50d87363000933886aed25b1a84079f8985c159819024409` |
| `data/advancedrocketrycommunity/celestial_bodies/imported/planet_a/planet_a_moon.json` | `84eee060d16d978693c496cc18507fe7b19874cb483677580845c92f577a8621` |
| `import-report.json` | `a316a1f43867584385443beb82c61f7b522084b31a52f59977cdb3af347ced8a` |

The report status is `SUCCESS_WITH_WARNINGS`. Unsupported legacy star, biome,
color, and orbital-angle fields are retained as bounded path-addressed
diagnostics rather than silently converted. Numeric dimension IDs, when
present, are report metadata only and never become runtime identities.
