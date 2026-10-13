# V180 C16b: arc furnace, lathe and cutting machine, delivery evidence

Date: 2026-10-13. Written by W31 under
[TASK-W31](../v1.8.0-root-queue/TASK-W31-LEDGER-EVIDENCE.md) for the C16b ledger rows that
W30's proposal (`records/W30-STATUS-PROPOSAL.md` on branch `codex/v1.8.0-w30-status`,
`2bc87cee`, sections 1.1 and 2 and Appendix A) marks delivered. The facts come from
[QUEUE.md](../v1.8.0-root-queue/QUEUE.md), the merge commit messages and the records in
`docs/work/v1.8.0-root-queue/records/`. Every SHA below resolves with `git cat-file -e` and is
an ancestor of `codex/v1.8.0-classic-content` at `e4bd8a12`.

This file is development evidence only. **It approves no Required Gate, candidate, version or
release.** Only the owner marks a Gate as passed (AGENTS.md sections 5 and 13).

## Merges and tested HEADs

| Merge on `codex/v1.8.0-classic-content` | Slices | Tested HEAD | Hosted run |
|---|---|---|---|
| `ab5d9808` | W01 (hatch family, generic classic controller, lathe), W01F (W01 review R1 to R4), W05 (electric arc furnace with blast brick, cutting machine with saw blade), W01G (combined review N1 to N3), and Root's config-count and log-manifest merge fixes | `2c60eadd` | 38199986751 |
| `0596d124` (Root fix, a direct commit) | The cutting machine model pointed at a missing `machine_casing` texture | itself | none of its own; covered by 38371580132 |

The registering commits inside `ab5d9808` are `944b67b8` (hatches and the kernel lathe) and
`3e79b95b` (arc furnace, blast brick, cutting machine, saw blade, iron saw blade and the
`machines.sawmillVanillaWood` switch). W01G's local GameTest run passed 689 of 689.

## Hosted CI

Each run is the workflow `v1.8 development regression`
(`.github/workflows/v180-development.yml`): the ledger validator with `--require-accepted`,
`clean build` with fresh unit tests, the artifact audit, the client-boundary check, `runData`
twice with a clean-tree check, all Forge GameTests and `check_gametest_log.py`. It runs no
dedicated server, no restart and no real client.

On 2026-10-13 W31 read each run back through the GitHub REST API
(`/repos/sunthemoon/AdvancedRocketry-Community/actions/runs/<id>`).

| Run | Head SHA returned | Branch | Attempt | Conclusion |
|---|---|---|---|---|
| 38199986751 | `2c60eadd` | `codex/v1.8.0-hatch-kernel` | 1 | success |
| 38371580132 | `888a81b2` | `codex/v1.8.0-classic-content` | 1 | success |

Run 38371580132 tested the shared branch after every Root-queue merge, including `0596d124`.
Since `888a81b2` the shared branch has changed only documents.

## Independent reviews

The reviews are kept outside the repository. The SHA-256 values were recomputed by W31 on
2026-10-13 and match the merge message prefixes.

| Review | Path | Verdict | SHA-256 |
|---|---|---|---|
| W01-review (Codex) | `D:/GitHub/ARCE-Task-Evidence/root-queue/W01-review/REVIEW.md` | BLOCK, fixed by W01F | `5954064319de8587de9f7b50e89a08d5c6ec2387f9e2a994bce932384ebd388a` |
| W01C-review (Claude) | `D:/GitHub/ARCE-Task-Evidence/root-queue/W01C-review/REVIEW.md` | BLOCK, fixed by W01G | `bb1ee58e098d84e85d1cac7ec8cbe865d1f717241609ba244298b704120edbdb` |
| W01G-review (Claude) | `D:/GitHub/ARCE-Task-Evidence/root-queue/W01G-review/REVIEW.md` | MERGE-WITH-FOLLOWUP | `d27a69843e865a47e33038be548a77724089df77d698944bf789fa81fc830101` |

## Delivered ledger units

| Unit | Disposition | Delivery |
|---|---|---|
| `block:arcfurnace` | IMPLEMENTED | `advancedrocketrycommunity:arc_furnace` (W05, `3e79b95b`) |
| `block:blastbrick` | IMPLEMENTED | `advancedrocketrycommunity:blast_brick` (W05, `3e79b95b`) |
| `block:cuttingMachine` | IMPLEMENTED | `advancedrocketrycommunity:cutting_machine` (W05, `3e79b95b`; model fix `0596d124`) |
| `block:lathe` | IMPLEMENTED | `advancedrocketrycommunity:lathe` (W01, `944b67b8`) |
| `block:sawBlade` | IMPLEMENTED | `advancedrocketrycommunity:saw_blade` (W05, `3e79b95b`) |
| `config:CATEGORY_GENERAL.sawMillCutVanillaWood` | REDESIGNED | COMMON `machines.sawmillVanillaWood` (`CommonConfig.java` line 28) |
| `config_file:CuttingMachine.xml` | REDESIGNED | the `cutting` recipe type, as data pack recipes |
| `config_file:ElectricArcFurnace.xml` | REDESIGNED | the `arc_furnace` recipe type, as data pack recipes |
| `config_file:Lathe.xml` | REDESIGNED | the `lathe` recipe type, as data pack recipes |
| `integration:jei/arcFurnace` | IMPLEMENTED | JEI category `arc_furnace` (`ClassicMachineJeiCategory`) |
| `integration:jei/lathe` | IMPLEMENTED | JEI category `lathe` |
| `integration:jei/sawmill` | IMPLEMENTED | JEI category `cutting`, which shows the cutting machine's wood recipes |
| `item_variant:sawBladeIron/0` | IMPLEMENTED | `advancedrocketrycommunity:iron_saw_blade` (W05, `3e79b95b`) |

Thirteen rows: 9 IMPLEMENTED and 4 REDESIGNED. No C16b row stays PLANNED.

## Open items

- **S1 (dedicated server and native restart):** not done. W01G's record says that no native
  server, `/data remove` command, region writer or restart was exercised.
- **S2 (crash cut across stores):** no crash-cut evidence for this batch.
- **V1 and V2:** no real-GPU V1 or two-client V2 evidence for the machines, their screens or
  JEI.
- **Dedicated server:** W08's review R02 stays open: start the packaged SRG jar on a dedicated
  server.
- **Art and provenance:** the manual review of the NEW art and the provenance review are open.
- **Follow-ups of this batch:**
  - W01G-review L1: a generation-0 controller with parts two generations ahead;
  - L2: `machine_clearance` does not cover modded air-like blocks or cables;
  - L3: the quarantine wording for the power plug's FE capability;
  - no save-admission test uses the `arc_furnace` or `cutting_machine` IDs (records/W01G.md,
    "Not done").

## Gate statement

No Gate is approved. This file supports the ledger delivery marks only. S1, S2, V1, V2 and the
art and provenance reviews remain open, as listed above.
