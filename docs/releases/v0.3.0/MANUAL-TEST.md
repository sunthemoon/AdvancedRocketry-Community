# MANUAL-TEST — v0.3.0

## Test identity

```yaml
date: 2026-08-31
tester: Codex-assisted packaged-client execution
reviewer: sunthemoon
review_status: APPROVED
reviewed_at: 2026-08-31
minecraft: 1.20.1
forge: 47.4.10
java: 17.0.8
build: 1.20.1-0.3.0-dev
tested_implementation_commit: 63d159ef3d9e489862b0d517b76bcc523df852c9
jar_sha256: 920425eaeb8cf8b6e94f23ed3086ca290ae734315059bbcf8eea100272d8bdfb
client_profiles: 2 isolated packaged Forge profiles
other_content_mods: 0
```

Only Minecraft F2 screenshots are committed. Complete raw logs, launcher
windows, machine paths, desktop content, and player IP addresses are excluded.

## Player-flow matrix

| Case | Expected | Actual | Result |
|---|---|---|---|
| Mods identity | Name, version, logo, MIT license, link, and v0.3 description appear | Selected Forge Mods detail displays the accepted build | PASS |
| Two matching clients | Both exact-JAR clients join the same dedicated server | A and B were simultaneously listed online; each received a 469-byte generation-1 snapshot | PASS |
| Moon flow | Operator travel creates a bounded safe destination and applies Moon gravity | A reached `advancedrocketrycommunity:moon` at 8.5/80/8.5; gravity was `0.0132` | PASS |
| Space flow | Operator travel creates a bounded destination without an endless fall/death loop | B reached `advancedrocketrycommunity:space` at 8.5/80/8.5; gravity was `0.0` | PASS |
| Safe return | Space player returns to Earth and regains Earth gravity | B reached `minecraft:overworld`; next-tick gravity was `0.08` with full health | PASS |
| Same catalog | Both dimensions observe the fixed three-body catalog | Server listed Earth, Moon, and Space with stable namespaced mappings | PASS |
| Clean restart/rejoin | Same world restarts and both matching clients reconnect | A resumed in Moon, B resumed in Overworld; both clients exited cleanly after stop | PASS |
| SavedData persistence | Schema and discovery/first-visit state remain exact | Three entries and compressed NBT SHA-256 were byte-identical before/after restart | PASS |

## Screenshot index

| Evidence | What it proves |
|---|---|
| [`mods_v030.png`](evidence/client/screenshots/mods_v030.png) | Packaged Forge Mods identity and metadata |
| [`moon_before_restart.png`](evidence/client/screenshots/moon_before_restart.png) | Player A in fixed Moon at the safe platform |
| [`space_before_restart.png`](evidence/client/screenshots/space_before_restart.png) | Player B in fixed Space at the safe platform |
| [`earth_safe_return.png`](evidence/client/screenshots/earth_safe_return.png) | Player B safely returned to Overworld |
| [`moon_after_restart.png`](evidence/client/screenshots/moon_after_restart.png) | Player A rejoined Moon after same-world restart |
| [`earth_after_restart.png`](evidence/client/screenshots/earth_after_restart.png) | Player B rejoined Overworld after same-world restart |

Hashes, dimensions, player IDs, artifact-copy hashes, observations, and filtered
logs are bound by
[`evidence/client/manual-evidence.json`](evidence/client/manual-evidence.json).

## Human Gate decision

```yaml
G0: APPROVED
G8: APPROVED
G9: APPROVED
reviewer: sunthemoon
reviewed_at: 2026-08-31
findings: []
```

Repository owner `sunthemoon` approved these Gates against main JAR SHA-256
`920425eaeb8cf8b6e94f23ed3086ca290ae734315059bbcf8eea100272d8bdfb`
and the exact evidence inventory. [`checksums.txt`](checksums.txt) includes the
approval record; changed artifact or evidence bytes require renewed review.
