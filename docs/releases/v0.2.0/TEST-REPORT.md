# TEST-REPORT — v0.2.0

## Accepted identity

```yaml
version: v0.2.0
build: 1.20.1-0.2.0-dev
tested_implementation_commit: adc505fdd471eaccacb73a5e1247d60be83dd808
jar_sha256: a8356cbeafdaffbd1192628c414c6996c402a757f6211c857d87e8ead52a2598
jar_bytes: 173080
jar_entries: 158
java: 17.0.8
forge: 47.4.10
```

## Automated results

| Check | Result | Evidence |
|---|---|---|
| Two consecutive `clean build` executions | PASS; byte-identical main JAR | `evidence/artifact/artifact-summary.json` |
| JUnit domain tests | 12/12 PASS | Gradle XML/HTML reports and `evidence/automated/summary.json` |
| Python unit suite | 556/556 PASS | `evidence/automated/summary.json` |
| JAR content audit | 158 entries; PASS | `evidence/artifact/jar-content-manifest.json` |
| Client import boundary | PASS; 0 findings | `evidence/automated/summary.json` |
| DataGen | PASS; 0 changed files | generated manifest and clean Git diff |
| Forge GameTest | 12/12 PASS with completion marker | GameTest log and `evidence/automated/summary.json` |
| Strict repository validation | 23 passed, 0 pending/warnings/failed | validator output and `evidence/automated/summary.json` |
| PR checks on tested head | 3/3 PASS | Forge/governance URLs in `GATE-STATUS.md` |

## Domain and authority coverage

- Bounded recipe values and JSON/network serialization reject invalid schema,
  fluid, sizes, ranges, output tags, and oversized identifiers.
- Exact cycle: 2 empty canisters + 1,000 mB water + 2,000 FE over 100 ticks
  produces exactly 1 hydrogen and 1 oxygen canister.
- Fifty cycles conserve 100 input/output canisters while consuming 50,000 mB
  water and 100,000 FE, with no duplication or loss.
- Server tick code owns recipe validity, progress, pause, resource consumption,
  and atomic completion. No custom result-bearing C2S packet exists.
- Two menu viewers observe the same server state. Capability side rules,
  insufficient energy/water/output space, redstone pause, drops, and future
  schema preservation are covered.
- The 20-idle-machine case performs bounded tick work without world scans or
  log spam.

## Packaged server and persistence

The official Forge 47.4.10 installer has SHA-1
`66bfea9963bfa60d88bab6b2750e74a958392715`. First start, status query, save,
clean stop, same-world restart, status query, matching-client join/leave, and
restart rejoin/leave all passed. Both cycles used the accepted JAR and the same
player identity.

The machine-specific restart harness paused at progress `40/100` with two
inputs, 1,000 mB water, and 1,200 FE. The same values loaded after restart, then
completion produced one H2 and one O2 canister and consumed the exact remaining
resources.

## Manual results

The packaged client initialized the accepted build, entered a disposable
single-player world, displayed the Electrolyzer and its redstone control, and
rendered its menu at effective GUI scales 1–4. No missing texture, overlap, or
client-class linkage finding was observed. G8/G9 were approved by repository
owner `sunthemoon` on 2026-08-31.

## Known non-blocking warning

The vanilla `ClientRecipeBook` reports the custom Electrolyzer recipe category
once per recipe synchronization. It does not affect recipe transfer, machine
processing, menu state, or server authority and is not emitted per tick.
