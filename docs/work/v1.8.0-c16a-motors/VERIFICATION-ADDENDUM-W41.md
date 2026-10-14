# C16a motors: verification addendum (Root, 2026-10-15)

## All-tier formation (C16a-07b) delivers the motor row

`VERIFICATION.md` in this folder verified C16a-07a and kept `libvulpes:java/blockMotor` PLANNED until formation with every tier was verified (C16a-07b). W41 verified it. That file matches the folder's `checksums.txt`, so this addendum is a separate file and leaves it unchanged.

| Commit or merge on `codex/v1.8.0-classic-content` | Content | Tested HEAD | Hosted run | Review |
|---|---|---|---|---|
| `85a19757` (code), `6a3bcffe` (generated data and art) | C16a-07a, verified in `VERIFICATION.md`: the four tiers, both `motors` tags, the base and upgrade recipes | the commands in `VERIFICATION.md` | none of its own; covered by the runs below | the final independent review in `VERIFICATION.md` |
| `c954a329` | W41 (ADR-064 sections 1.1 and 4.1): every pattern with a motor cell forms with each tier and refuses a copper coil in that cell | `5d2390b5` | 38496718830 (success) | Root review |

The record is `docs/work/v1.8.0-root-queue/records/W41.md`. Run 38603920684 (v1.8 development regression) passed at `cc36612c`, which contains every commit above.

| Unit | Disposition | Delivery |
|---|---|---|
| `libvulpes:java/blockMotor` | IMPLEMENTED | `advancedrocketrycommunity:motor`, `advancedrocketrycommunity:advanced_motor`, `advancedrocketrycommunity:enhanced_motor` and `advancedrocketrycommunity:elite_motor`, all four in the block and item tags `advancedrocketrycommunity:motors`. Each tier forms the lathe, the cutting machine, the chemical reactor, the precision laser etcher, the centrifuge and the observatory |

The five rows merged into it (`libvulpes:java/blockAdvancedMotor`, `libvulpes:java/blockEnhancedMotor`, `libvulpes:java/blockEliteMotor`, `libvulpes:java/motors` and `libvulpes:ore/blockMotor`) need no change.

Tests: the unit classes `MotorDefinitionTest`, `MotorDataFilesTest`, `MotorArtTest` and `MotorLanguageResourcesTest`; the GameTests `MotorGameTests` (tags and drops, crafting multisets, exact preceding tier) and `MotorTierFormationGameTests` (six machines).

This addendum is development evidence only. It approves no Required Gate, candidate, version or release. V1 and V2 for the motor art remain open.
