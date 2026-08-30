# MANUAL-TEST — v0.1.0

## Test identity

```yaml
date: 2026-08-30
tester: Codex-assisted packaged-client execution
reviewer: sunthemoon
review_status: APPROVED
minecraft: 1.20.1
forge: 47.4.10
java: 17.0.8
build: 1.20.1-0.1.0-dev
jar_sha256: 07f5c108233ba14dad518a64f4141caa70f2338166b139b31415d6f284b8e6ea
client_profile: isolated packaged Forge profile
other_content_mods: 0
```

Only Minecraft's F2 screenshots are committed. Launcher windows, desktop
content, machine paths, and full raw logs are excluded. Filtered logs retain
only the lifecycle and project observations needed for review.

## Player-flow matrix

| Case | Steps | Expected | Actual | Result |
|---|---|---|---|---|
| Mods identity | Open Forge Mods and select the project | Correct name, version, logo, license, description | Correct in zh_cn and en_us runs | PASS |
| Creative tab | Enter a disposable creative world and open page 2 | Dedicated tab contains exactly the five v0.1.0 entries | All five entries visible | PASS |
| Item/block resources | Inspect all entries and placed casings | No purple/black missing textures; item models readable | No missing resource observed | PASS |
| Orientation | Place casings with multiple facings | Front/side/top models remain coherent | Three visible orientations render correctly | PASS |
| Break | Break the center casing in creative mode | Block disappears and particles render | Backing block and break particles visible | PASS |
| Place | Place the casing item against the backing block | Casing returns and inert boundary remains clear | Casing placed; expected v0.2.0 behavior notice shown | PASS |
| Localization/scale | Run zh_cn at effective GUI scale 3 and en_us at scale 2 | Tab, five names, and tooltip are readable | Both language sets and data-storage tooltip are readable | PASS |
| Single-player lifecycle | Enter, save, and close the disposable world in both language runs | Login and clean world shutdown | Both runs initialized, logged in, saved all dimensions, and stopped | PASS |
| Dedicated first cycle | Join the loopback server with the matching packaged JAR, then disconnect | Server records join and leave | Both markers observed | PASS |
| Dedicated restart cycle | Restart the same world, rejoin with the same client, disconnect | Same player identity and world retained | Identity binding and same-world marker match | PASS |

## Screenshot index

| Evidence | What it proves |
|---|---|
| [`mods_zh_cn.png`](evidence/client/screenshots/mods_zh_cn.png) | Project appears in the packaged Forge Mods page |
| [`mods_en_us.png`](evidence/client/screenshots/mods_en_us.png) | English Mods identity, version, license, and description |
| [`creative_tab_zh_cn.png`](evidence/client/screenshots/creative_tab_zh_cn.png) | Chinese creative-tab title and all five entries |
| [`data_storage_tooltip_zh_cn.png`](evidence/client/screenshots/data_storage_tooltip_zh_cn.png) | Chinese item name and inert-v0.1.0 tooltip |
| [`creative_tab_en_us.png`](evidence/client/screenshots/creative_tab_en_us.png) | English creative-tab title and all five entries |
| [`data_storage_tooltip_en_us.png`](evidence/client/screenshots/data_storage_tooltip_en_us.png) | English item name and inert-v0.1.0 tooltip |
| [`machine_casing_orientations_zh_cn.png`](evidence/client/screenshots/machine_casing_orientations_zh_cn.png) | Three casing orientations without missing textures |
| [`machine_casing_break_zh_cn.png`](evidence/client/screenshots/machine_casing_break_zh_cn.png) | Manual break state and particles |
| [`machine_casing_place_zh_cn.png`](evidence/client/screenshots/machine_casing_place_zh_cn.png) | Manual replacement and expected boundary notice |
| [`dedicated_first_join.png`](evidence/client/screenshots/dedicated_first_join.png) | Matching packaged client in first server world cycle |
| [`dedicated_restart_rejoin.png`](evidence/client/screenshots/dedicated_restart_rejoin.png) | Matching client back in the same world after restart |

All screenshot hashes, dimensions, language labels, GUI-scale observations, and
artifact bindings are in
[`evidence/client/manual-evidence.json`](evidence/client/manual-evidence.json).

## Localized names observed

| Registry entry | zh_cn | en_us |
|---|---|---|
| `machine_casing` | 机器外壳 | Machine Casing |
| `silicon_wafer` | 硅晶片 | Silicon Wafer |
| `basic_circuit` | 基础芯片 | Basic Circuit |
| `advanced_circuit` | 高级芯片 | Advanced Circuit |
| `data_storage_unit` | 数据存储单位 | Data Storage Unit |

## OBJ/MTL applicability

OBJ/MTL import is explicitly deferred. v0.1.0 validates the migration pipeline
with JSON cube/item models only, so the conditional OBJ visual/performance case
is not part of this batch. No custom model-loader dependency was introduced.

## Provenance sample

All ten imported targets, rather than a smaller random subset, were checked
against the exact upstream commit and target transforms. Maintainer
`sunthemoon` approved content digest `b33ce1c9…` with no finding on 2026-08-31.
The renewal binds raw upstream Git blobs rather than platform-smudged checkout
bytes. See
[`evidence/provenance/human-review.json`](evidence/provenance/human-review.json).

## Human Gate decision

```yaml
G0: APPROVED
G8: APPROVED
G9: APPROVED
reviewer: sunthemoon
G0_reviewed_at: 2026-08-31
G8_G9_reviewed_at: 2026-08-30
findings: []
```

The approval applies only to the exact hashes in `checksums.txt`. Changed JAR,
provenance, screenshot, or filtered-log bytes require renewed review.
