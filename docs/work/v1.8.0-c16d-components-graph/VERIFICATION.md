# V180 C16d: components and the recipe graph, delivery evidence

Date: 2026-10-13. Written by W31 under
[TASK-W31](../v1.8.0-root-queue/TASK-W31-LEDGER-EVIDENCE.md) for the C16d ledger rows that
W30's proposal (`records/W30-STATUS-PROPOSAL.md` on branch `codex/v1.8.0-w30-status`,
`2bc87cee`, sections 1.1 and 2 and Appendix A) marks delivered. The facts come from
[QUEUE.md](../v1.8.0-root-queue/QUEUE.md), the merge commit messages, the records in
`docs/work/v1.8.0-root-queue/records/` and the qualification files in this folder. Every SHA
below resolves with `git cat-file -e` and is an ancestor of `codex/v1.8.0-classic-content` at
`e4bd8a12`.

This file is development evidence only. **It approves no Required Gate, candidate, version or
release.** Only the owner marks a Gate as passed (AGENTS.md sections 5 and 13).

## Commits and tested HEADs

The components were delivered before the Root handover, as commits directly on the shared
branch. Each has a qualification file in this folder.

| Commit on `codex/v1.8.0-classic-content` | Content | Qualification file | Hosted run |
|---|---|---|---|
| `4951a326`, verified by merge `8fe5a0ec` (C16d-ACQUISITION-01) | user interface and carbon brick resources; fixture correction `f654a15e` | [ACQUISITION-QUALIFICATION-02](ACQUISITION-QUALIFICATION-02.md) | 38107189300 at `8fe5a0ec` |
| `fd88f71b`, tested as `50d4a3ea` (IO-BOARD-03) | item IO board | [IO-BOARD-QUALIFICATION-03](IO-BOARD-QUALIFICATION-03.md) | 38114677677 at `7beccfcf`, which contains `50d4a3ea` |
| `0dc14e4f` (LIQUID-IO-BOARD-04) | liquid IO board | [LIQUID-IO-BOARD-QUALIFICATION-04](LIQUID-IO-BOARD-QUALIFICATION-04.md) | none of its own; covered by the later runs below |
| `c2934b83` (CONTROL-BOARD-05) | control board | [CONTROL-BOARD-QUALIFICATION-05](CONTROL-BOARD-QUALIFICATION-05.md) | none of its own; covered by the later runs below |
| `b3595a73` (CIRCUIT-PLATES-06) | basic and advanced circuit plates | [CIRCUIT-PLATES-QUALIFICATION-06](CIRCUIT-PLATES-QUALIFICATION-06.md) | none of its own; covered by the later runs below |

The recipe graph work came through the Root queue:

| Merge on `codex/v1.8.0-classic-content` | Slice | Tested HEAD | Hosted run |
|---|---|---|---|
| `7324a7dd` | W10: the precision laser etcher, which makes both circuit plates (`laser_etching_basic_circuit_plate`, `laser_etching_advanced_circuit_plate`) | `b0c8d992` | 38224855115 |
| `f5038f2f` | W15: `PackagedRecipeGraph` models all seven classic machine recipe types again and fails on any unmodelled type | `963d7db5` | 38239083487 |
| `3d499253` | W19: code-backed acquisition models; Root decision 01 (`3c08ab5d`) classifies five ore-less dusts as optional cross-mod inputs; every other registered item needs a survival route | `fe37b526` | 38261140501 |

The carbon brick carries the tag `forge:ingots/carbon`
(`src/generated/v1.8/resources/data/forge/tags/items/ingots/carbon.json`).

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
| 38107189300 | `8fe5a0ec` | `codex/v1.8.0-classic-content` | 1 | success |
| 38114677677 | `7beccfcf` | `codex/v1.8.0-classic-content` | 1 | success |
| 38224855115 | `b0c8d992` | `codex/v1.8.0-w10-c16c-machines` | 1 | success |
| 38239083487 | `963d7db5` | `codex/v1.8.0-w15-recipe-graph` | 1 | success |
| 38261140501 | `fe37b526` | `codex/v1.8.0-w19-acquisition` | 1 | success |
| 38371580132 | `888a81b2` | `codex/v1.8.0-classic-content` | 1 | success |

