# MANUAL-TEST — v0.4.0

## Test identity

```yaml
executed_at: [2026-08-31, 2026-09-01]
tester: Codex-assisted packaged-client execution
reviewer: sunthemoon
review_status: APPROVED
reviewed_at: 2026-09-01
minecraft: 1.20.1
forge: 47.4.10
java: 17.0.8
build: 1.20.1-0.4.0-dev
tested_implementation_commit: f880870aa4db0a46758dcc8615dfa2c16b2e3b59
jar_sha256: 05279656dfae21f682ca45a000517628dfcf706ebc4cce9ce2fe16e0723e96f1
client_profiles: 2 isolated packaged Forge profiles
other_content_mods: 0
```

The disposable dedicated server listened on loopback. Offline mode was used
only to create two deterministic isolated player identities; its normal
online-mode settings were restored after capture. Client and server copies
were byte-identical to the tested JAR.

## Player-flow matrix

| Case | Expected | Actual | Result |
|---|---|---|---|
| Two matching clients | Both clients join the same dedicated server | `ARCEV040A` and `ARCEV040B` were simultaneously online and received server snapshots | PASS |
| Sealed room | Powered Vent makes the 5×5×5 room breathable | Both server queries and both HUDs reported breathable / `ROOM SEALED` | PASS |
| Open and reseal | Opening a wall returns vacuum; resealing recovers | Both players changed together to vacuum and back to breathable | PASS |
| No/partial/full suit | Space distinguishes missing, incomplete, and complete protection | HUD showed `VACUUM`, `SUIT INCOMPLETE`, and `SUIT OXYGEN` respectively | PASS |
| Oxygen exhaustion | Empty suit oxygen stops protection | HUD showed `OXYGEN EMPTY`, damage occurred, and the vacuum death source was logged | PASS |
| Same-world restart | Vent state persists while the derived volume is rebuilt | Exact schema-1 Vent NBT survived; no-power restart failed closed, then re-energizing rebuilt the room | PASS |
| Rejoin | Both clients reconnect after the clean restart | Both rejoined the same Moon room and observed the rebuilt state | PASS |
| GUI scale | HUD remains readable at scales 2 and 3 | Both scales are visibly legible in committed captures | PASS |

## Screenshot and animation index

| Evidence | What it proves |
|---|---|
| [`a-room-sealed.png`](evidence/client/screenshots/a-room-sealed.png) | Client A at GUI scale 2 sees `ROOM SEALED` |
| [`b-room-sealed-scale3.png`](evidence/client/screenshots/b-room-sealed-scale3.png) | Client B at GUI scale 3 sees the same state |
| [`a-room-open.png`](evidence/client/screenshots/a-room-open.png) | Full suit switches to `SUIT OXYGEN` when the room opens |
| [`b-room-open.png`](evidence/client/screenshots/b-room-open.png) | Unsuitable player sees red `VACUUM` in the same open room |
| [`room-seal-flow.gif`](evidence/client/screenshots/room-seal-flow.gif) | 80-frame, 20-second Minecraft-window transition from sealed to open to sealed |
| [`a-after-restart-no-power.png`](evidence/client/screenshots/a-after-restart-no-power.png) | Restart initially fails closed without Vent power |
| [`a-after-restart-recovered.png`](evidence/client/screenshots/a-after-restart-recovered.png) | Client A sees the rebuilt sealed volume |
| [`b-after-restart-recovered.png`](evidence/client/screenshots/b-after-restart-recovered.png) | Client B sees the same rebuilt state |
| [`a-space-full-suit-separated.png`](evidence/client/screenshots/a-space-full-suit-separated.png) | Complete oxygenated suit protects in Space |
| [`b-space-no-suit-separated.png`](evidence/client/screenshots/b-space-no-suit-separated.png) | No suit reports vacuum in Space |
| [`b-space-partial-suit.png`](evidence/client/screenshots/b-space-partial-suit.png) | Partial equipment is explicitly incomplete |
| [`a-space-oxygen-empty.png`](evidence/client/screenshots/a-space-oxygen-empty.png) | Empty oxygen status and health loss are visible |

Hashes, byte counts, dimensions, identities, exact artifact-copy hashes, and
filtered logs are bound by
[`evidence/client/manual-evidence.json`](evidence/client/manual-evidence.json).

## Human Gate decision

```yaml
G0: APPROVED
G8: APPROVED
G9: APPROVED
reviewer: sunthemoon
reviewed_at: 2026-09-01
findings: []
```

Changed artifact or evidence bytes invalidate this approval and require a new
review. [`checksums.txt`](checksums.txt) binds the approved inventory.
