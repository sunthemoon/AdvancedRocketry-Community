# V180 C16c: crystallizer, chemical reactor, laser etcher and centrifuge, delivery evidence

Date: 2026-10-13. Written by W31 under
[TASK-W31](../v1.8.0-root-queue/TASK-W31-LEDGER-EVIDENCE.md) for the C16c ledger rows, and the
one C16a row, that W30's proposal (`records/W30-STATUS-PROPOSAL.md` on branch
`codex/v1.8.0-w30-status`, `2bc87cee`, sections 1.1 and 2 and Appendix A) marks delivered.
The facts come from [QUEUE.md](../v1.8.0-root-queue/QUEUE.md), the merge commit messages and
the records in `docs/work/v1.8.0-root-queue/records/`. Every SHA below resolves with
`git cat-file -e` and is an ancestor of `codex/v1.8.0-classic-content` at `e4bd8a12`.

This file is development evidence only. **It approves no Required Gate, candidate, version or
release.** Only the owner marks a Gate as passed (AGENTS.md sections 5 and 13).

## Merges and tested HEADs

| Merge on `codex/v1.8.0-classic-content` | Slices | Tested HEAD | Hosted run |
|---|---|---|---|
| `7324a7dd` | W10: the four C16c machines on the classic kernel (ADR-064 section 9) with the quartz crucible, the crystallizer gravity limit, the etcher's retained lens and the centrifuge's seeded, retained chance outcomes; JEI and generated assets | `b0c8d992` | 38224855115 |

The registering commit inside `7324a7dd` is `3e42caaf`. W10's local run passed 742 GameTests.
Root resolved the merge conflicts: `CommonConfigTest` counts the crystallizer gravity limit
(90), and the generic config-key rule also excludes `machines.crystallizerMaximumGravity`, with
its maximum of 99 unchanged.

The endgame casing, `advancedrocketrycommunity:endgame_casing`, was registered earlier by
`d045e7fb` (2026-10-01, the endgame content). W10's generated machine patterns for the
centrifuge and the precision laser etcher use it as their casing, and
`C16cMachineGameTests.centrifugeFormsInFourRotationsAndRefusesWrongCasing` forms the centrifuge
with it.

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
| 38224855115 | `b0c8d992` | `codex/v1.8.0-w10-c16c-machines` | 1 | success |
| 38371580132 | `888a81b2` | `codex/v1.8.0-classic-content` | 1 | success |

Run 38371580132 tested the shared branch after every Root-queue merge. Since `888a81b2` the
shared branch has changed only documents.

## Independent reviews

The review is kept outside the repository. W31 recomputed its SHA-256 on 2026-10-13; it
matches the merge message prefix.

| Review | Path | Verdict | SHA-256 |
|---|---|---|---|
| W10-review (Codex) | `D:/GitHub/ARCE-Task-Evidence/root-queue/W10-review/REVIEW.md` | MERGE-WITH-FOLLOWUP | `5317fbec165f10f36b6132ee9c87a10daa84e639eca6809a86ecb2ab0f433c4b` |

## Delivered ledger units

| Unit | Disposition | Delivery |
|---|---|---|
| `libvulpes:java/blockAdvStructureBlock` | IMPLEMENTED | `advancedrocketrycommunity:endgame_casing`, the casing of the centrifuge and laser etcher patterns (ledger plan today: C16a) |
| `block:centrifuge` | IMPLEMENTED | `advancedrocketrycommunity:centrifuge` |
| `block:chemicalReactor` | IMPLEMENTED | `advancedrocketrycommunity:chemical_reactor` (the two C18 special recipes are separate rows) |
| `block:crystallizer` | IMPLEMENTED | `advancedrocketrycommunity:crystallizer` |
| `block:precisionlaseretcher` | IMPLEMENTED | `advancedrocketrycommunity:precision_laser_etcher` |
| `block:quartzcrucible` | IMPLEMENTED | `advancedrocketrycommunity:quartz_crucible` |
| `config:CATEGORY_GENERAL.crystalliserMaximumGravity` | REDESIGNED | COMMON `machines.crystallizerMaximumGravity` |
| `config:CATEGORY_GENERAL.lavaCentrifugeOutputs` | REDESIGNED | the `centrifuging` recipe type, as data pack recipes |
| `config_file:Centrifuge.xml` | REDESIGNED | the `centrifuging` recipe type |
| `config_file:ChemicalReactor.xml` | REDESIGNED | the `chemical_reacting` recipe type |
| `config_file:Crystallizer.xml` | REDESIGNED | the `crystallizing` recipe type |
| `config_file:PrecisionLaserEtcher.xml` | REDESIGNED | the `laser_etching` recipe type |
| `integration:jei/centrifuge` | IMPLEMENTED | JEI category `centrifuging` (`ClassicMachineJeiCategory`) |
| `integration:jei/chemicalReactor` | IMPLEMENTED | JEI category `chemical_reacting` |
| `integration:jei/crystallizer` | IMPLEMENTED | JEI category `crystallizing` |
| `integration:jei/precisionLaserEtcher` | IMPLEMENTED | JEI category `laser_etching` |
| `item:iquartzcrucible` | IMPLEMENTED | `advancedrocketrycommunity:quartz_crucible_shell`, the crafting part of the crucible |

Seventeen rows: 11 IMPLEMENTED and 6 REDESIGNED. Sixteen are C16c rows and none of them stays
PLANNED. The C16a row `libvulpes:java/blockMotor` stays PLANNED: there is no formation GameTest
for each motor tier (ADR-064 section 4.1).

## Open items

- **S1 (dedicated server and native restart):** not done for the four machines.
- **S2 (crash cut across stores):** no crash-cut evidence for this batch.
- **V1 and V2:** no real-GPU V1 or two-client V2 evidence for the machines, their screens or
  JEI.
- **Dedicated server:** W08's review R02 stays open: start the packaged SRG jar on a dedicated
  server.
- **Art and provenance:** the manual review of the NEW art and the provenance review are open.
- **Follow-ups of this batch:**
  - W10-R1 (Low): the ordinal test does not drive the live overflow guard;
  - the chemical reactor has only `chemical_reacting_bone_meal` and
    `chemical_reacting_rocket_fuel`; the `space_breathing` application (ADR-066 section 5.1)
    and the lossless carbon-cartridge refresh (ADR-066 section 3.1) are missing
    (records/W10.md).

## Gate statement

No Gate is approved. This file supports the ledger delivery marks only. S1, S2, V1, V2 and the
art and provenance reviews remain open, as listed above.
