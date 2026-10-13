# V180 C17a: typed propulsion components, delivery evidence

Date: 2026-10-13. Written by W31 under
[TASK-W31](../v1.8.0-root-queue/TASK-W31-LEDGER-EVIDENCE.md) for the C17a ledger rows that
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
| `ebf4c07b` | W02 (ADR-065 C17a advanced mono, bipropellant and nuclear propulsion with typed fuel banks), Root's earlier merge fixes, and W02F (review F1, F2 and F4) | `57090329` | 38199403721, attempt 2 |

The registering commit inside `ebf4c07b` is `546eeefc`. The ten ROCKET settings are defined in
`config/RocketPropulsionConfig.java`. Attempt 1 of run 38199403721 failed only on the vanilla
pending-write IOWorker CME after all 661 GameTests had passed; W08 (`88b16cef`) later addressed
that CME. Root resolved the merge conflicts: `CommonConfigTest` counts the sawmill switch and
the ten ROCKET values (87), and the generic config-key rule excludes sawmill, OXYGEN and ROCKET,
with its maximum of 99 unchanged.

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
| 38199403721 | `57090329` | `codex/v1.8.0-propulsion` | 2 | success |
| 38371580132 | `888a81b2` | `codex/v1.8.0-classic-content` | 1 | success |

Run 38371580132 tested the shared branch after every Root-queue merge. Since `888a81b2` the
shared branch has changed only documents.

## Independent reviews

The reviews are kept outside the repository. W31 recomputed the SHA-256 values on 2026-10-13;
they match the merge message prefixes.

| Review | Path | Verdict | SHA-256 |
|---|---|---|---|
| W02-review (Codex) | `D:/GitHub/ARCE-Task-Evidence/root-queue/W02-review/REVIEW.md` | BLOCK, fixed by W02F except F3 | `4b9127e665e4dcc7074aaf37b2896faafbbfee62c49a003f8d5fc19105401d7a` |
| W02F-review (Codex) | `D:/GitHub/ARCE-Task-Evidence/root-queue/W02F-review/REVIEW.md` | MERGE-WITH-FOLLOWUP | `c3e02c1f93c6947742df9c4f3dd0ea21a426801348939403b7bb54fec99332f7` |

## Delivered ledger units

| Unit | Disposition | Delivery |
|---|---|---|
| `block:advbipropellantRocketmotor` | IMPLEMENTED | `advancedrocketrycommunity:advanced_bipropellant_rocket_motor` |
| `block:advRocketmotor` | IMPLEMENTED | `advancedrocketrycommunity:advanced_rocket_motor` |
| `block:bipropellantfueltank` | IMPLEMENTED | `advancedrocketrycommunity:bipropellant_fuel_tank` |
| `block:bipropellantrocketmotor` | IMPLEMENTED | `advancedrocketrycommunity:bipropellant_rocket_motor` |
| `block:nuclearcore` | IMPLEMENTED | `advancedrocketrycommunity:nuclear_core` |
| `block:nuclearfueltank` | IMPLEMENTED | `advancedrocketrycommunity:nuclear_fuel_tank` |
| `block:nuclearrocketmotor` | IMPLEMENTED | `advancedrocketrycommunity:nuclear_rocket_motor` |
| `block:oxidizerfueltank` | IMPLEMENTED | `advancedrocketrycommunity:oxidizer_fuel_tank` |
| `config:ROCKET.canBeFueledByHand` | REDESIGNED | COMMON `ROCKET.canBeFueledByHand`: hand container exchange with the typed fuel banks |
| `config:ROCKET.fuelCapacityMultiplier` | REDESIGNED | COMMON `ROCKET.fuelCapacityMultiplier`, captured at assembly |
| `config:ROCKET.gravityAffectsFuels` | REDESIGNED | COMMON `ROCKET.gravityAffectsFuels`, captured at assembly |
| `config:ROCKET.nuclearCoreThrustRatio` | REDESIGNED | COMMON `ROCKET.nuclearCoreThrustRatio`, captured at assembly |
| `config:ROCKET.rocketBipropellants` | REDESIGNED | COMMON `ROCKET.rocketBipropellants`, a role-specific Fluid ID list |
| `config:ROCKET.rocketFuels` | REDESIGNED | COMMON `ROCKET.rocketFuels`, a role-specific Fluid ID list |
| `config:ROCKET.rocketNuclearWorkingFluids` | REDESIGNED | COMMON `ROCKET.rocketNuclearWorkingFluids`, a role-specific Fluid ID list |
| `config:ROCKET.rocketOxidizers` | REDESIGNED | COMMON `ROCKET.rocketOxidizers`, a role-specific Fluid ID list |
| `config:ROCKET.rocketsRequireFuel` | REDESIGNED | COMMON `ROCKET.rocketsRequireFuel`, a debit-only fuel requirement switch |
| `config:ROCKET.thrustMultiplier` | REDESIGNED | COMMON `ROCKET.thrustMultiplier`, captured at assembly |

Eighteen rows: 8 IMPLEMENTED (all registered by `546eeefc`) and 10 REDESIGNED. Six C17a rows
stay PLANNED because the loaders, the monitoring station and the linker are not implemented
(ADR-065 sections 4.2 and 4.3): `block:monitoringStation`, `block_variant:loader/2`,
`block_variant:loader/3`, `block_variant:loader/4`, `block_variant:loader/5` and
`libvulpes:java/itemLinker`.

## Open items

- **S1 (dedicated server and native restart):** not done. The W02F review asks for native
  clean-restart checks of F1 and F2.
- **S2 (crash cut across stores):** no crash-cut evidence for this batch. The packaged S2
  evidence for fuel loading belongs to W11b3.
- **V1 and V2:** no real-GPU V1 or two-client V2 evidence for the propulsion blocks or the
  rocket fuel display.
- **Dedicated server:** W08's review R02 stays open: start the packaged SRG jar on a dedicated
  server.
- **Art and provenance:** the manual review of the NEW art and the provenance review are open.
- **Follow-ups of this batch:**
  - a rocket stays locked while a recorded passenger never returns; it needs an admin recovery
    path;
  - F3, partial refuelling, goes to the loader task W11b3.

## Gate statement

No Gate is approved. This file supports the ledger delivery marks only. S1, S2, V1, V2 and the
art and provenance reviews remain open, as listed above.
