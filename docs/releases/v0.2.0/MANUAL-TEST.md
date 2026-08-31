# MANUAL-TEST — v0.2.0

## Test identity

```yaml
date: 2026-08-31
tester: Codex-assisted packaged-client execution
reviewer: sunthemoon
review_status: APPROVED
minecraft: 1.20.1
forge: 47.4.10
java: 17.0.8
build: 1.20.1-0.2.0-dev
jar_sha256: a8356cbeafdaffbd1192628c414c6996c402a757f6211c857d87e8ead52a2598
client_profile: isolated packaged Forge profile
other_content_mods: 0
```

Only Minecraft F2 screenshots are committed. Desktop content, launcher
windows, machine paths, and complete raw logs are excluded. Filtered logs keep
only lifecycle and project observations needed for review.

## Player-flow matrix

| Case | Expected | Actual | Result |
|---|---|---|---|
| Mods identity | Project name, version, logo, MIT license, link, and description appear | Selected Mods detail page shows all fields | PASS |
| Single-player entry | Packaged client enters a disposable world with the accepted JAR | World loaded; Electrolyzer and adjacent redstone control rendered | PASS |
| Machine menu | Energy, water, progress, inputs, outputs, and status are readable | All values and slots visible; redstone pause reason shown | PASS |
| GUI scales 1–4 | Menu stays on-screen without overlapping inventory labels | Separate F2 evidence at effective scales 1, 2, 3, and 4 | PASS |
| Save and close | Integrated world saves and client stops cleanly | Login, save/pause, and stop lifecycle markers recorded | PASS |
| Dedicated first cycle | Matching client joins and leaves the packaged server | Server and client markers both observed | PASS |
| Dedicated restart cycle | Same player reconnects to the same world after clean restart | Identity and same-world bindings match | PASS |
| Machine restart | Paused progress, inventory, fluid, and energy survive restart | `40/100`, 2 inputs, 1,000 mB water, and 1,200 FE match before/after | PASS |
| Atomic completion | Restarted process produces exact outputs without partial consumption | 1 H2 + 1 O2; inputs/water/energy consumed exactly | PASS |

Failure-state behavior, two menu viewers, capability directions, block drops,
20 idle machines, and 50-cycle conservation are executed by GameTest/JUnit and
reported in [`TEST-REPORT.md`](TEST-REPORT.md).

## Screenshot index

| Evidence | What it proves |
|---|---|
| [`mods_zh_cn.png`](evidence/client/screenshots/mods_zh_cn.png) | Packaged Forge Mods identity and metadata |
| [`singleplayer_world.png`](evidence/client/screenshots/singleplayer_world.png) | Disposable single-player world with the machine and redstone control |
| [`electrolyzer_gui_scale_1.png`](evidence/client/screenshots/electrolyzer_gui_scale_1.png) | Menu at effective GUI scale 1 |
| [`electrolyzer_gui_scale_2.png`](evidence/client/screenshots/electrolyzer_gui_scale_2.png) | Menu at effective GUI scale 2 |
| [`electrolyzer_gui_scale_3.png`](evidence/client/screenshots/electrolyzer_gui_scale_3.png) | Menu at effective GUI scale 3 |
| [`electrolyzer_gui_scale_4.png`](evidence/client/screenshots/electrolyzer_gui_scale_4.png) | Menu at effective GUI scale 4 and 1920×1080 |
| [`dedicated_first_join.png`](evidence/client/screenshots/dedicated_first_join.png) | Matching client joined the first server cycle |
| [`dedicated_restart_rejoin.png`](evidence/client/screenshots/dedicated_restart_rejoin.png) | Matching client rejoined after same-world restart |

Hashes, dimensions, GUI scales, and artifact bindings are in
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

The repository owner explicitly authorized direct G0/G8/G9 confirmation. The
approval applies only to the exact artifact and evidence hashes in
[`checksums.txt`](checksums.txt); changed bytes require renewed review.