`b3595a73`, the last component commit, is an ancestor of `b0c8d992`, `963d7db5` and
`fe37b526`. Run 38371580132 tested the shared branch after every Root-queue merge. Since
`888a81b2` the shared branch has changed only documents.

## Independent reviews

W10's review is the only independent Root-queue review for this batch. W15 and W19 were Root
reviews; each merge message records that the assertion audit lost nothing. The pre-handover
component slices record their reviews in their ADOPTION and QUALIFICATION files in this folder.

| Review | Path | Verdict | SHA-256 |
|---|---|---|---|
| W10-review (Codex) | `D:/GitHub/ARCE-Task-Evidence/root-queue/W10-review/REVIEW.md` | MERGE-WITH-FOLLOWUP | `5317fbec165f10f36b6132ee9c87a10daa84e639eca6809a86ecb2ab0f433c4b` |

W31 recomputed the SHA-256 on 2026-10-13; it matches the merge message prefix.

## Delivered ledger units

| Unit | Disposition | Delivery |
|---|---|---|
| `item_variant:ic/3` | IMPLEMENTED | `advancedrocketrycommunity:control_board` (`c2934b83`) |
| `item_variant:ic/1` | IMPLEMENTED | `advancedrocketrycommunity:tracking_circuit` (W35, merge `2c99a1db`; hosted run 38419018623) |
| `item_variant:ic/4` | IMPLEMENTED | `advancedrocketrycommunity:item_io_board` (`fd88f71b`) |
| `item_variant:ic/5` | IMPLEMENTED | `advancedrocketrycommunity:liquid_io_board` (`0dc14e4f`) |
| `item_variant:itemCircuitPlate/0` | IMPLEMENTED | `advancedrocketrycommunity:basic_circuit_plate` (`b3595a73`; made by the laser etcher) |
| `item_variant:itemCircuitPlate/1` | IMPLEMENTED | `advancedrocketrycommunity:advanced_circuit_plate` (`b3595a73`; made by the laser etcher) |
| `item_variant:misc/0` | IMPLEMENTED | `advancedrocketrycommunity:user_interface` (`4951a326`) |
| `item_variant:misc/1` | IMPLEMENTED | `advancedrocketrycommunity:carbon_brick` (`4951a326`) |
| `material:Carbon` | IMPLEMENTED | `advancedrocketrycommunity:carbon_brick`; the legacy `ingotCarbon` is the carbon brick, tagged `forge:ingots/carbon` |

Eight rows, all IMPLEMENTED. Two C16d rows stay PLANNED:

- `item_variant:ic/1`, the tracking circuit, is not registered (ADR-064 section 10.1);
- `config:CATEGORY_GENERAL.makeMaterialsForOtherMods`: W30 could not name a delivering commit
  (see the question in records/W31.md).

## Open items

- **S1 (dedicated server and native restart):** not done for this batch.
- **S2 (crash cut across stores):** not applicable as far as W31 can tell: these rows are items
  and recipes and add no store. No crash-cut evidence exists.
- **V1 and V2:** no real-GPU V1 or two-client V2 evidence for the NEW component art.
- **Dedicated server:** W08's review R02 stays open: start the packaged SRG jar on a dedicated
  server.
- **Art and provenance:** the manual review of the NEW art and the provenance review are open.
- **Follow-ups of this batch (ADR-064 section 10):**
  - section 10.1: the tracking circuit;
  - section 10.2: a whole-graph run with the Tau Ceti Levels removed. W30 found only partial
    removal tests (`CodeAcquisitionGraphTest`), so it is not verified;
  - section 10.3: the rebalance with its CHANGELOG entries, not verified.

## Gate statement

No Gate is approved. This file supports the ledger delivery marks only. S1, V1, V2 and the art
and provenance reviews remain open, as listed above.
