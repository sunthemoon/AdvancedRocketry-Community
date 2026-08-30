# Advanced Rocketry 1.12 audit summary

```yaml
repository: https://github.com/Advanced-Rocketry/AdvancedRocketry
branch: 1.12
commit: c5cd5af62fc07cd4e0d24f06a16033f181c47c04
license: MIT
license_notice: Copyright (c) 2017
java_files: 510
asset_files: 898
libvulpes_references: 886
static_mutable_candidates: 35
network_packet_candidates: 17
asm_coremod_findings: 17
missing_asset_references: 540
case_collisions: 0
generation: deterministic
```

## Java domains

| Domain | Files |
|---|---:|
| `advancements` | 2 |
| `api` | 42 |
| `armor` | 2 |
| `asm` | 3 |
| `atmosphere` | 14 |
| `backwardCompat` | 6 |
| `block` | 56 |
| `cable` | 9 |
| `capability` | 3 |
| `client` | 50 |
| `command` | 1 |
| `common` | 1 |
| `dimension` | 2 |
| `enchant` | 1 |
| `entity` | 23 |
| `event` | 5 |
| `integration` | 50 |
| `inventory` | 20 |
| `item` | 24 |
| `mission` | 3 |
| `network` | 16 |
| `recipe` | 10 |
| `root` | 2 |
| `satellite` | 10 |
| `stations` | 3 |
| `tile` | 65 |
| `unit` | 4 |
| `util` | 27 |
| `world` | 56 |

## Asset kinds

| Kind | Files |
|---|---:|
| `advancement` | 17 |
| `blockstate` | 101 |
| `lang` | 9 |
| `model_json` | 155 |
| `model_mtl` | 20 |
| `model_obj` | 43 |
| `other` | 22 |
| `recipe` | 157 |
| `sound_definition` | 1 |
| `sound_ogg` | 17 |
| `texture_block` | 138 |
| `texture_entity` | 1 |
| `texture_gui` | 56 |
| `texture_item` | 74 |
| `texture_other` | 87 |

## Largest Java files

| Lines | Path |
|---:|---|
| 2433 | `src/main/java/zmaster587/advancedRocketry/entity/EntityRocket.java` |
| 2024 | `src/main/java/zmaster587/advancedRocketry/dimension/DimensionProperties.java` |
| 1258 | `src/main/java/zmaster587/advancedRocketry/AdvancedRocketry.java` |
| 1164 | `src/main/java/zmaster587/advancedRocketry/dimension/DimensionManager.java` |
| 1159 | `src/main/java/zmaster587/advancedRocketry/api/ARConfiguration.java` |
| 1114 | `src/main/java/zmaster587/advancedRocketry/util/XMLPlanetLoader.java` |
| 1052 | `src/main/java/zmaster587/advancedRocketry/command/WorldCommand.java` |
| 1023 | `src/main/java/zmaster587/advancedRocketry/tile/TileRocketAssemblingMachine.java` |
| 1016 | `src/main/java/zmaster587/advancedRocketry/client/render/planet/RenderSpaceTravelSky.java` |
| 977 | `src/main/java/zmaster587/advancedRocketry/tile/station/TileWarpController.java` |
| 958 | `src/main/java/zmaster587/advancedRocketry/client/render/planet/RenderPlanetarySky.java` |
| 955 | `src/main/java/zmaster587/advancedRocketry/client/render/planet/RenderAsteroidSky.java` |
| 842 | `src/main/java/zmaster587/advancedRocketry/asm/ClassTransformer.java` |
| 841 | `src/main/java/zmaster587/advancedRocketry/stations/SpaceStationObject.java` |
| 823 | `src/main/java/zmaster587/advancedRocketry/util/StorageChunk.java` |

## Architecture findings

- LibVulpes usages are indexed rather than copied; v0.1.0 introduces no LibVulpes implementation.
- ASM/coremod findings are indexed with `REJECTED_NO_PORT`; later work requires an ADR before changing that decision.
- Mutable static collections, dimension APIs, NBT, packets, entities, block entities, and legacy registration points have dedicated indexes.
- Missing upstream references and case collisions are historical audit findings, not permission to import broken paths.
- v0.1.0 imports only individually reviewed targets named in its provenance ledger; the manifest is not an import allowlist.

## v1.0 mapping

Exact entry points are summarized in `docs/PORTING_MATRIX.md`. Audit status does not imply implementation or behavioral acceptance.
